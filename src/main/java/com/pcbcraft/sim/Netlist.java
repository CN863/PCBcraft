package com.pcbcraft.sim;

import com.pcbcraft.PCBCraft;
import com.pcbcraft.data.ComponentInstance;
import com.pcbcraft.data.GridPoint;
import com.pcbcraft.data.Net;
import com.pcbcraft.data.PcbDesign;
import com.pcbcraft.data.Trace;
import com.pcbcraft.library.ComponentDef;
import com.pcbcraft.library.ComponentLibrary;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 网表：将 {@link PcbDesign} 编译为电路拓扑（节点 + 支路）。
 * <p>
 * 一个 {@link NetlistNode} 表示一个电气等电位点，对应一组共享同一网络的
 * {@link GridPoint}（焊盘 pinPos + 走线 path 点 + 过孔 pos）。{@link NetlistBranch}
 * 表示元件引脚对（电阻/电容/电感/二极管/电源/开关/运放/数字逻辑/芯片等）。
 * </p>
 * <p>
 * 构建流程（{@link #build}）：① 用并查集按网络名合并电气等电位点；② 标记接地节点
 * （GROUND 类型网络或 ground 元件引脚所在节点）；③ 遍历元件按模型类型生成支路。
 * </p>
 */
public final class Netlist {

    /** 电气节点列表，index 字段与列表下标一致。 */
    private final List<NetlistNode> nodes;
    /** 支路列表。 */
    private final List<NetlistBranch> branches;
    /** 坐标 → 节点索引。 */
    private final Map<GridPoint, Integer> pointToNodeIndex;
    /** 接地节点索引；无地时回退为 0（MNA 参考节点）。 */
    private final int groundNodeIndex;
    /** 网络名 → 该网络走线总长度（方块数），供 {@link SignalPropagator} 计算延迟。 */
    private final Map<String, Integer> netTraceLengths;

    private Netlist(List<NetlistNode> nodes, List<NetlistBranch> branches,
                    Map<GridPoint, Integer> pointToNodeIndex, int groundNodeIndex,
                    Map<String, Integer> netTraceLengths) {
        this.nodes = nodes;
        this.branches = branches;
        this.pointToNodeIndex = pointToNodeIndex;
        this.groundNodeIndex = groundNodeIndex;
        this.netTraceLengths = netTraceLengths;
    }

    public List<NetlistNode> getNodes() {
        return nodes;
    }

    public List<NetlistBranch> getBranches() {
        return branches;
    }

    public Map<GridPoint, Integer> getPointToNodeIndex() {
        return pointToNodeIndex;
    }

    public int getGroundNodeIndex() {
        return groundNodeIndex;
    }

    public Map<String, Integer> getNetTraceLengths() {
        return netTraceLengths;
    }

    /**
     * 返回坐标所属节点索引，未连接返回 -1。
     *
     * @param point 板内坐标
     * @return 节点索引或 -1
     */
    public int nodeOf(GridPoint point) {
        return pointToNodeIndex.getOrDefault(point, -1);
    }

    /**
     * 节点总数。
     *
     * @return 节点数
     */
    public int nodeCount() {
        return nodes.size();
    }

    /**
     * 返回指定类型的所有支路。
     *
     * @param type 支路类型（R/C/L/D/LED/V/SW/OPAMP/LOGIC/MCU/GND）
     * @return 支路列表
     */
    public List<NetlistBranch> branchesOf(String type) {
        List<NetlistBranch> result = new ArrayList<>();
        for (NetlistBranch b : branches) {
            if (b.type.equals(type)) {
                result.add(b);
            }
        }
        return result;
    }

    /**
     * 将 PCB 设计编译为网表。
     * <p>
     * 步骤：① 收集每个网络的 GridPoint（net.nodes + 走线 path 点），用并查集按网络名合并；
     * ② 每个 Union-Find 连通分量 → 一个 {@link NetlistNode}；③ 接地节点识别；
     * ④ 遍历元件按 {@link ComponentDef.ComponentModel#getType()} 生成支路。
     * 库未加载（lib 为 null）时仅构建节点拓扑，跳过支路生成。
     * </p>
     *
     * @param design PCB 设计
     * @param lib    元件库（可为 null）
     * @return 网表
     */
    public static Netlist build(PcbDesign design, ComponentLibrary lib) {
        List<NetlistNode> nodes = new ArrayList<>();
        List<NetlistBranch> branches = new ArrayList<>();
        Map<GridPoint, Integer> pointToNodeIndex = new HashMap<>();
        Map<String, Integer> netTraceLengths = new LinkedHashMap<>();

        // ===== 走线长度聚合（按网络名）=====
        for (Trace t : design.getTraces()) {
            String n = t.getNet();
            if (n == null || n.isEmpty()) {
                continue;
            }
            netTraceLengths.merge(n, t.length(), Integer::sum);
        }

        // ===== Step1：收集点 → 网络名映射，并查集按网络名合并 =====
        // pointId：为每个出现过的 GridPoint 分配一个整数 id 供并查集使用
        Map<GridPoint, Integer> pointId = new HashMap<>();
        List<GridPoint> allPoints = new ArrayList<>();
        // netName -> 该网络下所有 pointId
        Map<String, List<Integer>> netToPointIds = new LinkedHashMap<>();
        // 网络名 -> 电气类型
        Map<String, Net.ElectricalType> netType = new HashMap<>();

        // 来自 design.nets
        for (Net net : design.getNets()) {
            netType.put(net.getName(), net.getType());
            List<Integer> ids = netToPointIds.computeIfAbsent(net.getName(), k -> new ArrayList<>());
            for (GridPoint p : net.getNodes()) {
                Integer id = pointId.get(p);
                if (id == null) {
                    id = allPoints.size();
                    allPoints.add(p);
                    pointId.put(p, id);
                }
                ids.add(id);
            }
        }
        // 来自 traces（path 点归入其 net；过孔无 net 字段，依赖 net.nodes 覆盖，孤立过孔跳过）
        for (Trace t : design.getTraces()) {
            String n = t.getNet();
            if (n == null || n.isEmpty()) {
                continue;
            }
            netType.putIfAbsent(n, Net.ElectricalType.SIGNAL);
            List<Integer> ids = netToPointIds.computeIfAbsent(n, k -> new ArrayList<>());
            for (GridPoint p : t.getPath()) {
                Integer id = pointId.get(p);
                if (id == null) {
                    id = allPoints.size();
                    allPoints.add(p);
                    pointId.put(p, id);
                }
                ids.add(id);
            }
        }

        // 并查集：同一网络内的所有点合并为一个连通分量
        int[] parent = new int[allPoints.size()];
        for (int i = 0; i < parent.length; i++) {
            parent[i] = i;
        }
        for (List<Integer> ids : netToPointIds.values()) {
            for (int i = 1; i < ids.size(); i++) {
                union(parent, ids.get(0), ids.get(i));
            }
        }

        // point -> 网络名（任取一个；共享点会通过并查集连通）
        Map<GridPoint, String> pointToNetName = new HashMap<>();
        for (Map.Entry<String, List<Integer>> e : netToPointIds.entrySet()) {
            for (Integer id : e.getValue()) {
                pointToNetName.putIfAbsent(allPoints.get(id), e.getKey());
            }
        }

        // ===== Step2：连通分量 → 节点 =====
        Map<Integer, Set<GridPoint>> components = new LinkedHashMap<>();
        for (int i = 0; i < allPoints.size(); i++) {
            int root = find(parent, i);
            components.computeIfAbsent(root, k -> new LinkedHashSet<>()).add(allPoints.get(i));
        }
        for (Set<GridPoint> pts : components.values()) {
            int idx = nodes.size();
            String netName = null;
            for (GridPoint p : pts) {
                String nn = pointToNetName.get(p);
                if (nn != null) {
                    netName = nn;
                    break;
                }
            }
            Net.ElectricalType type = (netName != null)
                    ? netType.getOrDefault(netName, Net.ElectricalType.SIGNAL)
                    : Net.ElectricalType.SIGNAL;
            nodes.add(new NetlistNode(idx, netName, pts, type));
            for (GridPoint p : pts) {
                pointToNodeIndex.put(p, idx);
            }
        }

        // ===== Step3：接地节点识别 =====
        int groundNodeIndex = -1;
        // 优先：GROUND 类型网络
        for (NetlistNode node : nodes) {
            if (node.type == Net.ElectricalType.GROUND) {
                groundNodeIndex = node.index;
                break;
            }
        }
        // 其次：ground 元件引脚所在节点（浮动地焊盘则创建孤立节点）
        if (groundNodeIndex < 0) {
            for (ComponentInstance inst : design.getComponents()) {
                ComponentDef def = (lib != null) ? lib.get(inst.getComponentId()) : null;
                if (def != null && "ground".equals(def.getModel().getType())) {
                    groundNodeIndex = resolvePinNode(inst, 1, pointToNodeIndex, nodes);
                    break;
                }
            }
        }
        // 兜底：无显式地时以节点 0 为 MNA 参考节点
        if (groundNodeIndex < 0 && !nodes.isEmpty()) {
            groundNodeIndex = 0;
        }

        // ===== Step4：支路生成 =====
        for (ComponentInstance inst : design.getComponents()) {
            ComponentDef def = (lib != null) ? lib.get(inst.getComponentId()) : null;
            if (def == null) {
                PCBCraft.LOGGER.warn("元件 {} 的定义 {} 未找到，跳过支路生成",
                        inst.getDesignator(), inst.getComponentId());
                continue;
            }
            String mtype = def.getModel().getType();
            Map<String, Object> mparams = def.getModel().getParams();
            switch (mtype) {
                case "ground":
                    // 单引脚地元件：仅用于接地节点识别，不产生支路
                    break;
                case "resistor":
                    branches.add(new NetlistBranch(inst.getDesignator(), "R",
                            pinNodesOf(inst, def, pointToNodeIndex, nodes), copyParams(mparams)));
                    break;
                case "capacitor":
                    branches.add(new NetlistBranch(inst.getDesignator(), "C",
                            pinNodesOf(inst, def, pointToNodeIndex, nodes), copyParams(mparams)));
                    break;
                case "inductor":
                    branches.add(new NetlistBranch(inst.getDesignator(), "L",
                            pinNodesOf(inst, def, pointToNodeIndex, nodes), copyParams(mparams)));
                    break;
                case "diode":
                    branches.add(new NetlistBranch(inst.getDesignator(), "D",
                            pinNodesOf(inst, def, pointToNodeIndex, nodes), copyParams(mparams)));
                    break;
                case "led":
                    branches.add(new NetlistBranch(inst.getDesignator(), "LED",
                            pinNodesOf(inst, def, pointToNodeIndex, nodes), copyParams(mparams)));
                    break;
                case "vsource":
                    // pin1=+ , pin2=-
                    branches.add(new NetlistBranch(inst.getDesignator(), "V",
                            pinNodesOf(inst, def, pointToNodeIndex, nodes), copyParams(mparams)));
                    break;
                case "switch":
                    branches.add(new NetlistBranch(inst.getDesignator(), "SW",
                            pinNodesOf(inst, def, pointToNodeIndex, nodes), copyParams(mparams)));
                    break;
                case "opamp":
                    // pin1=IN+, pin2=IN-, pin3=OUT
                    branches.add(new NetlistBranch(inst.getDesignator(), "OPAMP",
                            pinNodesOf(inst, def, pointToNodeIndex, nodes), copyParams(mparams)));
                    break;
                case "logic_gate":
                    // pin 末位为输出，其余为输入
                    branches.add(new NetlistBranch(inst.getDesignator(), "LOGIC",
                            pinNodesOf(inst, def, pointToNodeIndex, nodes), copyParams(mparams)));
                    break;
                case "dff": {
                    // 标记为 LOGIC 支路，gate=DFF，节点顺序 [D, CLK, Q, Qbar]
                    Map<String, Object> p = copyParams(mparams);
                    p.putIfAbsent("gate", "DFF");
                    branches.add(new NetlistBranch(inst.getDesignator(), "LOGIC",
                            pinNodesOf(inst, def, pointToNodeIndex, nodes), p));
                    break;
                }
                case "mcu":
                    branches.add(new NetlistBranch(inst.getDesignator(), "MCU",
                            pinNodesOf(inst, def, pointToNodeIndex, nodes), copyParams(mparams)));
                    break;
                case "connector":
                    // 连接器仅起连通作用，不产生支路
                    break;
                default:
                    PCBCraft.LOGGER.warn("元件 {} 的模型类型 {} 未识别，跳过", inst.getDesignator(), mtype);
                    break;
            }
        }

        return new Netlist(nodes, branches, pointToNodeIndex, groundNodeIndex, netTraceLengths);
    }

    // ===== 内部辅助 =====

    /**
     * 解析某引脚焊盘对应的节点索引；若该焊盘未连接任何网络则创建一个孤立节点。
     *
     * @param inst             元件实例
     * @param pinNumber        引脚编号
     * @param pointToNodeIndex 坐标→节点索引（会被追加）
     * @param nodes            节点列表（会被追加）
     * @return 节点索引
     */
    private static int resolvePinNode(ComponentInstance inst, int pinNumber,
                                      Map<GridPoint, Integer> pointToNodeIndex,
                                      List<NetlistNode> nodes) {
        GridPoint p = inst.pinPos(pinNumber);
        if (p == null) {
            // 引脚不存在：创建匿名浮动节点，保证支路两端合法
            int idx = nodes.size();
            nodes.add(new NetlistNode(idx, null, new LinkedHashSet<>(), Net.ElectricalType.SIGNAL));
            return idx;
        }
        Integer existing = pointToNodeIndex.get(p);
        if (existing != null) {
            return existing;
        }
        int idx = nodes.size();
        Set<GridPoint> pts = new LinkedHashSet<>();
        pts.add(p);
        nodes.add(new NetlistNode(idx, null, pts, Net.ElectricalType.SIGNAL));
        pointToNodeIndex.put(p, idx);
        return idx;
    }

    /**
     * 返回元件全部引脚（按封装焊盘顺序）对应的节点索引数组。
     */
    private static int[] pinNodesOf(ComponentInstance inst, ComponentDef def,
                                    Map<GridPoint, Integer> pointToNodeIndex,
                                    List<NetlistNode> nodes) {
        List<ComponentDef.PadDef> pads = def.getFootprint().getPads();
        int[] arr = new int[pads.size()];
        for (int i = 0; i < pads.size(); i++) {
            arr[i] = resolvePinNode(inst, pads.get(i).getPin(), pointToNodeIndex, nodes);
        }
        return arr;
    }

    private static Map<String, Object> copyParams(Map<String, Object> src) {
        return new LinkedHashMap<>(src);
    }

    // 并查集
    private static int find(int[] parent, int x) {
        while (parent[x] != x) {
            parent[x] = parent[parent[x]];
            x = parent[x];
        }
        return x;
    }

    private static void union(int[] parent, int a, int b) {
        int ra = find(parent, a);
        int rb = find(parent, b);
        if (ra != rb) {
            parent[ra] = rb;
        }
    }

    // ===== 内部数据类 =====

    /**
     * 电气节点：一个等电位点，含若干板内坐标与所属网络名。
     */
    public static final class NetlistNode {
        /** 节点索引（与 Netlist.nodes 下标一致）。 */
        public final int index;
        /** 所属网络名；孤立节点为 null。 */
        public final String net;
        /** 该节点覆盖的板内坐标集合。 */
        public final Set<GridPoint> points;
        /** 电气类型。 */
        public final Net.ElectricalType type;

        public NetlistNode(int index, String net, Set<GridPoint> points, Net.ElectricalType type) {
            this.index = index;
            this.net = net;
            this.points = points;
            this.type = type;
        }
    }

    /**
     * 电路支路：元件引脚对/电源/地/逻辑等。
     */
    public static final class NetlistBranch {
        /** 位号（如 R1、U3）。 */
        public final String designator;
        /** 支路类型：R/C/L/D/LED/V/SW/OPAMP/LOGIC/MCU/GND。 */
        public final String type;
        /** 两端/多端节点索引；vsource 为 [+, -]，logic_gate 末位为输出，dff 为 [D,CLK,Q,Qbar]。 */
        public final int[] nodes;
        /** 模型参数（resistance/capacitance/voltage/gate/closed/gain/...）。 */
        public final Map<String, Object> params;

        public NetlistBranch(String designator, String type, int[] nodes, Map<String, Object> params) {
            this.designator = designator;
            this.type = type;
            this.nodes = nodes;
            this.params = params;
        }
    }
}
