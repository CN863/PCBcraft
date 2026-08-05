package com.pcbcraft.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * 元件实例，引用 {@code ComponentDef}（库定义）并在板上具体放置。
 * <p>
 * 仅以字符串 {@code componentId} 引用库定义，避免与 {@code com.pcbcraft.library} 包耦合。
 * {@code origin} 为放置原点，{@code rotation} 为 0/90/180/270 旋转，
 * {@code pads} 为按封装与旋转计算后的实际板内焊盘坐标列表。
 * </p>
 */
public final class ComponentInstance {
    /** 引用的元件库定义 id。 */
    private final String componentId;
    /** 板上位号（如 R1、U3）。 */
    private final String designator;
    /** 放置原点（板内坐标）。 */
    private final GridPoint origin;
    /** 旋转角度，仅允许 0/90/180/270。 */
    private final int rotation;
    /** 计算后的实际焊盘列表。 */
    private final List<Pad> pads;

    /**
     * 构造元件实例。
     *
     * @param componentId 引用的元件定义 id
     * @param designator  位号
     * @param origin      放置原点
     * @param rotation    旋转角度（0/90/180/270）
     * @param pads        计算后的焊盘列表（按引用持有，调用方不应再修改）
     */
    public ComponentInstance(String componentId, String designator, GridPoint origin, int rotation, List<Pad> pads) {
        this.componentId = componentId;
        this.designator = designator;
        this.origin = origin;
        this.rotation = rotation;
        this.pads = pads;
    }

    public String getComponentId() {
        return componentId;
    }

    public String getDesignator() {
        return designator;
    }

    public GridPoint getOrigin() {
        return origin;
    }

    public int getRotation() {
        return rotation;
    }

    /**
     * 返回焊盘列表（可变，供编辑器增删）。
     *
     * @return 焊盘列表
     */
    public List<Pad> getPads() {
        return pads;
    }

    /**
     * 返回指定引脚编号对应焊盘的板内世界坐标。
     *
     * @param pinNumber 引脚编号
     * @return 该引脚焊盘的板内坐标，若未找到则返回 {@code null}
     */
    public GridPoint pinPos(int pinNumber) {
        for (Pad p : pads) {
            if (p.getPinNumber() == pinNumber) {
                return p.getPos();
            }
        }
        return null;
    }

    /**
     * 序列化为 NBT，焊盘列表以 ListTag 嵌套 CompoundTag 存储。
     *
     * @return 包含全部字段的 CompoundTag
     */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("componentId", componentId);
        tag.putString("designator", designator);
        tag.put("origin", origin.save());
        tag.putInt("rotation", rotation);
        ListTag padList = new ListTag();
        for (Pad p : pads) {
            padList.add(p.save());
        }
        tag.put("pads", padList);
        return tag;
    }

    /**
     * 从 NBT 反序列化。
     *
     * @param tag 包含元件实例字段的 CompoundTag
     * @return 重建的元件实例
     */
    public static ComponentInstance load(CompoundTag tag) {
        List<Pad> pads = new ArrayList<>();
        ListTag padList = tag.getListOrEmpty("pads");
        for (int i = 0; i < padList.size(); i++) {
            pads.add(Pad.load(padList.getCompoundOrEmpty(i)));
        }
        return new ComponentInstance(
                tag.getStringOr("componentId", ""),
                tag.getStringOr("designator", ""),
                GridPoint.load(tag.getCompoundOrEmpty("origin")),
                tag.getIntOr("rotation", 0),
                pads
        );
    }
}
