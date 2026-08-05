package com.pcbcraft.render;

import com.pcbcraft.PCBCraft;
import com.pcbcraft.block.PcbBlockEntity;
import com.pcbcraft.data.GridPoint;
import com.pcbcraft.data.PcbDesign;
import com.pcbcraft.data.Trace;
import com.pcbcraft.net.SimStatePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
// TODO: MC 26.2 - RenderLevelStageEvent 已移除，待迁移到新渲染事件
// import net.minecraftforge.client.event.RenderLevelStageEvent;
// import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
// import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * PCB 信号可视化渲染器（Task 6.2 / 6.4）。
 * <p>
 * 在 {@link RenderLevelStageEvent.Stage#AFTER_TRANSLUCENT_BLOCKS} 阶段，对
 * {@link VisualizationState#enabled()} 中每个 master 的 PCB，按客户端缓存的
 * {@link SimStatePacket} 节点电压对其铜层走线方块绘制彩色线框覆盖。
 * </p>
 * <p>
 * 注意：MC 26.2 移除了 Tesselator/MultiBufferSource/RenderType 等旧渲染管线，
 * 下方渲染方法暂时禁用，等待 MC 26.2 原生渲染 API 集成。
 * </p>
 */
@OnlyIn(Dist.CLIENT)
public final class PcbVisualizationRender {

    /** 满量程电压（V），用于热力图与二值阈值。 */
    private static final double V_MAX = 5.0;
    private static final double HIGH_THRESHOLD = 2.5;
    private static final double LOW_THRESHOLD = 0.5;
    /** 线框相对方块边界的内缩量，避免与方块面共面。 */
    private static final double INSET = 0.1;
    /** 线框透明度。 */
    private static final float ALPHA = 0.85f;
    /** 单帧每块 PCB 最大绘制格子数，防止超大板卡顿。 */
    private static final int MAX_CELLS = 2000;
    /** 电流判定阈值：trace 两端节点电压差超过此值视为有电流流动（V）。 */
    private static final double CURRENT_THRESHOLD = 0.1;
    /** 每帧每格生成电流粒子的概率，控制粒子密度避免泛滥。 */
    private static final double PARTICLE_PROBABILITY = 0.1;
    /** 粒子沿路径的格间距，每隔 step 格生成一个粒子。 */
    private static final int PARTICLE_STEP = 3;

    private PcbVisualizationRender() {
    }

    // TODO: MC 26.2 - RenderLevelStageEvent 已移除，待迁移
    // 当新渲染事件 API 确定后，恢复此方法并迁移到新 API
    // @SubscribeEvent
    // public static void onRenderLevel(RenderLevelStageEvent event) {
    //     if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
    //         return;
    //     }
    //     ...
    // }
    public static void onRenderLevelDummy() {
        // 粒子流动功能已迁移到独立方法，可通过其他方式触发
    }

    /**
     * 仅粒子流动（不依赖渲染管线），使用原版粒子系统。
     */
    private static void spawnFlowParticlesOnly(Level level, BlockPos master, Vec3 cam) {
        BlockEntity be = level.getBlockEntity(master);
        if (!(be instanceof PcbBlockEntity pcb)) {
            return;
        }
        PcbDesign design = pcb.getDesign();
        if (design == null) {
            return;
        }
        // 仅在距离玩家 32 格内生成粒子
        double dist = cam.distanceToSqr(master.getX() + 0.5, master.getY() + 0.5, master.getZ() + 0.5);
        if (dist > 32 * 32) {
            return;
        }
        SimStatePacket state = ClientSimState.get(master);
        if (state == null) {
            return;
        }
        int copperCount = design.copperLayerCount();
        long now = System.currentTimeMillis();
        double phase = (now % 1000L) / 1000.0;
        int offset = (int) (phase * PARTICLE_STEP);
        for (Trace t : design.getTraces()) {
            int layerY = t.getLayerIndex();
            if (layerY < 0 || layerY >= copperCount) {
                continue;
            }
            List<GridPoint> path = t.getPath();
            if (path == null || path.size() < 2) {
                continue;
            }
            double vStart = ClientSimState.voltageAt(master, path.get(0), design);
            double vEnd = ClientSimState.voltageAt(master, path.get(path.size() - 1), design);
            double dv = vStart - vEnd;
            if (Math.abs(dv) <= CURRENT_THRESHOLD) {
                continue;
            }
            int dir = dv > 0 ? 1 : -1;
            List<GridPoint> cells = walkCells(path);
            int n = cells.size();
            if (n < 2) {
                continue;
            }
            for (int i = offset; i < n; i += PARTICLE_STEP) {
                if (Math.random() > PARTICLE_PROBABILITY) {
                    continue;
                }
                GridPoint cell = cells.get(i);
                double px = master.getX() + cell.x() + 0.5;
                double py = master.getY() + layerY + 1.0;
                double pz = master.getZ() + cell.y() + 0.5;
                int nextIdx = i + dir;
                double dx = 0.0;
                double dz = 0.0;
                if (nextIdx >= 0 && nextIdx < n) {
                    GridPoint next = cells.get(nextIdx);
                    dx = next.x() - cell.x();
                    dz = next.y() - cell.y();
                }
                level.addParticle(ParticleTypes.CRIT, px, py, pz, dx, 0.0, dz);
            }
        }
    }

    /**
     * 遍历走线路径覆盖的所有格子（曼哈顿逐格），去重。
     */
    private static List<GridPoint> walkCells(List<GridPoint> path) {
        List<GridPoint> cells = new ArrayList<>();
        Set<GridPoint> seen = new HashSet<>();
        for (int i = 1; i < path.size(); i++) {
            GridPoint a = path.get(i - 1);
            GridPoint b = path.get(i);
            int dx = Integer.signum(b.x() - a.x());
            int dy = Integer.signum(b.y() - a.y());
            int cx = a.x();
            int cy = a.y();
            addCell(cells, seen, cx, cy);
            while (cx != b.x() || cy != b.y()) {
                cx += dx;
                cy += dy;
                addCell(cells, seen, cx, cy);
            }
        }
        if (cells.isEmpty() && !path.isEmpty()) {
            addCell(cells, seen, path.get(0).x(), path.get(0).y());
        }
        return cells;
    }

    private static void addCell(List<GridPoint> cells, Set<GridPoint> seen, int x, int y) {
        GridPoint g = GridPoint.of(x, y);
        if (seen.add(g)) {
            cells.add(g);
        }
    }
}