package com.pcbcraft.item;

import com.pcbcraft.block.PcbCompiler;
import com.pcbcraft.data.PcbDesign;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.block.Block;

/**
 * PCB 方块物品。
 * <p>
 * 继承 {@link BlockItem}，重写 {@link #useOn(UseOnContext)} 以支持从物品 NBT 读取
 * {@link PcbDesign} 并调用 {@link PcbCompiler#compile} 在服务端生成多方块结构。
 * </p>
 * <p>
 * 编译触发条件（满足其一）：
 * <ul>
 *   <li>本物品 NBT 含 "Design" 标签</li>
 *   <li>另一只手持有含 "Design" 标签的物品（如 pcb_schematic）——便于编辑器保存后直接放置</li>
 * </ul>
 * 编译成功时不消耗物品，便于重复生成；失败或无设计则回退到普通方块放置。
 * </p>
 */
public class PcbBlockItem extends BlockItem {

    public PcbBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        ItemStack stack = context.getItemInHand();
        Player player = context.getPlayer();

        if (level instanceof ServerLevel serverLevel) {
            PcbDesign design = null;

            // 优先从本物品 NBT 读取设计
            if (stack.has(DataComponents.CUSTOM_DATA) && stack.get(DataComponents.CUSTOM_DATA).copyTag().contains("Design")) {
                design = PcbDesign.load(stack.get(DataComponents.CUSTOM_DATA).copyTag().getCompound("Design").orElse(new net.minecraft.nbt.CompoundTag()));
            }

            // 若本物品无设计，尝试从另一只手的物品读取（支持手持 schematic + PCB 方块物品放置）
            if (design == null && player != null) {
                InteractionHand otherHand = context.getHand() == InteractionHand.MAIN_HAND
                        ? InteractionHand.OFF_HAND
                        : InteractionHand.MAIN_HAND;
                ItemStack otherStack = player.getItemInHand(otherHand);
                if (otherStack.has(DataComponents.CUSTOM_DATA) && otherStack.get(DataComponents.CUSTOM_DATA).copyTag().contains("Design")) {
                    design = PcbDesign.load(otherStack.get(DataComponents.CUSTOM_DATA).copyTag().getCompound("Design").orElse(new net.minecraft.nbt.CompoundTag()));
                }
            }

            if (design != null) {
                BlockPos basePos = context.getClickedPos().relative(context.getClickedFace());
                if (PcbCompiler.compile(serverLevel, basePos, design)) {
                    // 不消耗物品，便于重复生成
                    return InteractionResult.SUCCESS;
                }
            }
        }

        return super.useOn(context);
    }
}
