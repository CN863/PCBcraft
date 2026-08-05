package com.pcbcraft.data;

import net.minecraft.nbt.CompoundTag;

import java.util.TreeSet;
import java.util.Set;

/**
 * 过孔，连接不同铜层的通孔。
 * <p>
 * {@code pos} 为过孔中心板内坐标，{@code holeSize} 为孔径（方块数），
 * {@code connectedLayers} 为该过孔连接的铜层索引集合。
 * </p>
 */
public final class Via {
    /** 过孔中心板内坐标。 */
    private final GridPoint pos;
    /** 孔径（方块数）。 */
    private final int holeSize;
    /** 连接的铜层索引集合。 */
    private final Set<Integer> connectedLayers;

    /**
     * 构造过孔。
     *
     * @param pos              中心坐标
     * @param holeSize         孔径
     * @param connectedLayers  连接的铜层索引集合（按引用持有，调用方不应再修改）
     */
    public Via(GridPoint pos, int holeSize, Set<Integer> connectedLayers) {
        this.pos = pos;
        this.holeSize = holeSize;
        this.connectedLayers = connectedLayers;
    }

    public GridPoint getPos() {
        return pos;
    }

    public int getHoleSize() {
        return holeSize;
    }

    /**
     * 返回连接的铜层索引集合（可变，供编辑器修改）。
     *
     * @return 连接铜层索引集合
     */
    public Set<Integer> getConnectedLayers() {
        return connectedLayers;
    }

    /**
     * 序列化为 NBT，连接层集合以 int 数组紧凑存储。
     *
     * @return 包含全部字段的 CompoundTag
     */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("pos", pos.save());
        tag.putInt("holeSize", holeSize);
        int[] arr = new int[connectedLayers.size()];
        int i = 0;
        for (Integer l : connectedLayers) {
            arr[i++] = l;
        }
        tag.putIntArray("connectedLayers", arr);
        return tag;
    }

    /**
     * 从 NBT 反序列化。
     *
     * @param tag 包含过孔字段的 CompoundTag
     * @return 重建的过孔实例
     */
    public static Via load(CompoundTag tag) {
        Set<Integer> layers = new TreeSet<>();
        for (int l : tag.getIntArray("connectedLayers").orElse(new int[0])) {
            layers.add(l);
        }
        return new Via(
                GridPoint.load(tag.getCompoundOrEmpty("pos")),
                tag.getIntOr("holeSize", 0),
                layers
        );
    }
}
