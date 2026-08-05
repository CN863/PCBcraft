package com.pcbcraft.sim;

import com.pcbcraft.PCBCraft;
import com.pcbcraft.PCBConfig;
import com.pcbcraft.data.GridPoint;
import com.pcbcraft.data.PcbDesign;
import com.pcbcraft.library.ComponentLibrary;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 电路仿真编排器，供 Phase 4.5 tick 调度调用。
 * <p>
 * 持有 {@link PcbDesign}、编译后的 {@link Netlist}、{@link DigitalSimulator} 与上一次
 * {@link MnaSolver.SimSolution}，每个 tick 顺序执行：① 数字逻辑推演 → ② MNA 求解 →
 * ③ 刷新节点电压表与热状态 → ④ 故障检测（短路/过载，Phase 4.4 细化）。
 * </p>
 */
public final class CircuitSimulator {

    private final PcbDesign design;
    private final Netlist netlist;
    private final DigitalSimulator digital;
    /** 上一次 MNA 解。 */
    private MnaSolver.SimSolution lastSolution;
    /** 网络名 → 节点电压。 */
    private final Map<String, Double> nodeVoltageByName = new LinkedHashMap<>();
    /** 位号 → 温度（简化：以功耗 P=I²R 近似，Phase 4.4 细化热模型）。 */
    private final Map<String, Double> componentHeat = new LinkedHashMap<>();
    /** 是否已停机（短路跳闸后由 {@link FaultModel} 置位，{@link #step()} 直接返回）。 */
    private boolean shutDown;

    private CircuitSimulator(PcbDesign design, Netlist netlist, DigitalSimulator digital) {
        this.design = design;
        this.netlist = netlist;
        this.digital = digital;
    }

    /**
     * 创建仿真器：用 {@link ComponentLibrary#get()} 构建网表。
     * <p>库未加载时记录警告并构建仅含拓扑的网表（无元件支路）。</p>
     *
     * @param design PCB 设计
     * @return 仿真器实例
     */
    public static CircuitSimulator create(PcbDesign design) {
        ComponentLibrary lib = ComponentLibrary.get();
        if (lib == null) {
            PCBCraft.LOGGER.warn("元件库尚未加载，网表将不含元件支路");
        }
        Netlist netlist = Netlist.build(design, lib);
        return new CircuitSimulator(design, netlist, new DigitalSimulator());
    }

    /**
     * 推进一个 tick：数字推演 → MNA 求解 → 刷新电压/热状态 → 故障检测 stub。
     */
    public void step() {
        // 跳闸断电后不再求解（保持上次解或零解）
        if (shutDown) {
            return;
        }
        // ① 数字逻辑推演（读上一拍解，写驱动电压）
        digital.step(netlist, lastSolution);
        // ② MNA 求解（带预算限时）
        lastSolution = MnaSolver.solveBounded(netlist, digital, PCBConfig.simBudgetMicros());
        // ③ 刷新网络名 → 电压
        nodeVoltageByName.clear();
        if (lastSolution != null) {
            for (Netlist.NetlistNode node : netlist.getNodes()) {
                if (node.net != null && !node.net.isEmpty()) {
                    nodeVoltageByName.put(node.net, lastSolution.voltageAt(node.index));
                }
            }
        }
        // ④ 热状态 + 故障检测 stub（Phase 4.4 细化）
        updateHeat();
        if (isShortCircuited()) {
            PCBCraft.LOGGER.warn("PCB [{}] 检测到短路，策略: {}",
                    design.getName(), PCBConfig.shortCircuitPolicy());
        }
        if (isBudgetExceeded()) {
            PCBCraft.LOGGER.debug("PCB [{}] 仿真超出预算 {}μs", design.getName(), PCBConfig.simBudgetMicros());
        }
    }

    /**
     * 简化热状态：按支路功耗 P=I²R 累计到对应位号。
     */
    private void updateHeat() {
        componentHeat.clear();
        if (lastSolution == null) {
            return;
        }
        List<Netlist.NetlistBranch> branches = netlist.getBranches();
        for (int i = 0; i < branches.size(); i++) {
            Netlist.NetlistBranch b = branches.get(i);
            double current = lastSolution.currentOf(i);
            double r = branchResistanceForHeat(b);
            if (!Double.isFinite(r) || r <= 0.0) {
                continue;
            }
            double power = current * current * r;
            if (power > 0.0) {
                componentHeat.merge(b.designator, power, Double::sum);
            }
        }
    }

    /**
     * 返回支路用于热计算的等效电阻（仅耗能元件）。
     */
    private double branchResistanceForHeat(Netlist.NetlistBranch b) {
        switch (b.type) {
            case "R":
                return toDouble(b.params.get("resistance"), 1000.0);
            case "C":
                return 1e6;
            case "L":
                return 1e-3;
            case "SW":
                return toBool(b.params.get("closed")) ? 1e-9 : 0.0;
            case "D":
            case "LED":
                return 1.0;
            default:
                return 0.0;
        }
    }

    /**
     * 返回指定网络名的节点电压。
     *
     * @param netName 网络名
     * @return 电压（未找到返回 0）
     */
    public double voltageAtNode(String netName) {
        return nodeVoltageByName.getOrDefault(netName, 0.0);
    }

    /**
     * 返回指定坐标处的节点电压。
     *
     * @param point 板内坐标
     * @return 电压（未连接或无解返回 0）
     */
    public double voltageAt(GridPoint point) {
        int idx = netlist.nodeOf(point);
        if (idx < 0 || lastSolution == null) {
            return 0.0;
        }
        return lastSolution.voltageAt(idx);
    }

    /**
     * 是否检测到短路。
     *
     * @return 短路返回 true
     */
    public boolean isShortCircuited() {
        return lastSolution != null && lastSolution.isShortCircuited();
    }

    /**
     * 是否超出仿真预算。
     *
     * @return 超预算返回 true
     */
    public boolean isBudgetExceeded() {
        return lastSolution != null && lastSolution.isBudgetExceeded();
    }

    /**
     * 是否已停机（短路跳闸）。
     *
     * @return 停机返回 true
     */
    public boolean isShutDown() {
        return shutDown;
    }

    /**
     * 设置停机状态（由 {@link FaultModel} 在短路跳闸时置 true）。
     *
     * @param shutDown 是否停机
     */
    public void setShutDown(boolean shutDown) {
        this.shutDown = shutDown;
    }

    /**
     * 返回上一次仿真解。
     *
     * @return 仿真解（未 step 过为 null）
     */
    public MnaSolver.SimSolution lastSolution() {
        return lastSolution;
    }

    public Netlist getNetlist() {
        return netlist;
    }

    /**
     * 返回数字仿真器（供 {@link com.pcbcraft.chip.ChipIoBridge} 注入 MCU 外部驱动）。
     *
     * @return 数字仿真器
     */
    public DigitalSimulator getDigital() {
        return digital;
    }

    /**
     * 转发设置外部 MCU 驱动电压表至 {@link DigitalSimulator}。
     *
     * @param drives 节点索引 → 电压
     */
    public void setExternalMcDrives(Map<Integer, Double> drives) {
        digital.setExternalMcDrives(drives);
    }

    public PcbDesign getDesign() {
        return design;
    }

    public Map<String, Double> nodeVoltageByName() {
        return Collections.unmodifiableMap(nodeVoltageByName);
    }

    public Map<String, Double> componentHeat() {
        return Collections.unmodifiableMap(componentHeat);
    }

    // ===== 辅助 =====

    private static double toDouble(Object o, double def) {
        if (o instanceof Number) {
            return ((Number) o).doubleValue();
        }
        return def;
    }

    private static boolean toBool(Object o) {
        if (o instanceof Boolean) {
            return (Boolean) o;
        }
        if (o instanceof Number) {
            return ((Number) o).doubleValue() != 0.0;
        }
        return false;
    }
}
