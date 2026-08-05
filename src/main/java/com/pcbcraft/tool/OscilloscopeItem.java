package com.pcbcraft.tool;

import com.pcbcraft.block.PcbBlock;
import com.pcbcraft.net.ModNet;
import com.pcbcraft.net.VisTogglePacket;
import com.pcbcraft.render.VisualizationState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraftforge.network.PacketDistributor;

/**
 * 示波器物品（Task 6.2 触发器 / Task 6.4 热力图切换）。
 * <p>
 * 手持示波器右键 PCB 任一方块切换该 PCB 的"信号可视化"开关：
 * </p>
 * <ul>
 *   <li>非潜行右键：切换 {@link VisualizationState} 中该 master 的启用状态，
 *       并向服务端发送 {@link VisTogglePacket}（C→S）请求/取消周期推送 {@code SimStatePacket}。
 *       启用后客户端渲染层（{@link com.pcbcraft.render.PcbVisualizationRender}）对该 PCB 做走线染色。
 *       订阅确认消息由服务端在 {@link VisTogglePacket} 处理时回发 actionbar。</li>
 *   <li>潜行右键：切换 {@link VisualizationState#toggleHeatmap()} 热力图模式（纯客户端，无需服务端）。
 *       热力图模式下染色按节点电压渐变（蓝→红），否则按二值红（高电平）/蓝（低电平）。</li>
 * </ul>
 * <p>可视化状态纯客户端维护，服务端仅按订阅推送仿真解数据。本类不直接引用客户端专有类，
 * 客户端分支仅操作公共数据容器 {@link VisualizationState} 与发送 C→S 数据包。</p>
 */
public class OscilloscopeItem extends Item {

    public OscilloscopeItem(Properties properties) {
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

        if (level.isClientSide()) {
            if (ctx.isSecondaryUseActive()) {
                // 潜行：切换热力图模式（纯客户端，染色样式立即变化作为反馈）
                VisualizationState.toggleHeatmap();
            } else {
                // 非潜行：切换该 PCB 可视化开关，并通知服务端订阅/退订
                boolean enabled = VisualizationState.toggle(master);
                ModNet.CHANNEL.send(new VisTogglePacket(master, enabled), net.minecraftforge.network.PacketDistributor.SERVER.noArg());
            }
        }
        // 服务端无需处理（可视化纯客户端），返回 SUCCESS 以阻止编辑器/切层交互
        return InteractionResult.SUCCESS;
    }
}
