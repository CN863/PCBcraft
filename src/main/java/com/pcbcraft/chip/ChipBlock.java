package com.pcbcraft.chip;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 可编程芯片方块（Task 5.1）。
 * <p>
 * 独立可放置方块，右键打开 {@link ChipTerminalScreen} 编辑固件；潜行右键在 actionbar 显示
 * 当前绑定的 PCB master 状态。绑定逻辑（潜行右键相邻 PCB master 写入 {@code boundMaster}）
 * 本阶段留 TODO，I/O 联动在 {@code boundMaster} 非空时即生效。
 * </p>
 */
public class ChipBlock extends Block implements EntityBlock {

    public ChipBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ChipBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                 BlockHitResult hit) {
        // 潜行右键：显示绑定状态
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide()) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof ChipBlockEntity chip) {
                    BlockPos m = chip.getBoundMaster();
                    String msg = (m == null)
                            ? "芯片未绑定 PCB"
                            : "已绑定 PCB @ " + m.getX() + "," + m.getY() + "," + m.getZ();
                    player.sendSystemMessage(Component.literal(msg));
                }
            }
            return InteractionResult.SUCCESS;
        }
        // 非潜行：客户端打开终端屏幕（通过客户端专属 opener，避免客户端类污染服务端）
        if (level.isClientSide()) {
            ClientChipOpener.open(pos);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.CONSUME;
    }
}
