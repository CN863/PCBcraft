package com.pcbcraft.data;

import net.minecraft.nbt.CompoundTag;

/**
 * PCB 网格坐标，不可变值对象（方块级精度）。
 * <p>
 * 坐标 {@code (x, y)} 为板内网格坐标，原点位于板左上角。
 * 作为 record 自动获得 {@code equals}/{@code hashCode}/{@code toString}，
 * 因此可安全作为 {@link java.util.Set} 元素与 {@link java.util.Map} 键。
 * </p>
 *
 * @param x 横坐标（向右递增）
 * @param y 纵坐标（向下递增）
 */
public record GridPoint(int x, int y) {

    /**
     * 静态工厂，等价于 {@code new GridPoint(x, y)}，便于链式可读。
     *
     * @param x 横坐标
     * @param y 纵坐标
     * @return 新的网格坐标
     */
    public static GridPoint of(int x, int y) {
        return new GridPoint(x, y);
    }

    /**
     * 序列化为 NBT。
     *
     * @return 包含 x / y 的 CompoundTag
     */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", x);
        tag.putInt("y", y);
        return tag;
    }

    /**
     * 从 NBT 反序列化。
     *
     * @param tag 包含 x / y 的 CompoundTag
     * @return 重建的网格坐标
     */
    public static GridPoint load(CompoundTag tag) {
        return new GridPoint(tag.getIntOr("x", 0), tag.getIntOr("y", 0));
    }
}
