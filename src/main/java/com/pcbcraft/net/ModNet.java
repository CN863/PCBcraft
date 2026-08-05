package com.pcbcraft.net;

import com.pcbcraft.PCBCraft;
import net.minecraft.resources.Identifier;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.SimpleChannel;

/**
 * 仿真状态同步网络通道（Forge 65.1.0 / MC 26.2 适配版）。
 * <p>
 * 使用 Forge 传统的 {@link SimpleChannel} 注册两类载荷，向后兼容性最佳：
 * </p>
 * <ul>
 *   <li>{@link VisTogglePacket}（C→S）：客户端请求/取消对某 PCB 的仿真状态周期推送；</li>
 *   <li>{@link SimStatePacket}（S→C）：服务端推送某 PCB 的节点电压（毫伏整数数组）+ 板尺寸 + 短路标志，
 *       供客户端走线染色与探针浮窗渲染。</li>
 * </ul>
 * <p>发送端使用 {@link SimpleChannel#sendToServer} / {@link net.minecraftforge.network.PacketDistributor#sendToPlayer}，
 * 与 {@link com.pcbcraft.chip.ChipNet} 通道独立，互不干扰。</p>
 */
public final class ModNet {

    /** 通道协议版本，客户端与服务端必须一致。 */
    private static final String PROTOCOL_VERSION = "1";

    /** 主通道实例。 */
    public static final SimpleChannel CHANNEL = ChannelBuilder
            .named(Identifier.fromNamespaceAndPath(PCBCraft.MOD_ID, "main"))
            .acceptedVersions(net.minecraftforge.network.Channel.VersionTest.exact(Integer.parseInt(PROTOCOL_VERSION)))
            .networkProtocolVersion(Integer.parseInt(PROTOCOL_VERSION))
            .simpleChannel();

    /** 消息 ID 计数器，{@code messageBuilder} 需要唯一整数 ID。 */
    private static int nextId = 0;

    private ModNet() {
    }

    /**
     * 注册仿真状态同步通道的消息。
     * <p>在模组主类构造函数中调用，将 {@link FMLCommonSetupEvent} 监听器挂载到模组事件总线。
     * SimpleChannel 实例为静态字段，类加载时即创建。</p>
     *
     * @param modBusGroup 模组事件总线
     */
    public static void register(BusGroup modBusGroup) {
        FMLCommonSetupEvent.getBus(modBusGroup).addListener(ModNet::onCommonSetup);
    }

    /**
     * 在 {@link FMLCommonSetupEvent} 中注册所有消息。
     * <p>使用 {@code enqueueWork} 保证注册在主线程同步执行。</p>
     *
     * @param event 通用初始化事件
     */
    private static void onCommonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // C→S：客户端请求/取消仿真状态推送
            CHANNEL.messageBuilder(VisTogglePacket.class, nextId++)
                    .encoder((msg, buf) -> VisTogglePacket.encode(msg, (net.minecraft.network.RegistryFriendlyByteBuf) buf))
                    .decoder(buf -> VisTogglePacket.decode((net.minecraft.network.RegistryFriendlyByteBuf) buf))
                    .consumerMainThread(VisTogglePacket::handle)
                    .add();

            // S→C：服务端推送仿真状态快照
            CHANNEL.messageBuilder(SimStatePacket.class, nextId++)
                    .encoder((msg, buf) -> SimStatePacket.encode(msg, (net.minecraft.network.RegistryFriendlyByteBuf) buf))
                    .decoder(buf -> SimStatePacket.decode((net.minecraft.network.RegistryFriendlyByteBuf) buf))
                    .consumerMainThread(SimStatePacket::handle)
                    .add();
        });
    }
}
