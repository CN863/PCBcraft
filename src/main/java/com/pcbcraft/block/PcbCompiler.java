package com.pcbcraft.block;

import com.pcbcraft.PCBCraft;
import com.pcbcraft.data.ComponentInstance;
import com.pcbcraft.data.GridPoint;
import com.pcbcraft.data.LayerType;
import com.pcbcraft.data.Pad;
import com.pcbcraft.data.PcbDesign;
import com.pcbcraft.data.Via;
import com.pcbcraft.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Set;

/**
 * PCB 编译器（Task 3.2）：将 {@link PcbDesign} 物化为游戏世界中的多层方块结构。
 * <p>
 * 编译产物为一组垂直堆叠的 {@link PcbBlock}，从 {@code basePos} 向上按 Y 轴分层：
 * </p>
 * <ul>
 *   <li>Y 偏移 0..copperCount-1：铜层（{@link LayerType#COPPER}），铺满板轮廓 width×height</li>
 *   <li>Y 偏移 copperCount：阻焊层（{@link LayerType#MASK}），铺满板轮廓但在焊盘/过孔位置开窗</li>
 *   <li>Y 偏移 copperCount+1：丝印层（{@link LayerType#SILK}），仅在元件 origin 位置放置方块</li>
 * </ul>
 * <p>
 * 底层铜层 (0,0) 位置方块标记 {@link PcbBlock#MASTER} 为 true，承载 {@link PcbBlockEntity}
 * 并保存完整设计数据。钻孔层（{@link LayerType#DRILL}）不作为独立可见方块，由后续阶段处理。
 * </p>
 * <p>
 * 本类仅提供服务端方法，编译操作必须在 {@link ServerLevel} 中执行。
 * 客户端编辑器通过 {@code PcbCompilerHook} 将设计写入手持 schematic 物品 NBT，
 * 玩家用 {@code PcbBlockItem} 右键空地时在服务端触发实际编译。
 * </p>
 */
public final class PcbCompiler {

    private PcbCompiler() {
    }

    /**
     * 将 PCB 设计编译为世界中的多方块结构。
     * <p>
     * 编译流程：
     * <ol>
     *   <li>校验设计尺寸与 Y 范围合法性</li>
     *   <li>收集焊盘/过孔位置（阻焊开窗）与元件 origin（丝印标记）</li>
     *   <li>区域占用预检查：若任何目标位置存在非空气、非 PCB 方块则失败</li>
     *   <li>清除区域内旧 PCB 方块（支持重新编译）</li>
     *   <li>按层序逐格放置方块，设置 LAYER_TYPE / LAYER_INDEX / MASTER 属性</li>
     *   <li>设置 MASTER 方块的 BlockEntity：写入设计、清零上电状态、同步客户端</li>
     * </ol>
     *
     * @param level   服务端世界
     * @param basePos 编译基准坐标（底层铜层 (0,0) 方块位置）
     * @param design  PCB 设计
     * @return true 表示编译成功；false 表示设计为 null / 尺寸无效 / Y 越界 / 区域被占用
     */
    public static boolean compile(ServerLevel level, BlockPos basePos, PcbDesign design) {
        if (design == null) {
            PCBCraft.LOGGER.warn("PCB 编译失败：设计为 null");
            return false;
        }
        int width = design.getWidth();
        int height = design.getHeight();
        int copperCount = design.copperLayerCount();
        // 总可见层数 = 铜层 + 阻焊 + 丝印
        int totalLayers = copperCount + 2;
        if (width <= 0 || height <= 0 || copperCount <= 0) {
            PCBCraft.LOGGER.warn("PCB 编译失败：尺寸无效 {}x{} 铜层={}", width, height, copperCount);
            return false;
        }

        // Y 越界检查
        int minY = basePos.getY();
        int maxY = basePos.getY() + totalLayers - 1;
        if (minY < level.getMinY() || maxY >= level.getMaxY()) {
            PCBCraft.LOGGER.warn("PCB 编译失败：Y 越界 baseY={} 总层={} 范围=[{},{}]",
                    basePos.getY(), totalLayers, level.getMinY(), level.getMaxY());
            return false;
        }

        // 收集焊盘/过孔位置（阻焊层开窗用）
        Set<GridPoint> padViaPositions = new HashSet<>();
        for (ComponentInstance c : design.getComponents()) {
            for (Pad p : c.getPads()) {
                padViaPositions.add(p.getPos());
            }
        }
        for (Via v : design.getVias()) {
            padViaPositions.add(v.getPos());
        }

        // 收集元件 origin 位置（丝印层放方块用）
        Set<GridPoint> silkPositions = new HashSet<>();
        for (ComponentInstance c : design.getComponents()) {
            silkPositions.add(c.getOrigin());
        }

        Block pcbBlock = ModBlocks.PCB_BLOCK.get();

        // 区域占用预检查：若存在非空气、非 PCB 方块则失败
        for (int layerY = 0; layerY < totalLayers; layerY++) {
            for (int gx = 0; gx < width; gx++) {
                for (int gy = 0; gy < height; gy++) {
                    BlockPos pos = basePos.offset(gx, layerY, gy);
                    BlockState existing = level.getBlockState(pos);
                    if (!existing.isAir() && existing.getBlock() != pcbBlock) {
                        PCBCraft.LOGGER.warn("PCB 编译失败：位置 {} 已被占用 ({})", pos, existing);
                        return false;
                    }
                }
            }
        }

        // 清除区域内旧 PCB 方块（支持重新编译，避免残留方块）
        for (int layerY = 0; layerY < totalLayers; layerY++) {
            for (int gx = 0; gx < width; gx++) {
                for (int gy = 0; gy < height; gy++) {
                    BlockPos pos = basePos.offset(gx, layerY, gy);
                    if (level.getBlockState(pos).getBlock() == pcbBlock) {
                        level.removeBlock(pos, false);
                    }
                }
            }
        }

        // 按层序逐格放置方块
        for (int layerY = 0; layerY < totalLayers; layerY++) {
            // 确定当前层的类型与索引
            LayerType type;
            int layerIndex;
            if (layerY < copperCount) {
                type = LayerType.COPPER;
                layerIndex = layerY;
            } else if (layerY == copperCount) {
                type = LayerType.MASK;
                layerIndex = copperCount;
            } else {
                type = LayerType.SILK;
                layerIndex = copperCount + 1;
            }

            for (int gx = 0; gx < width; gx++) {
                for (int gy = 0; gy < height; gy++) {
                    GridPoint gp = new GridPoint(gx, gy);

                    // 阻焊层：焊盘/过孔位置开窗（不放方块，露出下层铜）
                    if (type == LayerType.MASK && padViaPositions.contains(gp)) {
                        continue;
                    }
                    // 丝印层：仅在元件 origin 位置放方块（标注位号由后续渲染层处理）
                    if (type == LayerType.SILK && !silkPositions.contains(gp)) {
                        continue;
                    }

                    BlockPos pos = basePos.offset(gx, layerY, gy);
                    // 底层铜层 (0,0) 为 MASTER 方块
                    boolean isMaster = (layerY == 0 && gx == 0 && gy == 0);
                    BlockState state = pcbBlock.defaultBlockState()
                            .setValue(PcbBlock.LAYER_TYPE, type)
                            .setValue(PcbBlock.LAYER_INDEX, layerIndex)
                            .setValue(PcbBlock.MASTER, isMaster);
                    level.setBlock(pos, state, 3);
                }
            }
        }

        // 设置 MASTER 方块的 BlockEntity：写入设计、清零上电状态
        BlockPos masterPos = basePos;
        BlockEntity be = level.getBlockEntity(masterPos);
        if (be instanceof PcbBlockEntity pcb) {
            pcb.setDesign(design);
            pcb.setPowered(false);
            pcb.setVisibleLayer(-1);
            pcb.markUpdated();
        } else {
            PCBCraft.LOGGER.warn("PCB 编译：MASTER 方块实体未创建 @ {}", masterPos);
        }

        PCBCraft.LOGGER.info("PCB 编译成功：{} ({}x{} 铜层{} 总层{}) @ {}",
                design.getName(), width, height, copperCount, totalLayers, basePos);
        return true;
    }

    /**
     * 从物品 NBT 读取设计并编译。
     * <p>
     * 物品 NBT 的 "Design" 子标签由编辑器或 schematic 物品写入。
     * 若物品无 "Design" 标签则返回 false。
     * </p>
     *
     * @param level 服务端世界
     * @param pos   编译基准坐标
     * @param stack 携带设计 NBT 的物品栈
     * @return true 表示编译成功；false 表示无设计或编译失败
     */
    public static boolean compileFromItem(ServerLevel level, BlockPos pos, ItemStack stack) {
        if (stack == null) {
            return false;
        }
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return false;
        }
        CompoundTag tag = customData.copyTag();
        if (!tag.contains("Design")) {
            return false;
        }
        PcbDesign design = PcbDesign.load(tag.getCompound("Design").orElse(new CompoundTag()));
        return compile(level, pos, design);
    }
}
