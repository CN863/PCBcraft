package com.pcbcraft.sim;

import com.pcbcraft.PCBConfig;
import com.pcbcraft.data.Trace;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 信号传播延迟计算。
 * <p>
 * 走线每方块长度产生固定延迟（由 {@link PCBConfig#signalDelayTicksPerBlock()} 配置），
 * 用于仿真调度信号到达时刻。延迟 = 路径长度（方块数） × 每方块延迟 tick。
 * </p>
 */
public final class SignalPropagator {

    private SignalPropagator() {
    }

    /**
     * 按路径长度计算延迟 tick。
     *
     * @param pathLengthBlocks 路径长度（方块数）
     * @return 延迟 tick
     */
    public static int delayTicks(int pathLengthBlocks) {
        if (pathLengthBlocks <= 0) {
            return 0;
        }
        return pathLengthBlocks * PCBConfig.signalDelayTicksPerBlock();
    }

    /**
     * 返回单条走线长度（方块数）。
     *
     * @param t 走线
     * @return 曼哈顿总长度
     */
    public static int traceLength(Trace t) {
        return t.length();
    }

    /**
     * 按网络名聚合该网络所有走线总长度 → 延迟 tick。
     * <p>用于仿真调度信号到达：同一网络的走线总长决定其传播延迟。</p>
     *
     * @param netlist 网表（含各网络走线总长度）
     * @return 网络名 → 延迟 tick
     */
    public static Map<String, Integer> netDelays(Netlist netlist) {
        Map<String, Integer> delays = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : netlist.getNetTraceLengths().entrySet()) {
            delays.put(e.getKey(), delayTicks(e.getValue()));
        }
        return delays;
    }
}
