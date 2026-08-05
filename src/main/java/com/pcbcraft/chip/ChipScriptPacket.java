package com.pcbcraft.chip;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.network.CustomPayloadEvent;

/**
 * 客户端 → 服务端：上传芯片固件源码并控制运行状态（Task 5.1）。
 * <p>载荷：芯片方块坐标 + 固件源码 + 目标运行标志。服务端写回 {@link ChipBlockEntity}
 * 并按需启停/重启运行时。</p>
 * <p>Forge 65.1.0：通过 {@link ChipNet#CHANNEL}（SimpleChannel）注册，
 * 由 {@link #encode} / {@link #decode} 完成序列化，{@link #handle} 在主线程消费。</p>
 */
public final class ChipScriptPacket {

    private final BlockPos pos;
    private final String script;
    private final boolean running;

    public ChipScriptPacket(BlockPos pos, String script, boolean running) {
        this.pos = pos;
        this.script = script != null ? script : "";
        this.running = running;
    }

    public static void encode(ChipScriptPacket msg, RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeUtf(msg.script);
        buf.writeBoolean(msg.running);
    }

    public static ChipScriptPacket decode(RegistryFriendlyByteBuf buf) {
        return new ChipScriptPacket(buf.readBlockPos(), buf.readUtf(), buf.readBoolean());
    }

    public static void handle(ChipScriptPacket msg, CustomPayloadEvent.Context ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) ctx.getSender();
            if (player == null) {
                return;
            }
            ServerLevel level = (ServerLevel) player.level();
            BlockEntity be = level.getBlockEntity(msg.pos);
            if (!(be instanceof ChipBlockEntity chip)) {
                return;
            }
            chip.setScript(msg.script);
            if (msg.running) {
                if (!chip.isRunning()) {
                    chip.setRunning(true);
                } else {
                    // 运行中改写固件：重启运行时以加载新脚本
                    chip.restartRuntime();
                }
            } else {
                if (chip.isRunning()) {
                    chip.setRunning(false);
                }
            }
            chip.markUpdated();
        });
        ctx.setPacketHandled(true);
    }
}
