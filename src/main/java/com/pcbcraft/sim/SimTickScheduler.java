package com.pcbcraft.sim;

import com.pcbcraft.PCBCraft;
import com.pcbcraft.PCBConfig;
import com.pcbcraft.data.PcbDesign;
import com.pcbcraft.library.ComponentLibrary;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent.ServerTickEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端 tick 调度（Phase 4.5）。
 * <p>
 * 维护一个 {@link BlockPos} → {@link CircuitSimulator} / {@link FaultModel} 的全局注册表，
 * 在 {@link ServerTickEvent.Post}（Phase END）按 {@link PCBConfig#simTickInterval()} 周期
 * 遍历所有已注册且未跳闸的电路，执行 {@link CircuitSimulator#step()} + {@link FaultModel#analyze}。
 * </p>
 * <p>
 * <b>注册表方案</b>：不全局扫描区块/BlockEntity（1.20.1 无公开枚举 API），而是由
 * {@code PcbBlockEntity} 在 {@code setPowered(true)}/加载时调用 {@link #register}，
 * 在 {@code setPowered(false)}/移除时调用 {@link #unregister}。注册表使用
 * {@link ConcurrentHashMap}，调度与注册/注销均在服务端线程，但并发容器更稳健。
 * </p>
 * <p>
 * 超预算（{@link CircuitSimulator#isBudgetExceeded()}）的电路仍执行故障分析，仅记录日志，
 * 不跳过——避免预算紧张时故障状态长期不更新。
 * </p>
 */
@EventBusSubscriber(modid = PCBCraft.MOD_ID, bus = EventBusSubscriber.Bus.FORGE)
public final class SimTickScheduler {

    /** 已注册的电路仿真器：BlockPos → CircuitSimulator。 */
    private static final Map<BlockPos, CircuitSimulator> sims = new ConcurrentHashMap<>();
    /** 已注册的故障模型：BlockPos → FaultModel。 */
    private static final Map<BlockPos, FaultModel> faults = new ConcurrentHashMap<>();
    /** 已注册 PCB 所在的服务端维度：BlockPos → ServerLevel，用于发送短路冒烟粒子。 */
    private static final Map<BlockPos, ServerLevel> levels = new ConcurrentHashMap<>();
    /** tick 计数器，达到 {@link PCBConfig#simTickInterval()} 时推进一次。 */
    private static long tickCounter = 0L;
    /** 服务端绝对 tick 计数（每 tick 自增，不重置），用于短路冒烟 20-tick 节流。 */
    private static long serverTickCount = 0L;
    /** 短路冒烟告警的发送间隔（tick）。 */
    private static final long SMOKE_INTERVAL_TICKS = 20L;

    private SimTickScheduler() {
    }

    /**
     * 注册一块 PCB 进入仿真调度。
     * <p>若该位置已注册，重建仿真器（采用最新 design），保留既有 FaultModel。</p>
     *
     * @param pos    主方块坐标
     * @param design PCB 设计
     * @param level  所在服务端维度（用于短路冒烟粒子告警，可为 {@code null}）
     * @return 该位置对应的 FaultModel（已存在则保留，否则新建）
     */
    public static FaultModel register(BlockPos pos, PcbDesign design, ServerLevel level) {
        if (pos == null) {
            return null;
        }
        BlockPos key = pos.immutable();
        CircuitSimulator sim = CircuitSimulator.create(design);
        sims.put(key, sim);
        if (level != null) {
            levels.put(key, level);
        }
        FaultModel fault = faults.get(key);
        if (fault == null) {
            fault = new FaultModel();
            faults.put(key, fault);
        }
        return fault;
    }

    /**
     * 注销一块 PCB，停止其仿真。
     *
     * @param pos 主方块坐标
     */
    public static void unregister(BlockPos pos) {
        if (pos == null) {
            return;
        }
        sims.remove(pos);
        faults.remove(pos);
        levels.remove(pos);
    }

    /**
     * 替换指定位置的故障模型（用于从 NBT 恢复）。
     *
     * @param pos   主方块坐标
     * @param fault 故障模型
     */
    public static void setFault(BlockPos pos, FaultModel fault) {
        if (pos == null) {
            return;
        }
        faults.put(pos, fault);
    }

    /**
     * 更新设计（编辑器修改 design 后重建仿真器，保留故障状态）。
     * <p>仅当该位置已注册时生效。</p>
     *
     * @param pos    主方块坐标
     * @param design 新的 PCB 设计
     */
    public static void updateDesign(BlockPos pos, PcbDesign design) {
        if (pos == null || !sims.containsKey(pos)) {
            return;
        }
        sims.put(pos, CircuitSimulator.create(design));
    }

    /**
     * 返回指定位置的仿真器。
     *
     * @param pos 主方块坐标
     * @return 仿真器；未注册返回 {@code null}
     */
    public static CircuitSimulator simulator(BlockPos pos) {
        return sims.get(pos);
    }

    /**
     * 返回指定位置的故障模型。
     *
     * @param pos 主方块坐标
     * @return 故障模型；未注册返回 {@code null}
     */
    public static FaultModel fault(BlockPos pos) {
        return faults.get(pos);
    }

    /**
     * 服务端 tick：每 {@link PCBConfig#simTickInterval()} 个 tick 推进一次所有已注册电路，
     * 并对短路/跳闸的 PCB 持续发送冒烟粒子告警（每 {@link #SMOKE_INTERVAL_TICKS} tick 一次）。
     *
     * @param event 服务端 tick 事件
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        // 绝对 tick 计数（每 tick 自增，不重置），用于冒烟节流
        serverTickCount++;
        tickCounter++;
        int interval = PCBConfig.simTickInterval();
        if (interval <= 0) {
            interval = 1;
        }
        if (tickCounter < interval) {
            return;
        }
        tickCounter = 0L;

        ComponentLibrary lib = ComponentLibrary.get();
        for (Map.Entry<BlockPos, CircuitSimulator> entry : sims.entrySet()) {
            BlockPos pos = entry.getKey();
            CircuitSimulator sim = entry.getValue();
            FaultModel fault = faults.get(pos);
            if (sim == null || fault == null) {
                continue;
            }
            // 短路冒烟告警：即使跳闸断电也持续冒烟（每 20 tick 一次，避免刷屏）
            if (fault.isShortCircuited()) {
                sendSmokeAlarm(pos, fault);
            }
            if (fault.isShutDown()) {
                // 跳闸后不再推进仿真（断电）
                continue;
            }
            sim.step();
            PcbDesign design = sim.getDesign();
            fault.analyze(sim, design, lib);
            if (sim.isBudgetExceeded()) {
                PCBCraft.LOGGER.debug("PCB @{} 仿真超出预算 {}μs", pos, PCBConfig.simBudgetMicros());
            }
        }
    }

    /**
     * 对短路/跳闸的 PCB 发送 LARGE_SMOKE 粒子告警。
     * <p>使用 {@link FaultModel#getLastSmokeTick()} 节流，距上次发送不足
     * {@link #SMOKE_INTERVAL_TICKS} tick 则跳过，避免每 tick 刷屏。</p>
     *
     * @param pos   主方块坐标（板原点 basePos）
     * @param fault 故障模型
     */
    private static void sendSmokeAlarm(BlockPos pos, FaultModel fault) {
        if (pos == null || fault == null) {
            return;
        }
        if (serverTickCount - fault.getLastSmokeTick() < SMOKE_INTERVAL_TICKS) {
            return;
        }
        fault.setLastSmokeTick(serverTickCount);
        ServerLevel level = levels.get(pos);
        if (level == null) {
            return;
        }
        // 在 master 方块上方 1.5 格处释放大烟雾，8 个粒子、散布 0.5、速度 0.02
        // 1.21.4 sendParticles 新增 boolean overrideLimiter 参数
        level.sendParticles(ParticleTypes.LARGE_SMOKE, false, false,
                pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5,
                8, 0.5, 0.5, 0.5, 0.02);
    }
}
