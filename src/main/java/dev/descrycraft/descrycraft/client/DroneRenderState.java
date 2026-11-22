package dev.descrycraft.descrycraft.client;

import java.util.Map;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.joml.Matrix4f;

/**
 * Per-frame snapshot of the drone, filled in {@link DroneRenderer#extractRenderState} and consumed
 * in {@link DroneRenderer#submit}. 26.x 把实体渲染拆成「提取状态」与「提交绘制」两段，绘制线程只读
 * 这份状态，不再直接碰实体。
 */
public class DroneRenderState extends EntityRenderState {
    public float yaw;
    public float pitch;
    public float bank;
    public float roll;
    public boolean hurt;
    public boolean glowRed;
    public Map<String, Matrix4f> rig = Map.of();
}