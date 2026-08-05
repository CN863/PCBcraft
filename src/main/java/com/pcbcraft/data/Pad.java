package com.pcbcraft.data;

import net.minecraft.nbt.CompoundTag;

/**
 * 焊盘，元件封装在板上的实例化引脚连接点。
 * <p>
 * 焊盘位置 {@code pos} 为元件放置并按 {@code rotation} 旋转后计算得到的板内世界坐标，
 * {@code size} 为焊盘半径（方块数，0 表示单格），{@code layerIndex} 指明所在铜层。
 * 本类为不可变值对象。
 * </p>
 */
public final class Pad {
    /** 引脚编号（对应封装定义中的 pin number）。 */
    private final int pinNumber;
    /** 焊盘板内世界坐标。 */
    private final GridPoint pos;
    /** 焊盘半径（方块数），0 表示单格焊盘。 */
    private final int size;
    /** 所在铜层索引。 */
    private final int layerIndex;

    /**
     * 构造焊盘。
     *
     * @param pinNumber  引脚编号
     * @param pos        板内坐标
     * @param size       半径（方块数，默认 0）
     * @param layerIndex 所在铜层索引
     */
    public Pad(int pinNumber, GridPoint pos, int size, int layerIndex) {
        this.pinNumber = pinNumber;
        this.pos = pos;
        this.size = size;
        this.layerIndex = layerIndex;
    }

    public int getPinNumber() {
        return pinNumber;
    }

    public GridPoint getPos() {
        return pos;
    }

    public int getSize() {
        return size;
    }

    public int getLayerIndex() {
        return layerIndex;
    }

    /**
     * 序列化为 NBT。
     *
     * @return 包含全部字段的 CompoundTag
     */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("pinNumber", pinNumber);
        tag.put("pos", pos.save());
        tag.putInt("size", size);
        tag.putInt("layerIndex", layerIndex);
        return tag;
    }

    /**
     * 从 NBT 反序列化。
     *
     * @param tag 包含焊盘字段的 CompoundTag
     * @return 重建的焊盘实例
     */
    public static Pad load(CompoundTag tag) {
        return new Pad(
                tag.getIntOr("pinNumber", 0),
                GridPoint.load(tag.getCompoundOrEmpty("pos")),
                tag.getIntOr("size", 0),
                tag.getIntOr("layerIndex", 0)
        );
    }
}
