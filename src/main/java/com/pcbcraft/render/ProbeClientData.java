package com.pcbcraft.render;

import com.pcbcraft.data.GridPoint;
import net.minecraft.core.BlockPos;

/**
 * 探针客户端读数缓存（Task 6.1）。
 * <p>
 * 持有最近一次探针右键的坐标与时间戳，供 {@link ProbeOverlay} 在屏幕右上角绘制浮窗。
 * 本类为纯数据容器（不引用客户端专有 API），由 {@link com.pcbcraft.tool.ProbeItem} 在客户端
 * {@code useOn} 分支写入；浮窗电压由 {@link ClientSimState} 缓存的 SimStatePacket 反查。
 * </p>
 * <p>波形采样（持续右键周期采样）本阶段留 TODO，当前仅缓存最近一次瞬时读数坐标。</p>
 */
public final class ProbeClientData {

    /** 浮窗保留时长（毫秒），超时后浮窗消失。 */
    private static final long RETAIN_MS = 10_000L;

    private static volatile BlockPos master;
    private static volatile GridPoint point;
    private static volatile int layerY;
    private static volatile long stampMs;

    private ProbeClientData() {
    }

    /**
     * 记录最近一次探针读数坐标。
     *
     * @param masterPos 主方块坐标
     * @param point     板内网格坐标
     * @param layerY    层 Y 偏移
     */
    public static void set(BlockPos masterPos, GridPoint point, int layerY) {
        ProbeClientData.master = masterPos != null ? masterPos.immutable() : null;
        ProbeClientData.point = point;
        ProbeClientData.layerY = layerY;
        ProbeClientData.stampMs = System.currentTimeMillis();
    }

    /**
     * 返回当前有效读数，超时返回 {@code null}。
     *
     * @return 读数快照；超时返回 {@code null}
     */
    public static Reading current() {
        if (master == null || point == null) {
            return null;
        }
        if (System.currentTimeMillis() - stampMs > RETAIN_MS) {
            return null;
        }
        return new Reading(master, point, layerY);
    }

    /** 不可变读数快照。 */
    public static final class Reading {
        public final BlockPos master;
        public final GridPoint point;
        public final int layerY;

        public Reading(BlockPos master, GridPoint point, int layerY) {
            this.master = master;
            this.point = point;
            this.layerY = layerY;
        }
    }
}
