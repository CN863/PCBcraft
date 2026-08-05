package com.pcbcraft.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * PCB 设计聚合根，包含一块电路板的完整可编辑状态。
 * <p>
 * 被 {@code com.pcbcraft.editor} 的编辑器 GUI、{@code com.pcbcraft.block} 的 BlockEntity 持久化、
 * {@code com.pcbcraft.sim} 的仿真引擎三方共享。通过 {@link #save(CompoundTag)} /
 * {@link #load(CompoundTag)} 完成 NBT 序列化（同时服务于磁盘持久化与后续网络同步），
 * 通过 {@link #copy()} 提供深拷贝以支撑编辑器撤销栈。
 * </p>
 */
public final class PcbDesign {
    /** 板宽（方块数）。 */
    private final int width;
    /** 板高（方块数）。 */
    private final int height;
    /** 图层列表，按 index 升序，含铜层/阻焊/丝印/钻孔。 */
    private final List<Layer> layers;
    /** 元件实例列表。 */
    private final List<ComponentInstance> components;
    /** 走线列表。 */
    private final List<Trace> traces;
    /** 过孔列表。 */
    private final List<Via> vias;
    /** 网络列表。 */
    private final List<Net> nets;
    /** 设计名。 */
    private final String name;

    /**
     * 构造 PCB 设计。
     *
     * @param width      板宽
     * @param height     板高
     * @param layers     图层列表
     * @param components 元件实例列表
     * @param traces     走线列表
     * @param vias       过孔列表
     * @param nets       网络列表
     * @param name       设计名
     */
    public PcbDesign(int width, int height, List<Layer> layers, List<ComponentInstance> components,
                     List<Trace> traces, List<Via> vias, List<Net> nets, String name) {
        this.width = width;
        this.height = height;
        this.layers = layers;
        this.components = components;
        this.traces = traces;
        this.vias = vias;
        this.nets = nets;
        this.name = name;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    /**
     * 返回图层列表（可变，供编辑器修改）。
     *
     * @return 图层列表
     */
    public List<Layer> getLayers() {
        return layers;
    }

    /**
     * 返回元件实例列表（可变，供编辑器修改）。
     *
     * @return 元件实例列表
     */
    public List<ComponentInstance> getComponents() {
        return components;
    }

    /**
     * 返回走线列表（可变，供编辑器修改）。
     *
     * @return 走线列表
     */
    public List<Trace> getTraces() {
        return traces;
    }

    /**
     * 返回过孔列表（可变，供编辑器修改）。
     *
     * @return 过孔列表
     */
    public List<Via> getVias() {
        return vias;
    }

    /**
     * 返回网络列表（可变，供编辑器修改）。
     *
     * @return 网络列表
     */
    public List<Net> getNets() {
        return nets;
    }

    public String getName() {
        return name;
    }

    /**
     * 统计铜层数量。
     *
     * @return 铜层数量
     */
    public int copperLayerCount() {
        int count = 0;
        for (Layer l : layers) {
            if (l.getType() == LayerType.COPPER) {
                count++;
            }
        }
        return count;
    }

    /**
     * 生成默认 PCB 设计：{@code copperLayers} 个铜层 + 1 阻焊 + 1 丝印 + 1 钻孔。
     * <p>
     * 铜层 index 从 0（顶层铜）递增向下，最后一层铜为底层铜；
     * 铜层默认可见且可编辑，其余层默认可见且只读。
     * </p>
     *
     * @param width         板宽
     * @param height        板高
     * @param copperLayers  铜层数量（≥1）
     * @return 默认设计实例
     */
    public static PcbDesign createDefault(int width, int height, int copperLayers) {
        List<Layer> layers = new ArrayList<>();
        int idx = 0;
        for (int i = 0; i < copperLayers; i++) {
            String nm;
            if (copperLayers == 1) {
                nm = "Copper";
            } else if (i == 0) {
                nm = "Top Copper";
            } else if (i == copperLayers - 1) {
                nm = "Bottom Copper";
            } else {
                nm = "Inner Copper " + i;
            }
            layers.add(new Layer(idx++, LayerType.COPPER, true, true, nm));
        }
        layers.add(new Layer(idx++, LayerType.MASK, true, false, "Solder Mask"));
        layers.add(new Layer(idx++, LayerType.SILK, true, false, "Silkscreen"));
        layers.add(new Layer(idx++, LayerType.DRILL, true, false, "Drill"));
        return new PcbDesign(width, height, layers, new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>(), new ArrayList<>(), "Untitled");
    }

    /**
     * 完整序列化为 NBT，所有列表字段以 ListTag 嵌套 CompoundTag 存储。
     *
     * @return 包含全部字段的 CompoundTag
     */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("width", width);
        tag.putInt("height", height);
        tag.putString("name", name);

        ListTag layerList = new ListTag();
        for (Layer l : layers) {
            layerList.add(l.save());
        }
        tag.put("layers", layerList);

        ListTag compList = new ListTag();
        for (ComponentInstance c : components) {
            compList.add(c.save());
        }
        tag.put("components", compList);

        ListTag traceList = new ListTag();
        for (Trace t : traces) {
            traceList.add(t.save());
        }
        tag.put("traces", traceList);

        ListTag viaList = new ListTag();
        for (Via v : vias) {
            viaList.add(v.save());
        }
        tag.put("vias", viaList);

        ListTag netList = new ListTag();
        for (Net n : nets) {
            netList.add(n.save());
        }
        tag.put("nets", netList);
        return tag;
    }

    /**
     * 从 NBT 反序列化。
     *
     * @param tag 包含设计字段的 CompoundTag
     * @return 重建的 PCB 设计实例
     */
    public static PcbDesign load(CompoundTag tag) {
        List<Layer> layers = new ArrayList<>();
        ListTag layerList = tag.getListOrEmpty("layers");
        for (int i = 0; i < layerList.size(); i++) {
            layers.add(Layer.load(layerList.getCompoundOrEmpty(i)));
        }

        List<ComponentInstance> components = new ArrayList<>();
        ListTag compList = tag.getListOrEmpty("components");
        for (int i = 0; i < compList.size(); i++) {
            components.add(ComponentInstance.load(compList.getCompoundOrEmpty(i)));
        }

        List<Trace> traces = new ArrayList<>();
        ListTag traceList = tag.getListOrEmpty("traces");
        for (int i = 0; i < traceList.size(); i++) {
            traces.add(Trace.load(traceList.getCompoundOrEmpty(i)));
        }

        List<Via> vias = new ArrayList<>();
        ListTag viaList = tag.getListOrEmpty("vias");
        for (int i = 0; i < viaList.size(); i++) {
            vias.add(Via.load(viaList.getCompoundOrEmpty(i)));
        }

        List<Net> nets = new ArrayList<>();
        ListTag netList = tag.getListOrEmpty("nets");
        for (int i = 0; i < netList.size(); i++) {
            nets.add(Net.load(netList.getCompoundOrEmpty(i)));
        }

        return new PcbDesign(
                tag.getIntOr("width", 0),
                tag.getIntOr("height", 0),
                layers,
                components,
                traces,
                vias,
                nets,
                tag.getStringOr("name", "")
        );
    }

    /**
     * 深拷贝，供编辑器撤销栈使用。
     * <p>
     * 所有内部集合重新创建；其中不可变值对象（{@link Layer}、{@link Pad}、{@link GridPoint}）
     * 共享引用，可变集合（焊盘列表、路径、连接层集合、节点集合）逐项复制，确保修改副本不影响原件。
     * </p>
     *
     * @return 独立的深拷贝
     */
    public PcbDesign copy() {
        List<Layer> layersCp = new ArrayList<>(this.layers);

        List<ComponentInstance> compsCp = new ArrayList<>();
        for (ComponentInstance c : components) {
            compsCp.add(new ComponentInstance(
                    c.getComponentId(),
                    c.getDesignator(),
                    c.getOrigin(),
                    c.getRotation(),
                    new ArrayList<>(c.getPads())
            ));
        }

        List<Trace> tracesCp = new ArrayList<>();
        for (Trace t : traces) {
            tracesCp.add(new Trace(
                    t.getLayerIndex(),
                    new ArrayList<>(t.getPath()),
                    t.getWidth(),
                    t.getNet()
            ));
        }

        List<Via> viasCp = new ArrayList<>();
        for (Via v : vias) {
            viasCp.add(new Via(
                    v.getPos(),
                    v.getHoleSize(),
                    new TreeSet<>(v.getConnectedLayers())
            ));
        }

        List<Net> netsCp = new ArrayList<>();
        for (Net n : nets) {
            netsCp.add(new Net(
                    n.getName(),
                    n.getType(),
                    new LinkedHashSet<>(n.getNodes())
            ));
        }

        return new PcbDesign(width, height, layersCp, compsCp, tracesCp, viasCp, netsCp, name);
    }
}
