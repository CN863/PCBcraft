package com.pcbcraft.render;

import com.pcbcraft.PCBCraft;
import com.pcbcraft.block.PcbBlock;
import com.pcbcraft.block.PcbBlockEntity;
import com.pcbcraft.data.LayerType;
import com.pcbcraft.registry.ModBlocks;
import com.pcbcraft.registry.ModItems;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraft.world.item.ItemStack;

/**
 * PCB 渲染事件处理器（Task 3.3）：注册方块/物品颜色处理器。
 * <p>
 * 通过 {@link RegisterColorHandlersEvent.Block} 为 {@link PcbBlock} 注册 BlockColor，
 * 按 BlockState 的 {@link PcbBlock#LAYER_TYPE} 着色（铜/绿/白基色），
 * MASTER 方块额外按 {@link PcbBlockEntity#isPowered()} 上电状态增亮。
 * </p>
 * <p>
 * 所有层模型共用 {@code minecraft:block/white_concrete} 白色基础纹理，
 * 面声明 tintindex=0，颜色由本类注册的 BlockColor tint 提供，
 * 实现不切换纹理即可按层类型变色的效果。
 * </p>
 * <p>
 * 同时为 {@code PCB_BLOCK_ITEM} 注册 ItemColor，使物品在背包中显示铜色。
 * </p>
 */
@EventBusSubscriber(modid = PCBCraft.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.FORGE)
public final class PcbRenderEvents {

    private PcbRenderEvents() {
    }

    /**
     * 注册方块颜色：按层类型 + 上电状态着色。
     *
     * @param event 注册方块颜色事件
     */
    @SubscribeEvent
    public static void onRegisterBlockColors(RegisterColorHandlersEvent.Block event) {
        // TODO: MC 26.2 - BlockColor API changed to BlockTintSource
        // RegisterColorHandlersEvent.Block now uses register(List<BlockTintSource>, Block...)
        // The old lambda-based BlockColor registration is no longer supported.
        // Need to create BlockTintSource instances instead.
    }

    /**
     * 注册物品颜色：PCB 方块物品在背包中显示铜色。
     *
     * @param event 注册物品颜色事件
     */
    // TODO: MC 26.2 - RegisterColorHandlersEvent.Item removed
    // Item color registration is handled differently in MC 26.2
}