package com.pcbcraft.chip;

import com.pcbcraft.PCBCraft;
import net.minecraft.core.BlockPos;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent.ServerTickEvent;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 芯片服务端 tick 调度（Task 5.1/5.2）。
 * <p>
 * 维护 {@link BlockPos} → {@link ChipBlockEntity} 的全局注册表，由
 * {@link ChipBlockEntity#setRunning} / {@link ChipBlockEntity#setRemoved} 注册/注销。
 * 在 {@link ServerTickEvent.Post}（END 阶段）遍历所有活动芯片：先执行固件
 * {@link ChipRuntime#resumeTick()}，再由 {@link ChipIoBridge#sync} 联动绑定 PCB 仿真。
 * </p>
 * <p>与 {@link com.pcbcraft.sim.SimTickScheduler} 同为 END 阶段；二者执行顺序由监听器注册次序
 * 决定，最坏情况下 MCU 驱动存在 1 tick 延迟，可接受。</p>
 */
@EventBusSubscriber(modid = PCBCraft.MOD_ID, bus = EventBusSubscriber.Bus.FORGE)
public final class ChipTickScheduler {

    /** 已注册的活动芯片：BlockPos → ChipBlockEntity。 */
    private static final Map<BlockPos, ChipBlockEntity> active = new ConcurrentHashMap<>();

    private ChipTickScheduler() {
    }

    /** 注册一块活动芯片。 */
    public static void register(BlockPos pos, ChipBlockEntity be) {
        if (pos != null && be != null) {
            active.put(pos, be);
        }
    }

    /** 注销一块芯片。 */
    public static void unregister(BlockPos pos) {
        if (pos != null) {
            active.remove(pos);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        Iterator<Map.Entry<BlockPos, ChipBlockEntity>> it = active.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, ChipBlockEntity> e = it.next();
            ChipBlockEntity be = e.getValue();
            if (be == null || be.isRemoved() || !be.isRunning()) {
                it.remove();
                continue;
            }
            ChipRuntime rt = be.getRuntime();
            if (rt == null) {
                be.startRuntime();
                rt = be.getRuntime();
            }
            if (rt == null) {
                continue;
            }
            try {
                rt.resumeTick();
            } catch (Throwable t) {
                PCBCraft.LOGGER.warn("芯片@{} 固件运行异常: {}", e.getKey(), t.toString());
                rt.stop();
            }
            try {
                ChipIoBridge.sync(be);
            } catch (Throwable t) {
                PCBCraft.LOGGER.debug("芯片@{} I/O 同步异常: {}", e.getKey(), t.toString());
            }
        }
    }
}
