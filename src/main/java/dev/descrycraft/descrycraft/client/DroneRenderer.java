package dev.descrycraft.descrycraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.math.Axis;
import dev.descrycraft.descrycraft.entity.ReconDroneEntity;
import java.util.Map;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

/**
 * Draws the drone from the baked OBJ meshes.
 *
 * <p>The mesh is authored in blocks with Y up and the nose towards -Z, so unlike the cube model it
 * replaces there is no 1/16 scale and no {@code scale(-1, -1, 1)} flip to undo. What is left is
 * the placement: the origin sits at the airframe's centre, and the entity's bounding box is
 * 0.45 blocks tall, so the model is lifted by half of that to sit in the middle of its own hitbox.
 *
 * <p>26.x 把实体渲染拆成两段：{@link #extractRenderState} 在提取阶段把每帧数据写进
 * {@link DroneRenderState}，{@link #submit} 在绘制阶段只读这份状态，把按组烘好的 quad 通过
 * {@code submitCustomGeometry} 提交。逐组变换（旋翼自转、云台增稳）由 {@link DroneRig} 给出。
 */
public class DroneRenderer extends EntityRenderer<ReconDroneEntity, DroneRenderState> {

    /**
     * Half the model's own height, so its centre lands on the entity's centre.
     *
     * <p>The mesh is 0.4227 blocks tall, not the 0.45 of the hitbox, so this is deliberately not
     * {@code bbHeight / 2}: using the hitbox height would leave the skids hanging 1.4 cm above the
     * ground. {@code tools/prepare_drone_model.py} prints the exact value as {@code MODEL_Y} every
     * time it rebuilds the mesh - if the model is ever re-scaled, copy the new number from there.
     */
    private static final float MODEL_Y = 0.2113F;

    /**
     * Which way the airframe leans into a turn, and which way it rolls. Roll is applied about the
     * model's longitudinal axis, and a positive rotation about that axis lifts its right side, so a
     * roll to the right - a turn lean, or a barrel roll - is a negative angle here. Note that this
     * is the opposite of the sign the pilot's camera wants: see
     * {@code DroneController.CAMERA_ROLL_SIGN}. If the drone visibly banks out of its turns instead
     * of into them, flip this.
     */
    private static final float BANK_SIGN = -1.0F;

    /**
     * Solid red damage flash.
     *
     * <p>{@code pack(1.0F, true)} selects the overlay texture's hurt row (v = 3), which is red at
     * about 70% alpha, and the entity shader mixes that over the sampled texture by that alpha.
     */
    private static final int HURT_OVERLAY = OverlayTexture.pack(1.0F, true);

    public DroneRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.35F;
        this.shadowStrength = 0.5F;
    }

    @Override
    public DroneRenderState createRenderState() {
        return new DroneRenderState();
    }

    /**
     * 剔除盒比碰撞盒放大 0.13：旋翼与天线探出机体碰撞盒之外，按原盒剔除会让贴近屏幕边缘的
     * 无人机连探出的部分一起整个消失。26.3 把 getBoundingBoxForCulling 从实体挪到了渲染器，
     * 旧版实体侧的那点放大改在这里做，数值不变。
     */
    @Override
    protected AABB getBoundingBoxForCulling(ReconDroneEntity entity, float partialTicks) {
        return super.getBoundingBoxForCulling(entity, partialTicks).inflate(0.13);
    }

    @Override
    public void extractRenderState(ReconDroneEntity entity, DroneRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.yaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
        state.pitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
        state.bank = entity.getBank(partialTick);
        state.roll = entity.getRoll(partialTick);
        state.hurt = entity.getHurtTime() > 0;
        // A charge has armed the warhead and a detonation is already counting down, so both show
        // the alarm sheet. Everything else stays on the cyan running lights.
        state.glowRed = entity.isCharging() || entity.isDetonated();
        state.lightCoords = getPackedLightCoords(entity, partialTick);
        state.rig = DroneRig.of(entity, partialTick);
    }

    @Override
    public void submit(DroneRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                       CameraRenderState camera) {
        // Null for the first few frames while the OBJ parse and bake run on a worker thread.
        var meshes = DroneMeshes.meshes();
        if (meshes == null) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(0.0F, MODEL_Y, 0.0F);
        // The mesh's nose points down -Z, but a Minecraft entity at yaw 0 faces +Z.
        poseStack.rotateDegrees(Axis.YP, 180.0F - state.yaw);
        // xRot is positive nose-down, and rotating +Z towards +Y lifts the nose, so it is negated.
        poseStack.rotateDegrees(Axis.XP, -state.pitch);
        // Lean and barrel roll are rotations about the same axis - the model's own nose, which the
        // mesh points down -Z - so they share one sign constant and cannot fight each other.
        poseStack.rotateDegrees(Axis.ZP, BANK_SIGN * (state.bank + state.roll));

        int overlay = state.hurt ? HURT_OVERLAY : OverlayTexture.NO_OVERLAY;
        RenderType bodyType = RenderTypes.entityCutout(DroneMeshes.BODY_TEXTURE);
        RenderType glowType = RenderTypes.eyes(
                state.glowRed ? DroneMeshes.RED_GLOW_TEXTURE : DroneMeshes.GLOW_TEXTURE);

        drawParts(poseStack, collector, meshes.body(), bodyType, state.lightCoords, overlay, state.rig);
        // The lens and tail lamp use the additive emissive type, which samples no lightmap, so the
        // light stays on at noon and at midnight alike and the hurt overlay does not reach it.
        drawParts(poseStack, collector, meshes.glow(), glowType, LightCoordsUtil.FULL_BRIGHT, overlay, state.rig);

        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

    /** Submits each group's quads under its own rig transform, snapshotting the pose per group. */
    private static void drawParts(PoseStack poseStack, SubmitNodeCollector collector,
                                  Map<String, QuadCollection> parts, RenderType type,
                                  int light, int overlay, Map<String, Matrix4f> rig) {
        for (var entry : parts.entrySet()) {
            QuadCollection quads = entry.getValue();
            if (quads.getAll().isEmpty()) {
                continue;
            }
            poseStack.pushPose();
            Matrix4f transform = rig.get(entry.getKey());
            if (transform != null) {
                poseStack.mulPose(transform);
            }
            collector.submitCustomGeometry(poseStack, type, (pose, consumer) -> {
                QuadInstance instance = new QuadInstance();
                instance.setLightCoords(light);
                instance.setOverlayCoords(overlay);
                for (BakedQuad quad : quads.getAll()) {
                    consumer.putBakedQuad(pose, quad, instance);
                }
            });
            poseStack.popPose();
        }
    }
}