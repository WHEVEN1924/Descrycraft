package dev.descrycraft.descrycraft.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * 颗粒的 GPU 版本：一次全屏 pass 取代每帧几万次 1×1 的 GUI 填充。
 *
 * <p>原先信号劣化和引爆雪花都在 {@code DroneHud} 里逐颗粒调用 {@code GuiGraphics#fill}。
 * 引爆时一帧最多六万颗，每颗都是一次独立的顶点提交，于是雪花屏一触发就掉帧 —— 卡的是 CPU
 * 在往 GUI 顶点缓冲里塞四边形，不是 GPU 画不动。原版改由
 * {@code assets/descrycraft/shaders/program/screen_noise.fsh} 按格子哈希生成，每帧只画一次
 * 全屏四边形。
 *
 * <h2>26.3 移植状态（WIP，GPU 颗粒临时停用）</h2>
 *
 * <p>原因同 {@link DroneSignal}：26.3 的 {@code PostChain} 改成 framegraph 声明式管线，uniform
 * 在加载时烘焙进私有 {@code GpuBuffer}，没有逐帧 {@code setUniform}。而颗粒的 {@code GrainSeed}
 * **每帧都变**（{@code System.nanoTime()} / {@code millis / 40}），{@code GrainDensity} 也随信号
 * 强度连续变化，烘焙常量根本无法表现。忠实移植需要自造带动态 uniform 缓冲的全屏 pass，并把
 * {@code screen_noise.json} 与 {@code screen_noise.fsh} 迁到 26.3 格式，是一项独立子工程，尚未完成。
 *
 * <p>因此 {@link #render} 暂为空操作，保留签名供 {@link DroneHud} 在原层级调用；面罩与仪表盘
 * 照常绘制，只是少了颗粒/雪花这一层。原版完整实现见 git 历史（main / port 分支早期提交）。
 */
public final class DroneNoise {

    private DroneNoise() {
    }

    /** 提前建链的入口；26.3 PostChain 重写未完成，暂为空操作。见类注释。 */
    public static void prepare() {
        // no-op
    }

    /**
     * 在 GUI 上叠一层颗粒。调用点就是原来逐颗粒循环的位置，前后层级不变。
     *
     * <p>26.3 PostChain 重写未完成，暂为空操作；保留签名与参数，待动态 uniform pass 落地后接回。
     *
     * @param graphics   GUI 提取器（26.3 由 {@code GuiGraphics} 改名而来）
     * @param guiWidth   横向 GUI 像素数，格子按它切
     * @param guiHeight  纵向 GUI 像素数
     * @param density    每一格被点亮的概率，0..1
     * @param seed       重掷种子，只取低 16 位
     * @param detonation {@code true} 用引爆雪花的分布，{@code false} 用信号劣化的分布
     */
    public static void render(GuiGraphicsExtractor graphics, int guiWidth, int guiHeight,
                              double density, long seed, boolean detonation) {
        // no-op：见类注释「26.3 移植状态」。
    }

    /** 断链时释放链子；26.3 PostChain 重写未完成，暂为空操作。见类注释。 */
    public static void release() {
        // no-op
    }
}