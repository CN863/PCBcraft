package com.pcbcraft.data;

import net.minecraft.nbt.CompoundTag;

/**
 * PCB 单个图层定义。
 * <p>
 * {@code index} 从 0 开始（顶层铜），递增向下；{@code type} 标识图层语义；
 * {@code visible} / {@code editable} 控制编辑器交互；{@code name} 为显示名。
 * 本类为不可变值对象，可见性变更通过 {@link #withVisible(boolean)} 返回新副本。
 * </p>
 */
public final class Layer {
    /** 图层索引，0 表示顶层铜，递增向下。 */
    private final int index;
    /** 图层语义类型。 */
    private final LayerType type;
    /** 是否在编辑器中可见。 */
    private final boolean visible;
    /** 是否可编辑（铜层默认可编辑，阻焊/丝印/钻孔默认只读）。 */
    private final boolean editable;
    /** 图层显示名。 */
    private final String name;

    /**
     * 构造图层。
     *
     * @param index    图层索引
     * @param type     图层类型
     * @param visible  是否可见
     * @param editable 是否可编辑
     * @param name     显示名
     */
    public Layer(int index, LayerType type, boolean visible, boolean editable, String name) {
        this.index = index;
        this.type = type;
        this.visible = visible;
        this.editable = editable;
        this.name = name;
    }

    public int getIndex() {
        return index;
    }

    public LayerType getType() {
        return type;
    }

    public boolean isVisible() {
        return visible;
    }

    public boolean isEditable() {
        return editable;
    }

    public String getName() {
        return name;
    }

    /**
     * 返回可见性修改后的新副本，其余字段保持不变。
     *
     * @param visible 新的可见性
     * @return 修改后的新图层实例
     */
    public Layer withVisible(boolean visible) {
        return new Layer(this.index, this.type, visible, this.editable, this.name);
    }

    /**
     * 序列化为 NBT。
     *
     * @return 包含全部字段的 CompoundTag
     */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("index", index);
        tag.putString("type", type.getSerializedName());
        tag.putBoolean("visible", visible);
        tag.putBoolean("editable", editable);
        tag.putString("name", name);
        return tag;
    }

    /**
     * 从 NBT 反序列化。
     *
     * @param tag 包含图层字段的 CompoundTag
     * @return 重建的图层实例
     */
    public static Layer load(CompoundTag tag) {
        return new Layer(
                tag.getIntOr("index", 0),
                LayerType.byName(tag.getStringOr("type", "")),
                tag.getBooleanOr("visible", false),
                tag.getBooleanOr("editable", false),
                tag.getStringOr("name", "")
        );
    }
}
