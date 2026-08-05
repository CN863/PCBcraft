package com.pcbcraft.render;

import net.minecraft.core.BlockPos;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 信号可视化客户端状态（Task 6.2 / 6.4）。
 * <p>
 * 纯客户端维护两类状态：
 * </p>
 * <ul>
 *   <li>{@link #enabled}：已启用信号可视化的 PCB master 坐标集合，
 *       由 {@link com.pcbcraft.tool.OscilloscopeItem} 非潜行右键切换；</li>
 *   <li>{@link #heatmapMode}：热力图模式开关，由 {@link com.pcbcraft.tool.OscilloscopeItem}
 *       潜行右键切换。开启时 {@link PcbVisualizationRender} 按节点电压渐变染色，
 *       关闭时按二值红/蓝染色。</li>
 * </ul>
 * <p>本类为纯数据容器（不引用客户端专有 API），可由公共物品类安全引用。</p>
 */
public final class VisualizationState {

    private static final Set<BlockPos> enabled = ConcurrentHashMap.newKeySet();
    private static volatile boolean heatmapMode = false;

    private VisualizationState() {
    }

    /**
     * 切换指定 PCB 的可视化启用状态。
     *
     * @param master 主方块坐标
     * @return 切换后是否启用
     */
    public static boolean toggle(BlockPos master) {
        BlockPos key = master.immutable();
        if (enabled.remove(key)) {
            return false;
        }
        enabled.add(key);
        return true;
    }

    /**
     * 切换热力图模式。
     *
     * @return 切换后是否启用热力图
     */
    public static boolean toggleHeatmap() {
        heatmapMode = !heatmapMode;
        return heatmapMode;
    }

    /** @return 是否启用热力图模式 */
    public static boolean isHeatmap() {
        return heatmapMode;
    }

    /** @return 已启用可视化的 master 集合（只读视图） */
    public static Set<BlockPos> enabled() {
        return enabled;
    }

    /**
     * @param master 主方块坐标
     * @return 该 PCB 是否已启用可视化
     */
    public static boolean isEnabled(BlockPos master) {
        return enabled.contains(master);
    }
}
