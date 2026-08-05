package com.pcbcraft.chip;

import com.pcbcraft.sim.CircuitSimulator;
import com.pcbcraft.sim.MnaSolver;
import com.pcbcraft.sim.Netlist;
import com.pcbcraft.sim.SimTickScheduler;
import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.Map;

/**
 * 芯片引脚 ↔ PCB netlist 双向联动桥（Task 5.3）。
 * <p>
 * 每服务端 tick 由 {@link ChipTickScheduler} 在 {@link ChipRuntime#resumeTick()} 之后调用
 * {@link #sync(ChipBlockEntity)}：将芯片固件写出的引脚驱动电压注入绑定 PCB 的
 * {@link com.pcbcraft.sim.DigitalSimulator} 外部 MCU 驱动表，并从最近一次 MNA 解读回引脚输入电压。
 * </p>
 * <p>
 * MCU 支路节点顺序为封装焊盘顺序 [VCC, GND, D0, D1, D2, D3, A0, A1]（见
 * {@code data/pcbcraft/components/mcu.json}）。为避免与电源网络冲突，<b>仅向 GPIO 节点
 * （索引 2..7）注入驱动</b>，VCC/GND 节点不驱动；但全部 8 个节点电压均回读为引脚输入，
 * 供固件感知电源与地电平。
 * </p>
 * <p>绑定 PCB 取其网表中第一条 MCU 支路（简化）。未绑定或无 MCU 支路时为空操作。</p>
 */
public final class ChipIoBridge {

    /** MCU 支路中首个 GPIO 在 nodes 数组中的索引（VCC/GND 之后）。 */
    private static final int GPIO_START = 2;

    private ChipIoBridge() {
    }

    /**
     * 同步芯片引脚与绑定 PCB 的仿真状态。
     *
     * @param chip 芯片方块实体
     */
    public static void sync(ChipBlockEntity chip) {
        if (chip == null) {
            return;
        }
        BlockPos master = chip.getBoundMaster();
        if (master == null) {
            return;
        }
        CircuitSimulator sim = SimTickScheduler.simulator(master);
        if (sim == null) {
            return;
        }
        Netlist nl = sim.getNetlist();
        if (nl == null) {
            return;
        }
        Netlist.NetlistBranch mcu = findFirstMcu(nl);
        if (mcu == null) {
            return;
        }
        int[] nodes = mcu.nodes;
        int n = Math.min(ChipBlockEntity.PIN_COUNT, nodes.length);

        // 写：芯片 GPIO(2..7) 驱动电压 → DigitalSimulator.externalMcDrives
        Map<Integer, Double> drives = new HashMap<>();
        for (int i = GPIO_START; i < n; i++) {
            drives.put(nodes[i], chip.getPinOutput(i));
        }
        sim.setExternalMcDrives(drives);

        // 读：从最近一次 MNA 解回填全部 8 路引脚输入
        MnaSolver.SimSolution sol = sim.lastSolution();
        if (sol != null) {
            for (int i = 0; i < n; i++) {
                chip.setPinInput(i, sol.voltageAt(nodes[i]));
            }
        }
    }

    /** 返回网表中第一条 MCU 支路；无则 {@code null}。 */
    private static Netlist.NetlistBranch findFirstMcu(Netlist nl) {
        for (Netlist.NetlistBranch b : nl.getBranches()) {
            if ("MCU".equals(b.type)) {
                return b;
            }
        }
        return null;
    }
}
