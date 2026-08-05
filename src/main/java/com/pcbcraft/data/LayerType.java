package com.pcbcraft.data;

import net.minecraft.util.StringRepresentable;

/**
 * PCB 图层类型枚举。
 * <ul>
 *   <li>{@link #COPPER}：铜层，承载走线、焊盘与过孔电气连接</li>
 *   <li>{@link #MASK}：阻焊层，覆盖铜层以防止焊料桥接</li>
 *   <li>{@link #SILK}：丝印层，元件位号与板面标识</li>
 *   <li>{@link #DRILL}：钻孔层，过孔与通孔焊盘的钻孔信息</li>
 * </ul>
 * <p>
 * 每个枚举值附带小写序列化名，用于 NBT / JSON 持久化与网络同步。
 * </p>
 */
public enum LayerType implements StringRepresentable {
    COPPER("copper"),
    MASK("mask"),
    SILK("silk"),
    DRILL("drill");

    /** 用于持久化的小写序列化名。 */
    private final String serializedName;

    LayerType(String serializedName) {
        this.serializedName = serializedName;
    }

    /**
     * 返回用于 NBT / JSON 持久化的小写序列化名（"copper"/"mask"/"silk"/"drill"）。
     *
     * @return 小写序列化名
     */
    @Override
    public String getSerializedName() {
        return serializedName;
    }

    /**
     * 根据序列化名查找枚举值。
     *
     * @param name 序列化名（大小写敏感）
     * @return 匹配的枚举值
     * @throws IllegalArgumentException 当传入名称无法匹配任何枚举值
     */
    public static LayerType byName(String name) {
        for (LayerType t : values()) {
            if (t.serializedName.equals(name)) {
                return t;
            }
        }
        throw new IllegalArgumentException("未知图层类型: " + name);
    }
}
