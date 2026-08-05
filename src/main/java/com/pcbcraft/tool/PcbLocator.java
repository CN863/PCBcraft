package com.pcbcraft.tool;

import com.pcbcraft.block.PcbBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * PCB 主方块定位工具（Task 6.x 共用）。
 * <p>
 * PCB 在世界中以垂直堆叠的 {@link PcbBlock} 多方块结构存在，其中底层铜层 (0,0) 位置
 * 标记 {@link PcbBlock#MASTER} 为 true 并承载 {@link com.pcbcraft.block.PcbBlockEntity}。
 * 本类从任一 PCB 子方块反推其主方块坐标，供探针/示波器/电烙铁等工具复用。
 * </p>
 * <p>
 * 反推策略：在被点击方块的 X,Z 列上向上/下扫描（各 32 格），首个 MASTER=true 的方块即为主方块。
 * 主方块位于堆叠最底层（layerY=0），故优先向下扫描。
 * </p>
 */
public final class PcbLocator {

    /** 单方向最大扫描半径（方块数）。 */
    private static final int MAX_SCAN = 32;

    private PcbLocator() {
    }

    /**
     * 在指定方块所在的 X,Z 列上查找 PCB 主方块。
     *
     * @param level 世界
     * @param pos   被点击的方块坐标
     * @return 主方块坐标；未找到返回 {@code null}
     */
    public static BlockPos findMaster(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return null;
        }
        int x = pos.getX();
        int y0 = pos.getY();
        int z = pos.getZ();
        // 优先向下（主方块在堆叠底层）
        for (int dy = 0; dy <= MAX_SCAN; dy++) {
            BlockPos p = new BlockPos(x, y0 - dy, z);
            if (isMaster(level, p)) {
                return p;
            }
        }
        // 回退：向上扫描
        for (int dy = 1; dy <= MAX_SCAN; dy++) {
            BlockPos p = new BlockPos(x, y0 + dy, z);
            if (isMaster(level, p)) {
                return p;
            }
        }
        return null;
    }

    /**
     * 由点击坐标反推板内网格坐标 gx。
     *
     * @param clicked   被点击方块坐标
     * @param masterPos 主方块坐标
     * @return 板内 gx
     */
    public static int gridX(BlockPos clicked, BlockPos masterPos) {
        return clicked.getX() - masterPos.getX();
    }

    /**
     * 由点击坐标反推板内网格坐标 gy。
     *
     * @param clicked   被点击方块坐标
     * @param masterPos 主方块坐标
     * @return 板内 gy
     */
    public static int gridY(BlockPos clicked, BlockPos masterPos) {
        return clicked.getZ() - masterPos.getZ();
    }

    /**
     * 由点击坐标反推层 Y 偏移（0=底层铜，copperCount=阻焊，copperCount+1=丝印）。
     *
     * @param clicked   被点击方块坐标
     * @param masterPos 主方块坐标
     * @return 层 Y 偏移
     */
    public static int layerY(BlockPos clicked, BlockPos masterPos) {
        return clicked.getY() - masterPos.getY();
    }

    private static boolean isMaster(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof PcbBlock && state.getValue(PcbBlock.MASTER);
    }
}
