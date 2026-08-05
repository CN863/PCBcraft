package com.pcbcraft.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 网络，一组电气连通的节点集合。
 * <p>
 * {@code nodes} 为该网络覆盖的所有电气节点（焊盘、走线点、过孔点），
 * 使用 {@link LinkedHashSet} 保持插入顺序以便确定性序列化。
 * </p>
 */
public final class Net {
    /** 网络名（唯一标识）。 */
    private final String name;
    /** 电气类型。 */
    private final ElectricalType type;
    /** 网络全部电气节点集合。 */
    private final Set<GridPoint> nodes;

    /**
     * 构造网络。
     *
     * @param name  网络名
     * @param type  电气类型
     * @param nodes 电气节点集合（按引用持有，调用方不应再修改）
     */
    public Net(String name, ElectricalType type, Set<GridPoint> nodes) {
        this.name = name;
        this.type = type;
        this.nodes = nodes;
    }

    public String getName() {
        return name;
    }

    public ElectricalType getType() {
        return type;
    }

    /**
     * 返回电气节点集合（可变，供编辑器修改）。
     *
     * @return 电气节点集合
     */
    public Set<GridPoint> getNodes() {
        return nodes;
    }

    /**
     * 序列化为 NBT，节点集合以 ListTag 嵌套 CompoundTag 存储。
     *
     * @return 包含全部字段的 CompoundTag
     */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", name);
        tag.putString("type", type.getSerializedName());
        ListTag nodeList = new ListTag();
        for (GridPoint g : nodes) {
            nodeList.add(g.save());
        }
        tag.put("nodes", nodeList);
        return tag;
    }

    /**
     * 从 NBT 反序列化。
     *
     * @param tag 包含网络字段的 CompoundTag
     * @return 重建的网络实例
     */
    public static Net load(CompoundTag tag) {
        Set<GridPoint> nodes = new LinkedHashSet<>();
        ListTag nodeList = tag.getListOrEmpty("nodes");
        for (int i = 0; i < nodeList.size(); i++) {
            nodes.add(GridPoint.load(nodeList.getCompoundOrEmpty(i)));
        }
        return new Net(
                tag.getStringOr("name", ""),
                ElectricalType.byName(tag.getStringOr("type", "")),
                nodes
        );
    }

    /**
     * 网络电气类型枚举。
     * <ul>
     *   <li>{@link #POWER}：电源网络</li>
     *   <li>{@link #GROUND}：地网络</li>
     *   <li>{@link #SIGNAL}：信号网络</li>
     * </ul>
     */
    public enum ElectricalType {
        POWER("power"),
        GROUND("ground"),
        SIGNAL("signal");

        /** 用于持久化的小写序列化名。 */
        private final String serializedName;

        ElectricalType(String serializedName) {
            this.serializedName = serializedName;
        }

        /**
         * 返回小写序列化名。
         *
         * @return 小写序列化名
         */
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
        public static ElectricalType byName(String name) {
            for (ElectricalType t : values()) {
                if (t.serializedName.equals(name)) {
                    return t;
                }
            }
            throw new IllegalArgumentException("未知网络电气类型: " + name);
        }
    }
}
