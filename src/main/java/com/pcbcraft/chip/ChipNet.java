package com.pcbcraft.chip;

import com.pcbcraft.PCBCraft;
import net.minecraft.resources.Identifier;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.SimpleChannel;

/**
 * 芯片网络通道（Forge 65.1.0 / MC 26.2 适配版）。
 * <p>使用 Forge 传统的 {@link SimpleChannel} 注册客户端 → 服务端的 {@link ChipScriptPacket} 载荷。
 * 通道与 {@link com.pcbcraft.net.ModNet} 独立，互不干扰。</p>
 * <p>发送端使用 {@link SimpleChannel#sendToServer}。</p>
 */
public final class ChipNet {

    /** 通道协议版本，客户端与服务端必须一致。 */
    private static final String PROTOCOL_VERSION = "1";

    /** 芯片通道实例。 */
    public static final SimpleChannel CHANNEL = ChannelBuilder
            .named(Identifier.fromNamespaceAndPath(PCBCraft.MOD_ID, "chip"))
            .acceptedVersions(net.minecraftforge.network.Channel.VersionTest.exact(Integer.parseInt(PROTOCOL_VERSION)))
            .networkProtocolVersion(Integer.parseInt(PROTOCOL_VERSION))
            .simpleChannel();

    /** 消息 ID 计数器。 */
    private static int nextId = 0;

    private ChipNet() {
    }

    /**
     * 将 {@link FMLCommonSetupEvent} 监听器挂载到模组事件总线以完成消息注册。
     * <p>在模组主类构造函数中调用。</p>
     *
     * @param modBusGroup 模组事件总线
     */
    public static void register(BusGroup modBusGroup) {
        FMLCommonSetupEvent.getBus(modBusGroup).addListener(ChipNet::onCommonSetup);
    }

    /**
     * 在 {@link FMLCommonSetupEvent} 中注册芯片脚本消息。
     *
     * @param event 通用初始化事件
     */
    private static void onCommonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // C→S：上传芯片固件源码并控制运行状态
            CHANNEL.messageBuilder(ChipScriptPacket.class, nextId++)
                    .encoder((msg, buf) -> ChipScriptPacket.encode(msg, (net.minecraft.network.RegistryFriendlyByteBuf) buf))
                    .decoder(buf -> ChipScriptPacket.decode((net.minecraft.network.RegistryFriendlyByteBuf) buf))
                    .consumerMainThread(ChipScriptPacket::handle)
                    .add();
        });
    }
}
