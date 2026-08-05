package com.pcbcraft.render;

import com.pcbcraft.data.GridPoint;
import com.pcbcraft.data.PcbDesign;
import com.pcbcraft.library.ComponentLibrary;
import com.pcbcraft.net.SimStatePacket;
import com.pcbcraft.sim.Netlist;
import net.minecraft.core.BlockPos;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端仿真状态缓存（Task 6.2 / 6.4）。
 * <p>
 * 缓存两类客户端数据：
 * </p>
 * <ul>
 *   <li>{@link #states}：最近一次接收的 {@link SimStatePacket}（按 master 坐标索引）；</li>
 *   <li>{@link #netlists}：由客户端 design + {@link ComponentLibrary} 构建的 {@link Netlist} 缓存，
 *       用于将板内坐标映射到节点索引以反查电压。缓存按 design 引用一致性失效——
 *       当服务端同步新设计后引用改变，自动重建网表，保证节点索引与服务端一致。</li>
 * </ul>
 * <p>本类为纯数据容器（不引用客户端专有 API），可被网络包 handler 与渲染层共同引用。</p>
 */
public final class ClientSimState {

    private static final Map<BlockPos, SimStatePacket> states = new ConcurrentHashMap<>();
    private static final Map<BlockPos, NetlistEntry> netlists = new ConcurrentHashMap<>();

    private ClientSimState() {
    }

    /**
     * 处理收到的仿真状态包：缓存到 states。
     *
     * @param pkt 仿真状态包
     */
    public static void handle(SimStatePacket pkt) {
        if (pkt == null) {
            return;
        }
        states.put(pkt.getMasterPos().immutable(), pkt);
    }

    /**
     * 返回指定 PCB 最近一次缓存的状态包。
     *
     * @param master 主方块坐标
     * @return 状态包；无缓存返回 {@code null}
     */
    public static SimStatePacket get(BlockPos master) {
        return states.get(master);
    }

    /**
     * 返回指定 PCB 指定坐标的节点电压（伏特）。
     * <p>由缓存的 {@link SimStatePacket} + 客户端构建的 {@link Netlist} 反查。</p>
     *
     * @param master 主方块坐标
     * @param point  板内坐标
     * @param design 该 PCB 当前设计（用于构建/校验网表缓存）
     * @return 电压（V）；无数据返回 0
     */
    public static double voltageAt(BlockPos master, GridPoint point, PcbDesign design) {
        SimStatePacket pkt = states.get(master);
        if (pkt == null || design == null || point == null) {
            return 0.0;
        }
        Netlist netlist = netlistFor(master, design);
        if (netlist == null) {
            return 0.0;
        }
        int node = netlist.nodeOf(point);
        return pkt.voltageAt(node);
    }

    /**
     * 取/建指定 PCB 的客户端网表缓存。design 引用变化时重建。
     *
     * @param master 主方块坐标
     * @param design 当前设计
     * @return 网表；元件库未加载返回 {@code null}
     */
    private static Netlist netlistFor(BlockPos master, PcbDesign design) {
        NetlistEntry entry = netlists.get(master);
        if (entry != null && entry.design() == design) {
            return entry.netlist();
        }
        ComponentLibrary lib = ComponentLibrary.get();
        if (lib == null) {
            return null;
        }
        Netlist netlist = Netlist.build(design, lib);
        netlists.put(master.immutable(), new NetlistEntry(design, netlist));
        return netlist;
    }

    /** 网表缓存条目：记录构建时使用的 design 引用以便失效判定。 */
    private record NetlistEntry(PcbDesign design, Netlist netlist) {
    }
}
