package com.pcbcraft.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * 走线，单层铜上的曼哈顿折线路径。
 * <p>
 * {@code path} 为路径拐点列表（至少 2 点），相邻点之间仅水平或垂直连接；
 * {@code width} 为走线宽度（方块数），{@code net} 为所属网络名。
 * </p>
 */
public final class Trace {
    /** 所在铜层索引。 */
    private final int layerIndex;
    /** 曼哈顿路径拐点列表（至少 2 点）。 */
    private final List<GridPoint> path;
    /** 走线宽度（方块数）。 */
    private final int width;
    /** 所属网络名。 */
    private final String net;

    /**
     * 构造走线。
     *
     * @param layerIndex 所在铜层索引
     * @param path       路径拐点列表（至少 2 点）
     * @param width      走线宽度
     * @param net        所属网络名
     */
    public Trace(int layerIndex, List<GridPoint> path, int width, String net) {
        this.layerIndex = layerIndex;
        this.path = path;
        this.width = width;
        this.net = net;
    }

    public int getLayerIndex() {
        return layerIndex;
    }

    /**
     * 返回路径拐点列表（可变，供编辑器修改）。
     *
     * @return 路径拐点列表
     */
    public List<GridPoint> getPath() {
        return path;
    }

    public int getWidth() {
        return width;
    }

    public String getNet() {
        return net;
    }

    /**
     * 计算路径总曼哈顿长度（相邻拐点 |dx|+|dy| 之和）。
     *
     * @return 总曼哈顿长度（方块数）
     */
    public int length() {
        int total = 0;
        for (int i = 1; i < path.size(); i++) {
            GridPoint a = path.get(i - 1);
            GridPoint b = path.get(i);
            total += Math.abs(b.x() - a.x()) + Math.abs(b.y() - a.y());
        }
        return total;
    }

    /**
     * 序列化为 NBT，路径以 ListTag 嵌套 CompoundTag 存储。
     *
     * @return 包含全部字段的 CompoundTag
     */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("layerIndex", layerIndex);
        tag.putInt("width", width);
        tag.putString("net", net);
        ListTag pathList = new ListTag();
        for (GridPoint p : path) {
            pathList.add(p.save());
        }
        tag.put("path", pathList);
        return tag;
    }

    /**
     * 从 NBT 反序列化。
     *
     * @param tag 包含走线字段的 CompoundTag
     * @return 重建的走线实例
     */
    public static Trace load(CompoundTag tag) {
        List<GridPoint> path = new ArrayList<>();
        ListTag pathList = tag.getListOrEmpty("path");
        for (int i = 0; i < pathList.size(); i++) {
            path.add(GridPoint.load(pathList.getCompoundOrEmpty(i)));
        }
        return new Trace(
                tag.getIntOr("layerIndex", 0),
                path,
                tag.getIntOr("width", 0),
                tag.getStringOr("net", "")
        );
    }
}
