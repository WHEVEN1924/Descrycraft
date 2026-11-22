package dev.descrycraft.descrycraft.client;

import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;

/**
 * 把驾驶员本体的反胃/传送门画面扭曲挡在无人机镜头之外。
 *
 * <p>无人机链路只把<b>相机实体</b>换成无人机（{@code Minecraft#setCameraEntity}），但投影矩阵上的
 * 扭曲读的是<b>本机玩家</b>的渲染快照，而玩家本体对象在操控期间始终还是本机玩家 —— 于是这份扭曲
 * 会照常叠在无人机镜头上。
 *
 * <h2>26.3 的两段式渲染与这里的钩子</h2>
 *
 * <p>26.3 把渲染改成"先抽取快照、再渲染"：{@code LevelExtractor#extractPlayerState} 把玩家的
 * {@code portalEffectIntensity}（由 oPortalEffectIntensity/portalEffectIntensity 插值）、
 * {@code nauseaEffectIntensity}（getEffectBlendFactor(NAUSEA)）、{@code spinningEffectAngle}
 * （getSpinningEffectAngle）写进 {@link PlayerRenderState}；{@code GameRenderer#renderLevel} 之后
 * <b>只读这份快照</b>来算投影矩阵的摇晃、横向拉伸与旋转。
 *
 * <p>NeoForge 的 {@code ExtractLevelRenderStateEvent} 正好在"所有原版快照抽取完之后"触发，且快照
 * 字段都是 public 可写的。所以这里在那个事件里把三个扭曲字段清零，投影矩阵那一段就等于看不到
 * 驾驶员的反胃/传送门状态。快照每帧 {@code reset()} 后重抽，<b>不需要"挪走再还回来"</b>，也就没有
 * 旧版（1.21.1 直接 park 玩家 spinningEffectIntensity 字段）那种漏还的风险。
 *
 * <h2>已知限制（26.3 移植 WIP）</h2>
 *
 * <p>HUD 上那层<b>平面</b>的传送门/反胃叠加（{@code Hud#extractPortalOverlay} /
 * {@code extractConfusionOverlay}）在 26.3 改成<b>直接读</b> {@code player.portalEffectIntensity}
 * 与 {@code getEffectBlendFactor(NAUSEA)}，不再走这份快照，所以本类挡不住它。要挡它得另找 HUD
 * 抽取钩子、或在 HUD 渲染那段临时挪玩家字段（反胃那一项还读 mob 效果，不是单纯字段），留待后续。
 *
 * <p>受伤红屏不在这里，因为它本来就是对的：它取的是相机实体，操控期间那就是无人机自己。
 */
public final class DroneVisor {

    private DroneVisor() {
    }

    /**
     * 在原版快照抽取完之后调用（{@code ExtractLevelRenderStateEvent}）：操控期间把玩家快照里的
     * 反胃/传送门扭曲清零，让这一帧的无人机镜头不带驾驶员本体的画面扭曲。
     *
     * <p>没在操控就什么都不碰 —— 普通游戏完全不受影响。
     */
    public static void suppressPilotNausea(LevelRenderState state) {
        if (!DroneController.isLinked()) {
            return;
        }
        PlayerRenderState player = state.playerRenderState;
        player.portalEffectIntensity = 0.0F;
        player.nauseaEffectIntensity = 0.0F;
        player.spinningEffectAngle = 0.0F;
    }
}