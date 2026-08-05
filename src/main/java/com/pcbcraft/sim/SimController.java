package com.pcbcraft.sim;

import com.pcbcraft.data.GridPoint;
import net.minecraft.core.BlockPos;

/**
 * 仿真状态查询门面（Phase 4.5）。
 * <p>
 * 为其它模块（如 Phase 6 调试工具、渲染层电压热力图）提供统一的只读查询入口，
 * 内部委托 {@link SimTickScheduler} 注册表，屏蔽仿真器/故障模型的直接依赖。
 * </p>
 * <p>所有方法在指定 BlockPos 未注册时返回安全默认值（null / 0.0 / false）。</p>
 */
public final class SimController {

    private SimController() {
    }

    /**
     * 返回指定位置最近一次的仿真解。
     *
     * @param pos 主方块坐标
     * @return 仿真解；未注册或未 step 过返回 {@code null}
     */
    public static MnaSolver.SimSolution solution(BlockPos pos) {
        CircuitSimulator sim = SimTickScheduler.simulator(pos);
        return (sim != null) ? sim.lastSolution() : null;
    }

    /**
     * 返回指定位置的故障模型。
     *
     * @param pos 主方块坐标
     * @return 故障模型；未注册返回 {@code null}
     */
    public static FaultModel fault(BlockPos pos) {
        return SimTickScheduler.fault(pos);
    }

    /**
     * 返回指定位置板内坐标处的节点电压。
     *
     * @param pos   主方块坐标
     * @param point 板内坐标
     * @return 电压（未连接或未注册返回 0）
     */
    public static double voltageAt(BlockPos pos, GridPoint point) {
        CircuitSimulator sim = SimTickScheduler.simulator(pos);
        return (sim != null) ? sim.voltageAt(point) : 0.0;
    }

    /**
     * 指定位置的电路是否已停机（跳闸或断电）。
     *
     * @param pos 主方块坐标
     * @return 停机返回 true；未注册返回 false
     */
    public static boolean isShutDown(BlockPos pos) {
        FaultModel f = SimTickScheduler.fault(pos);
        if (f != null && f.isShutDown()) {
            return true;
        }
        CircuitSimulator sim = SimTickScheduler.simulator(pos);
        return sim != null && sim.isShutDown();
    }
}
