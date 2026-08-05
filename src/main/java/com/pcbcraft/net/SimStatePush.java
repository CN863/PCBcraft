package com.pcbcraft.net;

import com.pcbcraft.PCBCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.TickEvent.ServerTickEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端仿真状态周期推送（Task 6.2）。
 * <p>
 * 维护 {@code BlockPos → Set&lt;ServerPlayer&gt;} 订阅表（由 {@link VisTogglePacket} 在服务端添加/移除），
 * 在 {@link ServerTickEvent.Post}（原 TickEvent.ServerTickEvent Phase.END）每 {@link #PUSH_INTERVAL} tick 遍历订阅，
 * 为每个 master 构建 {@link SimStatePacket} 并发送给订阅玩家。
 * </p>
 * <p>玩家退出时自动清理其所有订阅，避免空指针与内存泄漏。</p>
 * <p>NeoForge 1.21.4：发送使用 {@link PacketDistributor#sendToPlayer} 静态方法。</p>
 */
@EventBusSubscriber(modid = PCBCraft.MOD_ID, bus = EventBusSubscriber.Bus.FORGE)
public final class SimStatePush {

    /** 推送周期（tick）。 */
    private static final int PUSH_INTERVAL = 5;

    /** master 坐标 → 订阅该 PCB 状态的玩家集合。 */
    private static final Map<BlockPos, Set<ServerPlayer>> subscriptions = new ConcurrentHashMap<>();
    /** tick 计数器。 */
    private static long tickCounter = 0L;

    private SimStatePush() {
    }

    /**
     * 添加订阅：指定玩家开始接收该 PCB 的状态推送。
     *
     * @param masterPos 主方块坐标
     * @param player    服务端玩家
     */
    public static void subscribe(BlockPos masterPos, ServerPlayer player) {
        subscriptions.computeIfAbsent(masterPos.immutable(), k -> ConcurrentHashMap.newKeySet()).add(player);
    }

    /**
     * 移除订阅：指定玩家不再接收该 PCB 的状态推送。
     *
     * @param masterPos 主方块坐标
     * @param player    服务端玩家
     */
    public static void unsubscribe(BlockPos masterPos, ServerPlayer player) {
        Set<ServerPlayer> set = subscriptions.get(masterPos);
        if (set != null) {
            set.remove(player);
            if (set.isEmpty()) {
                subscriptions.remove(masterPos);
            }
        }
    }

    /**
     * 服务端 tick：每 {@link #PUSH_INTERVAL} tick 推送一次所有订阅 PCB 的状态。
     * <p>NeoForge 1.21.4 将 {@code TickEvent.ServerTickEvent} 拆为 {@link ServerTickEvent.Pre} /
     * {@link ServerTickEvent.Post}，此处监听 Post（等价旧 Phase.END）。</p>
     *
     * @param event 服务端 tick 事件（Post 阶段）
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        tickCounter++;
        if (tickCounter < PUSH_INTERVAL) {
            return;
        }
        tickCounter = 0L;
        if (subscriptions.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<BlockPos, Set<ServerPlayer>>> it = subscriptions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Set<ServerPlayer>> e = it.next();
            BlockPos master = e.getKey();
            Set<ServerPlayer> players = e.getValue();
            if (players.isEmpty()) {
                it.remove();
                continue;
            }
            SimStatePacket pkt = SimStatePacket.build(master);
            if (pkt == null) {
                // 该 PCB 未注册仿真（未上电/已移除），跳过本次推送
                continue;
            }
            for (ServerPlayer player : players) {
                if (player.hasDisconnected()) {
                    continue;
                }
                ModNet.CHANNEL.send(pkt, PacketDistributor.PLAYER.with(player));
            }
        }
    }

    /**
     * 玩家退出时清理其所有订阅。
     *
     * @param event 玩家退出事件
     */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Iterator<Map.Entry<BlockPos, Set<ServerPlayer>>> it = subscriptions.entrySet().iterator();
        while (it.hasNext()) {
            Set<ServerPlayer> set = it.next().getValue();
            set.remove(player);
            if (set.isEmpty()) {
                it.remove();
            }
        }
    }
}
