package dev.descrycraft.descrycraft.client;

import com.mojang.logging.LogUtils;
import dev.descrycraft.descrycraft.Descrycraft;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.block.dispatch.ModelState;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelDebugName;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.sprite.MaterialBaker;
import net.minecraft.client.resources.model.sprite.TextureSlots;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.Util;
import net.minecraft.util.context.ContextMap;
import net.neoforged.neoforge.client.model.obj.ObjGeometry;
import net.neoforged.neoforge.client.model.obj.ObjLoader;
import net.neoforged.neoforge.client.textures.UnitTextureAtlasSprite;
import org.joml.Vector3fc;
import org.slf4j.Logger;

/**
 * Loads the two OBJ meshes that make up the drone and bakes them once.
 *
 * <p>Both meshes are authored offline: {@code drone.obj} carries the airframe and
 * {@code drone_glow.obj} carries the emissive parts, which live in a separate file so they can be
 * drawn with a different render type and forced to full brightness. They are produced either by
 * {@code tools/prepare_drone_model.py} (from the Blender hard-surface model) or by
 * {@code tools/generate_drone_mesh.py} (the procedural fallback). Both write the same layout.
 *
 * <p>The loader settings are worth spelling out, because two of the four defaults are wrong for
 * an entity:
 * <ul>
 *   <li>{@code automatic_culling} is off. It only ever fires for quads sitting exactly on the
 *       x = 0 or x = 1 plane, which is a block-model assumption; leaving it on would silently
 *       assign cull directions to a mesh that does not want them.</li>
 *   <li>{@code flip_v} stays off, because the generator already writes {@code vt} with the origin
 *       at the top left, matching Minecraft rather than the OBJ spec.</li>
 *   <li>{@code emissive_ambient} is off. With it on, the baked lightmap comes from the material
 *       {@code Ka}, and our materials declare {@code Ka 0 0 0} - which bakes every quad at light
 *       level zero and renders the whole drone black.</li>
 * </ul>
 *
 * <p>26.3 起 NeoForge 的 OBJ 加载器不再产出 {@code CompositeRenderable}，而是 {@link ObjGeometry}，
 * 烘焙结果是 {@link QuadCollection}。这里把每个顶层组单独烘成一份 quad 集合，键为组名，
 * 好让 {@link DroneRig} 的逐组变换在绘制时仍然能套上去（旋翼、云台窗）。
 *
 * <p>烘焙用的 {@link ModelBaker} 是一次性的桩：实体贴图不进方块图集，所以材质一律解析到
 * {@link UnitTextureAtlasSprite}（UV 恒等），真正的贴图在绘制时由 {@code RenderTypes} 传入 ——
 * 这与旧版 {@code StandaloneGeometryBakingContext} + {@code UnitTextureAtlasSprite} 的行为一致。
 *
 * <p>The two meshes are drawn with deliberately different render types (see {@link DroneRenderer}):
 * the airframe uses {@code RenderTypes.entityCutout}, an ordinary lit type that multiplies in the
 * lightmap; the camera window, motor rings and nose lamp use {@code RenderTypes.eyes}, the vanilla
 * additive emissive type that samples no lightmap and blends {@code ONE, ONE}. Passing
 * {@code FULL_BRIGHT} to a lit type is <em>not</em> enough to look self-lit - it is a ceiling, not a
 * bypass - so the glow parts need the additive type to exceed the lighting ceiling.
 */
public final class DroneMeshes implements ResourceManagerReloadListener {

    public static final DroneMeshes INSTANCE = new DroneMeshes();

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String NAMESPACE = Descrycraft.MOD_ID;

    /** Full texture paths, handed to {@code RenderTypes} at draw time. */
    static final Identifier BODY_TEXTURE = id("textures/entity/recon_drone.png");
    static final Identifier GLOW_TEXTURE = id("textures/entity/recon_drone_glow.png");
    static final Identifier RED_GLOW_TEXTURE = id("textures/entity/recon_drone_glow_red.png");

    /**
     * The OBJ groups {@link DroneRig} animates. A missing group is not fatal - the transform is
     * simply not applied - so this exists purely to turn a silently dead rotor into a log line.
     */
    static final String[] ANIMATED = {
            "Rotor_FL", "Rotor_FR", "Rotor_RL", "Rotor_RR", "Gimbal_Lens",
    };

    /** A fully baked pair, each keyed by OBJ group name: the lit airframe and the emissive parts. */
    public record Meshes(Map<String, QuadCollection> body, Map<String, QuadCollection> glow) {
    }

    /** Guards the bookkeeping below; the meshes themselves are published through {@link #meshes}. */
    private final Object lock = new Object();
    /** Bumped on every reload, so a load that finishes after one is thrown away. */
    private int generation;
    /** Whether a load is in flight. */
    private boolean loading;
    /** Published as one value, so the renderer never sees the body without the glow. */
    private volatile Meshes meshes;
    /** Latched when a load throws, so a broken mesh does not retry (and log) every frame. */
    private volatile boolean failed;

    private DroneMeshes() {}

    /**
     * Drops the baked meshes and starts loading them again on a background thread.
     *
     * <p>{@code failed} is cleared too: a reload is exactly the moment at which a mesh that failed
     * to parse might have been fixed.
     */
    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        synchronized (lock) {
            generation++;
            meshes = null;
            failed = false;
            loading = false;
        }
        // Start at once: a reload happens while the world is still loading, so the parse and the
        // bake are normally finished before the drone can first come into view.
        ensureLoaded();
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(NAMESPACE, path);
    }

    /**
     * {@return the baked meshes, or {@code null} while the background load has not finished}
     *
     * <p>Also kicks the load off if it has not started yet - the reload listener normally does
     * that, but a failed start is retried here rather than left for a reload that may never come.
     */
    public static Meshes meshes() {
        INSTANCE.ensureLoaded();
        return INSTANCE.meshes;
    }

    /** {@return whether a full bake is available for callers that cannot draw a partial one} */
    public static boolean isReady() {
        return meshes() != null;
    }

    private void ensureLoaded() {
        int startedFor;
        synchronized (lock) {
            if (loading || failed || meshes != null) {
                return;
            }
            loading = true;
            startedFor = generation;
        }
        Util.backgroundExecutor().execute(() -> load(startedFor));
    }

    /**
     * Parses and bakes both meshes off the render thread.
     *
     * <p>It is roughly half a second of work - the airframe alone is 1.1 MB of OBJ and 6,675
     * quads - plus the class loading that comes with the OBJ loader. On the render thread that
     * landed as a one-second freeze on the first frame the drone was visible. Baking it here moves
     * the cost to a worker, and the drone simply stays out of frame for the handful of frames it
     * takes.
     */
    private void load(int startedFor) {
        Meshes baked = null;
        try {
            ObjGeometry bodyGeometry = loadGeometry("drone.obj");
            ObjGeometry glowGeometry = loadGeometry("drone_glow.obj");
            baked = new Meshes(bake(bodyGeometry), bake(glowGeometry));
            reportMissingParts(bodyGeometry, glowGeometry);
        } catch (Exception error) {
            // One failed load should cost us the drone, not the frame.
            LOGGER.error("Could not load the Recon Drone meshes; the drone will not render", error);
        }
        synchronized (lock) {
            if (startedFor != generation) {
                // A reload ran while this load was in flight and already owns the state.
                return;
            }
            loading = false;
            if (baked == null) {
                failed = true;
            } else {
                meshes = baked;
            }
        }
    }

    private static ObjGeometry loadGeometry(String file) {
        return ObjLoader.INSTANCE.loadGeometry(new ObjGeometry.Settings(
                id("models/entity/" + file),
                false,   // automatic_culling
                true,    // shade_quads
                false,   // flip_v
                false,   // emissive_ambient
                null));  // mtl_override - resolved from the mtllib line in the OBJ
    }

    /** Bakes each top-level group into its own quad collection, keyed by group name for the rig. */
    private static Map<String, QuadCollection> bake(ObjGeometry geometry) {
        ModelBaker baker = standaloneBaker();
        Map<String, QuadCollection> parts = new HashMap<>();
        for (ObjGeometry.ModelObject part : geometry.getRootParts()) {
            QuadCollection.Builder builder = new QuadCollection.Builder();
            part.addQuads(builder, TextureSlots.EMPTY, baker, IDENTITY_STATE, DEBUG_NAME, ContextMap.EMPTY);
            parts.put(part.name(), builder.build());
        }
        return parts;
    }

    private static final ModelState IDENTITY_STATE = new ModelState() {};
    private static final ModelDebugName DEBUG_NAME = () -> "descrycraft:recon_drone";

    private static final ModelBaker.Interner IDENTITY_INTERNER = new ModelBaker.Interner() {
        @Override
        public Vector3fc vector(Vector3fc vector) {
            return vector;
        }

        @Override
        public BakedQuad.MaterialInfo materialInfo(BakedQuad.MaterialInfo material) {
            return material;
        }
    };

    /**
     * A throwaway baker for entity geometry that never touches the block atlas. Every material
     * resolves to the unit sprite (identity UVs); the real texture is supplied at draw time. The
     * model hooks the OBJ baker never calls are stubbed out.
     */
    private static ModelBaker standaloneBaker() {
        SpriteLoader.Preparations atlas = new SpriteLoader.Preparations(
                1, 1, 0, UnitTextureAtlasSprite.INSTANCE, Map.of(), CompletableFuture.completedFuture(null));
        MaterialBaker materials = new MaterialBaker(atlas, atlas) {
            @Override
            public Material.Baked resolveSlot(TextureSlots slots, String id, ModelDebugName name) {
                return new Material.Baked(UnitTextureAtlasSprite.INSTANCE, false);
            }

            @Override
            public Material.Baked get(Material material, ModelDebugName name) {
                return new Material.Baked(UnitTextureAtlasSprite.INSTANCE, false);
            }
        };
        return new ModelBaker() {
            @Override
            public ResolvedModel getModel(Identifier location) {
                throw new UnsupportedOperationException();
            }

            @Override
            public BlockStateModelPart missingBlockModelPart() {
                throw new UnsupportedOperationException();
            }

            @Override
            public MaterialBaker materials() {
                return materials;
            }

            @Override
            public Interner interner() {
                return IDENTITY_INTERNER;
            }

            @Override
            public <T> T compute(SharedOperationKey<T> key) {
                return key.compute(this);
            }
        };
    }

    /**
     * Both models are searched, not just the airframe: a mesh may carry part of the animated
     * geometry in its own file, so checking only the body could report a present group as missing.
     */
    private static void reportMissingParts(ObjGeometry body, ObjGeometry glow) {
        Set<String> names = new HashSet<>(body.getConfigurableComponentNames());
        names.addAll(glow.getConfigurableComponentNames());
        for (String part : ANIMATED) {
            if (!names.contains(part)) {
                LOGGER.error("Recon Drone mesh has no '{}' group, so that part will not animate. "
                        + "Groups present: {}", part, names);
            }
        }
    }
}