package dev.descrycraft.descrycraft.client;

import dev.descrycraft.descrycraft.entity.ReconDroneEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/**
 * Signal degradation, the world-space half of it.
 *
 * <p>Two things happen as the drone flies away from its operator: the picture loses definition,
 * and the visor fills up with grain. This class owns the first one - a real defocus of the
 * rendered world - and publishes {@link #amount(ReconDroneEntity, double)} so the overlay in
 * {@link DroneHud} can draw the second at exactly the same strength.
 *
 * <p><b>为什么是世界而不是 GUI。</b>GUI 只能铺纯色，能提亮黑位、压暗四角，却永远没法柔化一条
 * 边；只提对比度的覆盖层看起来像浮在清晰画面上的灰尘。真正的模糊只能来自帧缓冲，所以世界被
 * 真实地失焦，覆盖层只负责本就属于它的部分：颗粒、撕裂扫描线和暗角。
 *
 * <h2>26.3 移植状态（WIP，世界失焦临时停用）</h2>
 *
 * <p>26.3 把 {@code PostChain} 重写成 framegraph 声明式管线：uniform 在加载时从 JSON 烘焙进
 * pass 的私有 {@code GpuBuffer}，旧版的 {@code new PostChain(...)} / {@code setUniform} /
 * {@code resize} / {@code process(float)} 逐帧接口全部移除，公开的 {@code process(RenderTarget,
 * GraphicsResourceAllocator)} 也只跑烘焙好的常量。而本效果需要**逐帧变化**的 {@code Radius}
 * （随距离）和 {@code BlurAmount}（随引爆），新 API 没有动态 uniform 的公开入口。
 *
 * <p>忠实移植需要：自造一条带可映射动态 uniform 缓冲的全屏 pass（类似 {@code PostPass} 的
 * {@code infoUbo}），把 {@code drone_blur.json} 迁到 26.3 的 {@code PostChainConfig} 格式
 * （{@code vertex_shader}/{@code fragment_shader}/{@code inputs}/{@code output}/uniforms map），
 * 并把 {@code box_blur}/{@code radial_blur} 着色器迁到 26.3 的 GLSL 与 uniform block 布局。
 * 这是一项独立子工程，尚未完成。
 *
 * <p>因此这里保留 {@link #amount} 等纯计算（{@link DroneHud} 仍按同一强度画覆盖层），
 * {@link #renderLevelStage} 暂为空操作。原版完整实现见 git 历史（main / port 分支早期提交）。
 */
public final class DroneSignal {

    /** Widest blur radius, in pixels, at full signal loss. 配置镜像，待 PostChain 重写后接回。 */
    private static float MAX_RADIUS = 6.0F;

    /** 客户端配置载入/热重载后把模糊半径写回镜像。 */
    public static void applyConfig() {
        MAX_RADIUS = dev.descrycraft.descrycraft.config.DescrycraftConfig.CLIENT.signalMaxBlurRadius
                .get().floatValue();
    }

    private DroneSignal() {
    }

    /**
     * 没装电池时画面劣化的强度，**故意大于 1**。
     *
     * <p>1.0 是"链路拉到极限"那一档：糊、下雪，但你还认得出树和墙。没电池要的不是"更远的距离"，
     * 而是"这块屏幕已经没用了"，所以这里越界，让模糊半径和雪花浓度一起再往上走一截。
     */
    public static final double NO_BATTERY_SEVERITY = 1.8D;

    /**
     * How far the picture has degraded: 0 at the airframe's own static onset, 1 at its own leash.
     *
     * <p>Both ends come off the drone rather than off the base constants, because signal boosters
     * push them out together - see {@link ReconDroneEntity#staticOnset()}. On a bare drone the two
     * ends are the original 48 and 64, exactly four {@code STATIC_STEP}s apart, so it still lands
     * on the grades the design calls for: clean at 48, a quarter at 52, half at 56, three quarters
     * at 60, whiteout at 64. A boosted one simply degrades over a proportionally longer run.
     *
     * <p>An airframe with no pack at all is the one case that returns more than 1 - see
     * {@link #NO_BATTERY_SEVERITY}.
     */
    public static double amount(ReconDroneEntity drone, double range) {
        // 没装电池的机体画面是全糊的 —— 直接借"最远距离"那一档再往上加，而不是另做一套效果：
        // 玩家已经知道画面糊是什么意思，缺的只是一个"为什么糊"的提示，那个提示在 DroneHud 里。
        if (!drone.hasBattery()) {
            return NO_BATTERY_SEVERITY;
        }
        double onset = drone.staticOnset();
        double span = drone.linkRange() - onset;
        if (span <= 0.0D) {
            return 0.0D;
        }
        return Mth.clamp((range - onset) / span, 0.0D, 1.0D);
    }

    /**
     * Once per level render, at the tail of {@code GameRenderer#renderLevel}.
     *
     * <p>26.3 PostChain 重写未完成，世界失焦/引爆径向模糊暂为空操作；见类注释。保留签名与
     * partialTicks 参数，待动态 uniform pass 落地后接回。
     */
    public static void renderLevelStage(float partialTicks) {
        // no-op：见类注释「26.3 移植状态」。
    }

    /** 待 PostChain 重写后接回：按距离算出这一帧的模糊半径。 */
    private static float radiusFor(Minecraft minecraft, ReconDroneEntity drone) {
        return (float) (MAX_RADIUS * amount(drone, Math.sqrt(drone.distanceToSqr(minecraft.player))));
    }
}