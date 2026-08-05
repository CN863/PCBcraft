package com.pcbcraft.sim;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 数字逻辑仿真器：处理 logic_gate / dff / mcu 支路。
 * <p>
 * 每个 tick 在 {@link CircuitSimulator#step()} 中先于 MNA 调用：
 * 读取各逻辑门输入引脚节点电压（&gt;2.5V=高，&lt;0.5V=低，中间保持上一次状态），
 * 按门类型真值表计算输出，写入 {@link #getDriveVoltages()}（0.0 或 5.0），
 * 供 {@link MnaSolver} 作为理想电压源注入对应输出节点。
 * </p>
 * <p>
 * MCU 支路本阶段为 stub：仅记录引脚拓扑，不执行固件（Phase 5 接入 Lua 沙盒）。
 * </p>
 * <p>真值表：AND（全高→高）、OR（任高→高）、NOT（反）、NAND、NOR、XOR；
 * DFF 在 CLK 上升沿锁存 D→Q。</p>
 */
public final class DigitalSimulator {

    /** 高电平输入阈值（V）。 */
    private static final double VIH = 2.5;
    /** 低电平输入阈值（V）。 */
    private static final double VIL = 0.5;
    /** 高电平输出驱动电压（V）。 */
    private static final double VHI = 5.0;
    /** 低电平输出驱动电压（V）。 */
    private static final double VLO = 0.0;

    /** 当前 tick 各数字输出节点应驱动的电压。 */
    private final Map<Integer, Double> driveVoltages = new LinkedHashMap<>();
    /** 节点 → 上一次输出是否高（用于输入中间电平时的“保持”）。 */
    private final Map<Integer, Boolean> prevOutputHigh = new HashMap<>();
    /** DFF 位号 → 当前 Q 状态。 */
    private final Map<String, Boolean> dffQ = new HashMap<>();
    /** DFF 位号 → 上一次 CLK 是否高（上升沿检测）。 */
    private final Map<String, Boolean> dffPrevClkHigh = new HashMap<>();
    /**
     * 外部 MCU 驱动电压：由 {@link com.pcbcraft.chip.ChipIoBridge} 每 tick 写入，
     * key 为 MCU 引脚节点索引，value 为该引脚驱动电压。仅含被芯片实际驱动的 GPIO 节点。
     */
    private Map<Integer, Double> externalMcDrives = new HashMap<>();

    /**
     * 执行一个 tick 的数字逻辑推演。
     *
     * @param netlist      网表
     * @param prevSolution 上一 tick 的 MNA 解（用于读输入电压；首 tick 可为 null）
     */
    public void step(Netlist netlist, MnaSolver.SimSolution prevSolution) {
        driveVoltages.clear();
        for (Netlist.NetlistBranch b : netlist.getBranches()) {
            if ("LOGIC".equals(b.type)) {
                stepLogic(b, prevSolution);
            } else if ("MCU".equals(b.type)) {
                stepMcu(b);
            }
        }
    }

    /**
     * MCU 支路：注入外部芯片沙盒提供的引脚驱动电压。
     * <p>仅对 {@link #externalMcDrives} 中已驱动的节点产生 driveVoltage；未驱动的节点保持浮空，
     * 避免与电源网络（VCC/GND）冲突。无芯片绑定时 externalMcDrives 为空，行为同 stub。</p>
     */
    private void stepMcu(Netlist.NetlistBranch b) {
        for (int node : b.nodes) {
            Double v = externalMcDrives.get(node);
            if (v != null) {
                driveVoltages.put(node, v);
            }
        }
    }

    /**
     * 设置外部 MCU 驱动电压表（由 {@link com.pcbcraft.chip.ChipIoBridge} 调用）。
     *
     * @param drives 节点索引 → 电压
     */
    public void setExternalMcDrives(Map<Integer, Double> drives) {
        this.externalMcDrives = (drives != null) ? drives : new HashMap<>();
    }

    /**
     * 返回外部 MCU 驱动电压表。
     *
     * @return 节点索引 → 电压
     */
    public Map<Integer, Double> getExternalMcDrives() {
        return externalMcDrives;
    }

    /**
     * 返回当前 tick 各数字输出节点应驱动的电压（含 MCU 外部驱动）。
     *
     * @return 节点索引 → 电压
     */
    public Map<Integer, Double> getDriveVoltages() {
        Map<Integer, Double> merged = new LinkedHashMap<>(driveVoltages);
        // 补充 externalMcDrives（step 中已合入 driveVoltages，此处作冗余兜底，确保 getDriveVoltages 在 step 未调用时仍含 MCU 驱动）
        merged.putAll(externalMcDrives);
        return merged;
    }

    // ===== 内部 =====

    private void stepLogic(Netlist.NetlistBranch b, MnaSolver.SimSolution prevSolution) {
        String gate = strParam(b.params.get("gate"));
        if (gate == null || gate.isEmpty()) {
            return;
        }
        if ("DFF".equals(gate)) {
            stepDff(b, prevSolution);
            return;
        }
        int[] nd = b.nodes;
        if (nd.length < 2) {
            return;
        }
        // 末位为输出，其余为输入
        int outNode = nd[nd.length - 1];
        boolean[] inputs = new boolean[nd.length - 1];
        for (int i = 0; i < inputs.length; i++) {
            inputs[i] = readInputHigh(nd[i], prevSolution);
        }
        boolean out = computeGate(gate, inputs, prevOutputHigh.getOrDefault(outNode, false));
        prevOutputHigh.put(outNode, out);
        driveVoltages.put(outNode, out ? VHI : VLO);
    }

    /**
     * DFF：CLK 上升沿锁存 D→Q，Q 与 Qbar 互补输出。
     * 节点顺序 [D, CLK, Q, Qbar]。
     */
    private void stepDff(Netlist.NetlistBranch b, MnaSolver.SimSolution prevSolution) {
        int[] nd = b.nodes;
        if (nd.length < 4) {
            return;
        }
        int dNode = nd[0];
        int clkNode = nd[1];
        int qNode = nd[2];
        int qbarNode = nd[3];
        boolean d = readInputHigh(dNode, prevSolution);
        boolean clkHigh = readInputHigh(clkNode, prevSolution);
        boolean prevClk = dffPrevClkHigh.getOrDefault(b.designator, false);
        boolean q = dffQ.getOrDefault(b.designator, false);
        // 上升沿：本拍高且上一拍低
        if (clkHigh && !prevClk) {
            q = d;
        }
        dffQ.put(b.designator, q);
        dffPrevClkHigh.put(b.designator, clkHigh);
        prevOutputHigh.put(qNode, q);
        driveVoltages.put(qNode, q ? VHI : VLO);
        if (qbarNode != qNode) {
            prevOutputHigh.put(qbarNode, !q);
            driveVoltages.put(qbarNode, !q ? VHI : VLO);
        }
    }

    /**
     * 读取输入引脚节点电压并判定高/低；中间电平保持上一次状态。
     */
    private boolean readInputHigh(int nodeIndex, MnaSolver.SimSolution prevSolution) {
        if (prevSolution == null) {
            return false;
        }
        double v = prevSolution.voltageAt(nodeIndex);
        if (v > VIH) {
            return true;
        }
        if (v < VIL) {
            return false;
        }
        // 中间电平：保持
        return prevOutputHigh.getOrDefault(nodeIndex, false);
    }

    /**
     * 按门类型真值表计算输出。
     *
     * @param gate    门类型（AND/OR/NOT/NAND/NOR/XOR）
     * @param in      输入电平数组
     * @param prevOut 上一拍输出（未知门保持用）
     * @return 输出电平
     */
    private boolean computeGate(String gate, boolean[] in, boolean prevOut) {
        switch (gate) {
            case "AND":
                for (boolean v : in) {
                    if (!v) {
                        return false;
                    }
                }
                return true;
            case "OR":
                for (boolean v : in) {
                    if (v) {
                        return true;
                    }
                }
                return false;
            case "NOT":
                return in.length >= 1 && !in[0];
            case "NAND":
                for (boolean v : in) {
                    if (!v) {
                        return true;
                    }
                }
                return false;
            case "NOR":
                for (boolean v : in) {
                    if (v) {
                        return false;
                    }
                }
                return true;
            case "XOR": {
                boolean r = false;
                for (boolean v : in) {
                    r ^= v;
                }
                return r;
            }
            default:
                return prevOut;
        }
    }

    private static String strParam(Object o) {
        return (o instanceof String) ? (String) o : null;
    }
}
