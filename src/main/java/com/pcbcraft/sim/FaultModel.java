package com.pcbcraft.sim;

import com.pcbcraft.PCBCraft;
import com.pcbcraft.PCBConfig;
import com.pcbcraft.data.ComponentInstance;
import com.pcbcraft.data.GridPoint;
import com.pcbcraft.data.PcbDesign;
import com.pcbcraft.library.ComponentDef;
import com.pcbcraft.library.ComponentLibrary;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 故障模型（Phase 4.4）。
 * <p>
 * 每个 tick 由 {@link SimTickScheduler} 在推进 {@link CircuitSimulator} 后调用
 * {@link #analyze(CircuitSimulator, PcbDesign, ComponentLibrary)}，完成三类检测：
 * </p>
 * <ul>
 *   <li><b>短路保护</b>：{@code sim.isShortCircuited()} 为真时按 {@link PCBConfig#shortCircuitPolicy()}
 *       处理——{@code "trip"} 跳闸断电（置 {@link #tripped} 并 {@link CircuitSimulator#setShutDown(boolean)}），
 *       {@code "limit"} 仅记录短路标志、继续求解。</li>
 *   <li><b>过载发热</b>：遍历 design.components，对电阻按 P=V²/R、LED 按支路电流超额定计算
 *       累积热量；每 tick 先散热（{@code *=0.98}）再与相邻元件（origin 曼哈顿距离≤2）按 0.1 系数扩散，
 *       最后累积超额功耗×dt（dt=1 tick=0.05s）。{@code heat>100} 标记烧毁。</li>
 *   <li><b>开路检测</b>：{@link MnaSolver.SimSolution#isOpenCircuit()} 为真时置 {@link #openCircuit}
 *       （无电源/网表奇异/冲突源）。</li>
 * </ul>
 * <p>
 * 状态通过 {@link #save()} / {@link #load(CompoundTag)} 持久化，由 {@code PcbBlockEntity} 在
 * 存盘时写入 NBT、加载时恢复，保证跳闸/烧毁状态跨存档保留。
 * </p>
 */
public final class FaultModel {

    /** 1 game tick 对应的物理时间（秒），用于热量累积。 */
    private static final double DT = 0.05;
    /** 每 tick 散热系数（保留 98%）。 */
    private static final double COOLING = 0.98;
    /** 相邻元件热扩散系数。 */
    private static final double DIFFUSION = 0.1;
    /** 烧毁温度阈值。 */
    private static final double BURN_THRESHOLD = 100.0;
    /** 电阻默认额定功耗（W），JSON 未提供 ratedPower 时使用。 */
    private static final double DEFAULT_RATED_POWER = 0.25;
    /** LED 默认额定电流（A），JSON 未提供 ratedCurrent 时使用。 */
    private static final double DEFAULT_RATED_CURRENT = 0.02;
    /** LED 默认正向压降（V）。 */
    private static final double DEFAULT_LED_VF = 2.0;
    /** 电阻默认阻值（Ω），JSON 未提供 resistance 时使用。 */
    private static final double DEFAULT_RESISTANCE = 1000.0;

    /** 当前 tick 是否检测到短路。 */
    private boolean shortCircuited;
    /** 是否已跳闸（跳闸后断电，不再推进仿真，需手动复位，本阶段未提供复位）。 */
    private boolean tripped;
    /** 是否处于开路状态（无电源/网表奇异）。 */
    private boolean openCircuit;
    /** 位号 → 温度（摄氏，热量代理值）。 */
    private final Map<String, Double> componentHeat = new LinkedHashMap<>();
    /** 已烧毁元件位号集合。 */
    private final Set<String> burnedComponents = new LinkedHashSet<>();
    /** 最近一次发送短路冒烟告警的游戏 tick，用于 20-tick 节流（避免每 tick 刷屏）。 */
    private long lastSmokeTick;

    /**
     * 执行一次故障分析。
     *
     * @param sim    电路仿真器（已 step 过）
     * @param design PCB 设计
     * @param lib    元件库（可为 null）
     */
    public void analyze(CircuitSimulator sim, PcbDesign design, ComponentLibrary lib) {
        if (sim == null || design == null) {
            return;
        }
        MnaSolver.SimSolution sol = sim.lastSolution();

        // ===== 短路保护 =====
        if (sim.isShortCircuited() && !tripped) {
            shortCircuited = true;
            if ("trip".equals(PCBConfig.shortCircuitPolicy())) {
                tripped = true;
                sim.setShutDown(true);
                PCBCraft.LOGGER.warn("PCB [{}] 短路跳闸（trip），仿真断电", design.getName());
            } else {
                // limit 策略：不跳闸，仅记录短路标志，sim 继续求解（已由 MnaSolver 限流检测）
                PCBCraft.LOGGER.warn("PCB [{}] 短路限流（limit）", design.getName());
            }
        }

        // ===== 开路检测 =====
        openCircuit = (sol != null && sol.isOpenCircuit());

        // ===== 过载发热 =====
        // 1. 散热
        for (Map.Entry<String, Double> e : componentHeat.entrySet()) {
            e.setValue(e.getValue() * COOLING);
        }
        // 2. 相邻元件热扩散
        diffuseHeat(design);
        // 3. 超额功耗累积
        if (lib != null && sol != null) {
            accumulateOverloadHeat(sim, design, lib, sol);
        }
        // 4. 烧毁判定
        for (Map.Entry<String, Double> e : componentHeat.entrySet()) {
            if (e.getValue() > BURN_THRESHOLD && burnedComponents.add(e.getKey())) {
                PCBCraft.LOGGER.warn("PCB [{}] 元件 {} 因过热烧毁（heat={})",
                        design.getName(), e.getKey(), String.format("%.2f", e.getValue()));
            }
        }
    }

    /**
     * 相邻元件（origin 曼哈顿距离≤2）按 {@link #DIFFUSION} 系数互相扩散热量。
     * <p>使用 delta 累加器保证顺序无关。</p>
     *
     * @param design PCB 设计
     */
    private void diffuseHeat(PcbDesign design) {
        List<ComponentInstance> comps = design.getComponents();
        Map<String, Double> delta = new HashMap<>();
        for (int i = 0; i < comps.size(); i++) {
            ComponentInstance a = comps.get(i);
            if (a.getOrigin() == null) {
                continue;
            }
            for (int j = i + 1; j < comps.size(); j++) {
                ComponentInstance b = comps.get(j);
                if (b.getOrigin() == null) {
                    continue;
                }
                int dist = Math.abs(a.getOrigin().x() - b.getOrigin().x())
                        + Math.abs(a.getOrigin().y() - b.getOrigin().y());
                if (dist > 2) {
                    continue;
                }
                double ha = componentHeat.getOrDefault(a.getDesignator(), 0.0);
                double hb = componentHeat.getOrDefault(b.getDesignator(), 0.0);
                double d = DIFFUSION * (hb - ha);
                delta.merge(a.getDesignator(), d, Double::sum);
                delta.merge(b.getDesignator(), -d, Double::sum);
            }
        }
        for (Map.Entry<String, Double> e : delta.entrySet()) {
            componentHeat.merge(e.getKey(), e.getValue(), Double::sum);
        }
    }

    /**
     * 遍历元件按模型类型累积超额热量。
     * <ul>
     *   <li>resistor：P=V²/R，V 为两端节点电压差；超额 = max(0, P - ratedPower)。</li>
     *   <li>led：I=支路电流，超额 = max(0, (I - ratedCurrent) × forwardVoltage)。</li>
     *   <li>其它元件本阶段不发热。</li>
     * </ul>
     * <p>已烧毁元件跳过（不再发热）。累积量 = 超额功率 × {@link #DT}。</p>
     *
     * @param sim     仿真器
     * @param design  设计
     * @param lib     元件库
     * @param sol     当前仿真解
     */
    private void accumulateOverloadHeat(CircuitSimulator sim, PcbDesign design,
                                        ComponentLibrary lib, MnaSolver.SimSolution sol) {
        List<Netlist.NetlistBranch> branches = sim.getNetlist().getBranches();
        // 位号 → 支路索引（用于读取 LED 支路电流）
        Map<String, Integer> branchIndex = new HashMap<>();
        for (int i = 0; i < branches.size(); i++) {
            branchIndex.put(branches.get(i).designator, i);
        }

        for (ComponentInstance inst : design.getComponents()) {
            String designator = inst.getDesignator();
            if (burnedComponents.contains(designator)) {
                continue; // 已烧毁：视为开路，不再发热
            }
            ComponentDef def = lib.get(inst.getComponentId());
            if (def == null) {
                continue;
            }
            String mtype = def.getModel().getType();
            Map<String, Object> params = def.getModel().getParams();
            double excessPower = 0.0;
            switch (mtype) {
                case "resistor": {
                    double r = toDouble(params.get("resistance"), DEFAULT_RESISTANCE);
                    GridPoint p1 = inst.pinPos(1);
                    GridPoint p2 = inst.pinPos(2);
                    double v1 = (p1 != null) ? sim.voltageAt(p1) : 0.0;
                    double v2 = (p2 != null) ? sim.voltageAt(p2) : 0.0;
                    double v = Math.abs(v1 - v2);
                    double power = (r > 0.0) ? (v * v) / r : 0.0;
                    double rated = toDouble(params.get("ratedPower"), DEFAULT_RATED_POWER);
                    if (power > rated) {
                        excessPower = power - rated;
                    }
                    break;
                }
                case "led": {
                    Integer bi = branchIndex.get(designator);
                    double current = (bi != null) ? Math.abs(sol.currentOf(bi)) : 0.0;
                    double rated = toDouble(params.get("ratedCurrent"), DEFAULT_RATED_CURRENT);
                    if (current > rated) {
                        double vf = toDouble(params.get("forwardVoltage"), DEFAULT_LED_VF);
                        excessPower = (current - rated) * vf;
                    }
                    break;
                }
                default:
                    break;
            }
            if (excessPower > 0.0) {
                componentHeat.merge(designator, excessPower * DT, Double::sum);
            }
        }
    }

    /**
     * 是否已停机（跳闸或电源切断）。
     *
     * @return tripped 为 true 时返回 true
     */
    public boolean isShutDown() {
        return tripped;
    }

    public boolean isShortCircuited() {
        return shortCircuited;
    }

    public boolean isTripped() {
        return tripped;
    }

    public boolean isOpenCircuit() {
        return openCircuit;
    }

    /**
     * 返回指定位号元件的当前温度。
     *
     * @param designator 位号
     * @return 温度（未记录返回 0）
     */
    public double heatOf(String designator) {
        return componentHeat.getOrDefault(designator, 0.0);
    }

    /**
     * 指定位号元件是否已烧毁。
     *
     * @param designator 位号
     * @return 烧毁返回 true
     */
    public boolean isBurned(String designator) {
        return burnedComponents.contains(designator);
    }

    /**
     * 返回已烧毁元件位号集合（可变视图，调用方不应修改）。
     *
     * @return 烧毁位号集合
     */
    public Set<String> getBurnedComponents() {
        return burnedComponents;
    }

    /**
     * 返回位号 → 温度映射（可变视图，调用方不应修改）。
     *
     * @return 温度映射
     */
    public Map<String, Double> getComponentHeat() {
        return componentHeat;
    }

    /**
     * 返回最近一次短路冒烟告警的游戏 tick。
     *
     * @return 最近冒烟 tick；未发送过返回 0
     */
    public long getLastSmokeTick() {
        return lastSmokeTick;
    }

    /**
     * 记录最近一次发送冒烟告警的游戏 tick。
     *
     * @param tick 游戏 tick
     */
    public void setLastSmokeTick(long tick) {
        this.lastSmokeTick = tick;
    }

    /**
     * 序列化为 NBT：tripped / shortCircuited / openCircuit / heat 列表 / burned 列表。
     *
     * @return 包含故障状态的 CompoundTag
     */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("tripped", tripped);
        tag.putBoolean("shortCircuited", shortCircuited);
        tag.putBoolean("openCircuit", openCircuit);

        ListTag heatList = new ListTag();
        for (Map.Entry<String, Double> e : componentHeat.entrySet()) {
            CompoundTag h = new CompoundTag();
            h.putString("d", e.getKey());
            h.putDouble("v", e.getValue());
            heatList.add(h);
        }
        tag.put("heat", heatList);

        ListTag burnedList = new ListTag();
        for (String d : burnedComponents) {
            burnedList.add(StringTag.valueOf(d));
        }
        tag.put("burned", burnedList);
        return tag;
    }

    /**
     * 从 NBT 反序列化。
     *
     * @param tag 包含故障状态的 CompoundTag
     * @return 重建的 FaultModel
     */
    public static FaultModel load(CompoundTag tag) {
        FaultModel f = new FaultModel();
        f.tripped = tag.getBooleanOr("tripped", false);
        f.shortCircuited = tag.getBooleanOr("shortCircuited", false);
        f.openCircuit = tag.getBooleanOr("openCircuit", false);

        ListTag heatList = tag.getListOrEmpty("heat");
        for (int i = 0; i < heatList.size(); i++) {
            CompoundTag h = heatList.getCompoundOrEmpty(i);
            f.componentHeat.put(h.getStringOr("d", ""), h.getDoubleOr("v", 0.0));
        }

        ListTag burnedList = tag.getListOrEmpty("burned");
        for (int i = 0; i < burnedList.size(); i++) {
            f.burnedComponents.add(burnedList.getStringOr(i, ""));
        }
        return f;
    }

    private static double toDouble(Object o, double def) {
        if (o instanceof Number) {
            return ((Number) o).doubleValue();
        }
        return def;
    }
}
