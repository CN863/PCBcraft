package com.pcbcraft.block;

import com.pcbcraft.data.LayerType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * PCB 方块。
 * <p>
 * 多层 PCB 在世界中由若干相邻 PcbBlock 组成，每个方块携带 {@link #LAYER_TYPE} 与
 * {@link #LAYER_INDEX} 描述其所属图层；其中标记 {@link #MASTER} 为 true 的方块承载
 * {@link PcbBlockEntity}（保存整块板的设计数据），非 master 方块本阶段不持有 BlockEntity。
 * 钻孔（{@link LayerType#DRILL}）由 Phase 3.2 编译器单独处理，不作为可见方块，故
 * {@link #LAYER_TYPE} 仅限定 COPPER/MASK/SILK 三种取值。
 * </p>
 * <p>
 * 本阶段 {@link #use} 仅作占位返回，编辑器屏幕打开由 Phase 2 在客户端事件中接入，
 * 因此本类不依赖 {@code com.pcbcraft.editor} 包。
 * </p>
 */
public class PcbBlock extends Block implements EntityBlock {

    /** 方块所属图层类型（限定铜/阻焊/丝印三种可见图层）。 */
    public static final EnumProperty<LayerType> LAYER_TYPE =
            EnumProperty.create("layer_type", LayerType.class, LayerType.COPPER, LayerType.MASK, LayerType.SILK);

    /** 图层序号 0..15。 */
    public static final IntegerProperty LAYER_INDEX = IntegerProperty.create("layer_index", 0, 15);

    /** 是否为承载 BlockEntity 的主方块。 */
    public static final BooleanProperty MASTER = BooleanProperty.create("master");

    public PcbBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(LAYER_TYPE, LayerType.COPPER)
                .setValue(LAYER_INDEX, 0)
                .setValue(MASTER, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LAYER_TYPE, LAYER_INDEX, MASTER);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(MASTER) ? new PcbBlockEntity(pos, state) : null;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!state.getValue(MASTER)) {
            return InteractionResult.PASS;
        }
        // 潜行 + 右键：循环切换可见层
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof PcbBlockEntity pcb) {
                    pcb.cycleVisibleLayer();
                    pcb.markUpdated();
                    // 发粒子提示层号变化
                    serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, false, false,
                            pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                            5, 0.3, 0.3, 0.3, 0.1);
                    // 显示当前可见层信息
                    int vl = pcb.getVisibleLayer();
                    String msg = vl < 0 ? "显示所有层" : "仅显示层 " + vl;
                    player.sendSystemMessage(Component.literal(msg));
                    // TODO Phase 3.x：visibleLayer>=0 时需 BlockEntityRenderer 配合隐藏非当前层方块
                }
            }
            return InteractionResult.SUCCESS;
        }
        // 非潜行：打开编辑器（客户端）
        if (level.isClientSide()) {
            // TODO Phase 2：在此打开 PCB 编辑器屏幕（由 com.pcbcraft.editor 包接入）
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }
}
