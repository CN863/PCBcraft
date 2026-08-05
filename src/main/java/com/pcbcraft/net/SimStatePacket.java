package com.pcbcraft.net;

import com.pcbcraft.data.PcbDesign;
import com.pcbcraft.render.ClientSimState;
import com.pcbcraft.sim.CircuitSimulator;
import com.pcbcraft.sim.MnaSolver;
import com.pcbcraft.sim.SimTickScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;

/**
 * 服务端 → 客户端：推送某 PCB 的仿真解快照（Task 6.2 / 6.4）。
 * <p>
 * 紧凑载荷：主方块坐标 + 节点电压毫伏整数数组（按 {@link com.pcbcraft.sim.Netlist} 节点索引顺序）
 * + 铜层数 + 板宽 + 板高 + 短路标志。客户端按 {@code nodeOf(GridPoint)} 反查节点索引取电压染色。
 * </p>
 * <p>
 * 节点电压以毫伏整数传输，避免浮点序列化开销与精度漂移；客户端还原为 {@code volts = mv / 1000.0}。
 * 节点索引在服务端与客户端由同一份 {@code design + ComponentLibrary} 经 {@link com.pcbcraft.sim.Netlist#build}
 * 确定性构建，两端一致。
 * </p>
 * <p>Forge 65.1.0：通过 {@link ModNet#CHANNEL}（SimpleChannel）注册，
 * 由 {@link #encode} / {@link #decode} 完成序列化，{@link #handle} 在主线程消费。</p>
 */
public final class SimStatePacket {

    private final BlockPos masterPos;
    private final int[] nodeVoltageMilliVolts;
    private final int layerCount;
    private final int width;
    private final int height;
    private final boolean shortCircuit;

    public SimStatePacket(BlockPos masterPos, int[] nodeVoltageMilliVolts,
                          int layerCount, int width, int height, boolean shortCircuit) {
        this.masterPos = masterPos;
        this.nodeVoltageMilliVolts = nodeVoltageMilliVolts;
        this.layerCount = layerCount;
        this.width = width;
        this.height = height;
        this.shortCircuit = shortCircuit;
    }

    public BlockPos getMasterPos() {
        return masterPos;
    }

    public int[] getNodeVoltageMilliVolts() {
        return nodeVoltageMilliVolts;
    }

    public int getLayerCount() {
        return layerCount;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public boolean isShortCircuit() {
        return shortCircuit;
    }

    /**
     * 返回指定节点索引的电压（伏特），越界或无数据返回 0。
     *
     * @param nodeIndex 节点索引
     * @return 电压（V）
     */
    public double voltageAt(int nodeIndex) {
        if (nodeIndex < 0 || nodeIndex >= nodeVoltageMilliVolts.length) {
            return 0.0;
        }
        return nodeVoltageMilliVolts[nodeIndex] / 1000.0;
    }

    public static void encode(SimStatePacket msg, RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(msg.masterPos);
        buf.writeVarIntArray(msg.nodeVoltageMilliVolts);
        buf.writeInt(msg.layerCount);
        buf.writeInt(msg.width);
        buf.writeInt(msg.height);
        buf.writeBoolean(msg.shortCircuit);
    }

    public static SimStatePacket decode(RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int[] mv = buf.readVarIntArray();
        int layerCount = buf.readInt();
        int width = buf.readInt();
        int height = buf.readInt();
        boolean sc = buf.readBoolean();
        return new SimStatePacket(pos, mv, layerCount, width, height, sc);
    }

    public static void handle(SimStatePacket msg, CustomPayloadEvent.Context ctx) {
        ctx.enqueueWork(() -> ClientSimState.handle(msg));
        ctx.setPacketHandled(true);
    }

    /**
     * 由服务端仿真注册表构建指定 PCB 的状态快照。
     *
     * @param masterPos 主方块坐标
     * @return 状态包；该位置未注册仿真器或无设计返回 {@code null}
     */
    public static SimStatePacket build(BlockPos masterPos) {
        CircuitSimulator sim = SimTickScheduler.simulator(masterPos);
        if (sim == null) {
            return null;
        }
        PcbDesign design = sim.getDesign();
        if (design == null) {
            return null;
        }
        int n = sim.getNetlist().nodeCount();
        int[] mv = new int[n];
        MnaSolver.SimSolution sol = sim.lastSolution();
        boolean sc = false;
        if (sol != null) {
            sc = sol.isShortCircuited();
            for (int i = 0; i < n; i++) {
                mv[i] = (int) Math.round(sol.voltageAt(i) * 1000.0);
            }
        }
        int layerCount = design.copperLayerCount();
        return new SimStatePacket(masterPos, mv, layerCount, design.getWidth(), design.getHeight(), sc);
    }
}
