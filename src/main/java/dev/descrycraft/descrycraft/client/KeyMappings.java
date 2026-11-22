package dev.descrycraft.descrycraft.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.descrycraft.descrycraft.Descrycraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.sdl.SDLScancode;

public final class KeyMappings {

    /** 26.x 起按键分类是注册出来的对象，语言键为 key.category.descrycraft.drone。 */
    public static final KeyMapping.Category CATEGORY =
            new KeyMapping.Category(Identifier.fromNamespaceAndPath(Descrycraft.MOD_ID, "drone"));

    public static final KeyMapping LINK = new KeyMapping(
            "key.descrycraft.link",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYBOARD,
            SDLScancode.SDL_SCANCODE_V,
            CATEGORY);

    public static final KeyMapping THROW = new KeyMapping(
            "key.descrycraft.throw",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYBOARD,
            SDLScancode.SDL_SCANCODE_G,
            CATEGORY);

    public static final KeyMapping RECALL = new KeyMapping(
            "key.descrycraft.recall",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYBOARD,
            SDLScancode.SDL_SCANCODE_R,
            CATEGORY);

    /** One press = one barrell roll. Only meaningful while piloting. */
    public static final KeyMapping ROLL = new KeyMapping(
            "key.descrycraft.roll",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYBOARD,
            SDLScancode.SDL_SCANCODE_C,
            CATEGORY);

    /** Blows the warhead where the drone is standing. Only meaningful while piloting. */
    public static final KeyMapping DETONATE = new KeyMapping(
            "key.descrycraft.detonate",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYBOARD,
            SDLScancode.SDL_SCANCODE_X,
            CATEGORY);

    /**
     * 切换跟随。
     *
     * <p>不需要连线，也不需要看着无人机 —— 这是它和上面几个键最大的差别。玩家在挖矿、
     * 在跑图、在做任何别的事，随手一按就能让无人机跟上来或者停在原地当固定哨兵。
     */
    public static final KeyMapping FOLLOW = new KeyMapping(
            "key.descrycraft.follow",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYBOARD,
            SDLScancode.SDL_SCANCODE_H,
            CATEGORY);

    private KeyMappings() {
    }
}