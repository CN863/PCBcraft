package com.pcbcraft.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;

/**
 * 客户端 → 服务端：请求/取消对某 PCB 仿真状态的周期推送（Task 6.2）。
 * <p>载荷：主方块坐标 + 是否启用订阅。服务端在 {@link SimStatePush} 中维护
 * {@code BlockPos → Set&lt;ServerPlayer&gt;} 订阅表，每 5 tick 向订阅玩家发 {@link SimStatePacket}。</p>
 * <p>Forge 65.1.0：通过 {@link ModNet#CHANNEL}（SimpleChannel）注册，
 * 由 {@link #encode} / {@link #decode} 完成序列化，{@link #handle} 在主线程消费。</p>
 */
public final class VisTogglePacket {

    private final BlockPos masterPos;
    private final boolean enable;

    public VisTogglePacket(BlockPos masterPos, boolean enable) {
        this.masterPos = masterPos;
        this.enable = enable;
    }

    public static void encode(VisTogglePacket msg, RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(msg.masterPos);
        buf.writeBoolean(msg.enable);
    }

    public static VisTogglePacket decode(RegistryFriendlyByteBuf buf) {
        return new VisTogglePacket(buf.readBlockPos(), buf.readBoolean());
    }

    public static void handle(VisTogglePacket msg, CustomPayloadEvent.Context ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) ctx.getSender();
            if (player == null) {
                return;
            }
            if (msg.enable) {
                SimStatePush.subscribe(msg.masterPos, player);
                player.sendSystemMessage(Component.literal("信号可视化：开"));
            } else {
                SimStatePush.unsubscribe(msg.masterPos, player);
                player.sendSystemMessage(Component.literal("信号可视化：关"));
            }
        });
        ctx.setPacketHandled(true);
    }
}
