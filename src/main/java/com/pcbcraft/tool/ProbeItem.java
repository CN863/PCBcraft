package com.pcbcraft.tool;

import com.pcbcraft.block.PcbBlock;
import com.pcbcraft.block.PcbBlockEntity;
import com.pcbcraft.data.GridPoint;
import com.pcbcraft.data.Net;
import com.pcbcraft.data.PcbDesign;
import com.pcbcraft.net.ModNet;
import com.pcbcraft.net.SimStatePacket;
import com.pcbcraft.render.ProbeClientData;
import com.pcbcraft.sim.CircuitSimulator;
import com.pcbcraft.sim.MnaSolver;
import com.pcbcraft.sim.Netlist;
import com.pcbcraft.sim.SimController;
import com.pcbcraft.sim.SimTickScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;
import net.minecraft.world.item.context.UseOnContext;

import java.util.List;

/**
 * 探针物品（Task 6.1）。
 * <p>
 * 手持探针右键 PCB 任一方块，向玩家发送该格的瞬时电气读数（电压/电流/网络名），
 * 以 actionbar 形式显示；同时将读数坐标缓存到客户端 {@link ProbeClientData} 供浮窗渲染，
 * 并向该玩家推送一次 {@link SimStatePacket} 以刷新客户端仿真状态缓存。
 * </p>
 * <p>
 * <b>读数来源</b>：电压取 {@link SimController#voltageAt}；电流取该节点关联支路的最大绝对电流；
 * 网络名遍历 design.nets 匹配包含该坐标的网络。客户端无仿真解，故读数在服务端计算后通过
 * actionbar 同步，浮窗电压则由客户端缓存的 SimStatePacket 反查。
 * </p>
 * <p>
 * 波形采样（持续右键周期采样）本阶段留 TODO：当前仅瞬时读数 + 浮窗最近读数。
 * </p>
 */
public class ProbeItem extends Item {

    public ProbeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos clicked = ctx.getClickedPos();
        if (!(level.getBlockState(clicked).getBlock() instanceof PcbBlock)) {
            return InteractionResult.PASS;
        }
        BlockPos master = PcbLocator.findMaster(level, clicked);
        if (master == null) {
            return InteractionResult.PASS;
        }
        int gx = PcbLocator.gridX(clicked, master);
        int gy = PcbLocator.gridY(clicked, master);
        int layerY = PcbLocator.layerY(clicked, master);

        if (level.isClientSide()) {
            // 客户端：记录读数坐标供浮窗渲染（波形 TODO）
            ProbeClientData.set(master, GridPoint.of(gx, gy), layerY);
            return InteractionResult.SUCCESS;
        }

        // 服务端：计算瞬时读数
        Player player = ctx.getPlayer();
        GridPoint point = GridPoint.of(gx, gy);
        double voltage = SimController.voltageAt(master, point);
        double current = currentAt(master, point);
        String netName = netNameAt(level, master, point);

        if (player != null) {
            String msg = String.format("电压 %.2fV  电流 %.3fA  网络 %s  (层 %d)",
                    voltage, current, netName, layerY);
            player.sendSystemMessage(Component.literal(msg));
            // 推送一次仿真状态到客户端，刷新浮窗/可视化缓存
            SimStatePacket pkt = SimStatePacket.build(master);
            if (pkt != null && player instanceof ServerPlayer sp) {
                ModNet.CHANNEL.send(pkt, PacketDistributor.PLAYER.with(sp));
            }
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * 取该节点关联支路的最大绝对电流（简化：遍历网表支路，取任一端节点等于目标节点的支路电流最大值）。
     *
     * @param master 主方块坐标
     * @param point  板内坐标
     * @return 电流（A）；未连接或无解返回 0
     */
    private static double currentAt(BlockPos master, GridPoint point) {
        CircuitSimulator sim = SimTickScheduler.simulator(master);
        if (sim == null) {
            return 0.0;
        }
        MnaSolver.SimSolution sol = sim.lastSolution();
        Netlist netlist = sim.getNetlist();
        if (sol == null || netlist == null) {
            return 0.0;
        }
        int node = netlist.nodeOf(point);
        if (node < 0) {
            return 0.0;
        }
        List<Netlist.NetlistBranch> branches = netlist.getBranches();
        double max = 0.0;
        for (int bi = 0; bi < branches.size(); bi++) {
            int[] nodes = branches.get(bi).nodes;
            for (int n : nodes) {
                if (n == node) {
                    double c = Math.abs(sol.currentOf(bi));
                    if (c > max) {
                        max = c;
                    }
                    break;
                }
            }
        }
        return max;
    }

    /**
     * 取该坐标所属网络名。遍历 design.nets，返回首个包含该坐标的网络名。
     *
     * @param level  世界
     * @param master 主方块坐标
     * @param point  板内坐标
     * @return 网络名；未找到返回 "无"
     */
    private static String netNameAt(Level level, BlockPos master, GridPoint point) {
        if (level.getBlockEntity(master) instanceof PcbBlockEntity pcb) {
            PcbDesign design = pcb.getDesign();
            if (design != null) {
                for (Net net : design.getNets()) {
                    if (net.getNodes().contains(point)) {
                        return net.getName();
                    }
                }
            }
        }
        return "无";
    }
}
