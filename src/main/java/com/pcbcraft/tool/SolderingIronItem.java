package com.pcbcraft.tool;

import com.pcbcraft.block.PcbBlock;
import com.pcbcraft.block.PcbBlockEntity;
import com.pcbcraft.data.ComponentInstance;
import com.pcbcraft.data.GridPoint;
import com.pcbcraft.data.Pad;
import com.pcbcraft.data.PcbDesign;
import com.pcbcraft.library.ComponentDef;
import com.pcbcraft.library.ComponentLibrary;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.context.UseOnContext;

/**
 * 电烙铁物品（Task 6.3）— 拆焊工具。
 * <p>
 * 手持电烙铁右键 PCB 任一方块，在服务端定位点击焊盘所属元件并从 design 移除（拆焊），
 * 调用 {@link PcbBlockEntity#setDesign} 触发 {@link com.pcbcraft.sim.SimTickScheduler#updateDesign}
 * 增量重建网表，无需重新编译世界方块。
 * </p>
 * <ul>
 *   <li>找到元件：移除后回写 design，向玩家发"已拆焊 &lt;designator&gt;"；</li>
 *   <li>未找到：发"此处无可拆焊元件"；</li>
 *   <li>若被拆元件为电源（vsource）且为板上唯一电源：顺带 setPowered(false) 停止仿真。</li>
 * </ul>
 * <p>换件（直接替换为其它元件）本阶段留 TODO：当前实现"拆焊"，换件=拆焊后用编辑器重新放置。</p>
 */
public class SolderingIronItem extends Item {

    /** 电源元件模型类型标识。 */
    private static final String VSOURCE_TYPE = "vsource";

    public SolderingIronItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos clicked = ctx.getClickedPos();
        if (!(level.getBlockState(clicked).getBlock() instanceof PcbBlock)) {
            return InteractionResult.PASS;
        }
        // 客户端直接消费，阻止编辑器/切层交互
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        BlockPos master = PcbLocator.findMaster(level, clicked);
        Player player = ctx.getPlayer();
        if (master == null) {
            return InteractionResult.PASS;
        }
        if (!(level.getBlockEntity(master) instanceof PcbBlockEntity pcb)) {
            return InteractionResult.PASS;
        }
        PcbDesign design = pcb.getDesign();
        if (design == null) {
            if (player != null) {
                player.sendSystemMessage(Component.literal("此处无 PCB 设计"));
            }
            return InteractionResult.SUCCESS;
        }

        int gx = PcbLocator.gridX(clicked, master);
        int gy = PcbLocator.gridY(clicked, master);
        GridPoint point = GridPoint.of(gx, gy);

        // 查找点击焊盘所属元件
        ComponentInstance target = null;
        for (ComponentInstance c : design.getComponents()) {
            for (Pad p : c.getPads()) {
                if (point.equals(p.getPos())) {
                    target = c;
                    break;
                }
            }
            if (target != null) {
                break;
            }
        }

        if (target == null) {
            if (player != null) {
                player.sendSystemMessage(Component.literal("此处无可拆焊元件"));
            }
            return InteractionResult.SUCCESS;
        }

        String designator = target.getDesignator();
        ComponentLibrary lib = ComponentLibrary.get();
        boolean wasVsource = isVsource(lib, target);

        // 从设计移除元件并增量更新网表
        design.getComponents().remove(target);
        pcb.setDesign(design);

        // 拆掉唯一电源则停止仿真
        if (wasVsource && pcb.isPowered() && !hasVsource(lib, design)) {
            pcb.setPowered(false);
        }

        // 同步到客户端
        pcb.markUpdated();

        if (player != null) {
            player.sendSystemMessage(Component.literal("已拆焊 " + designator));
        }
        return InteractionResult.SUCCESS;
    }

    private static boolean isVsource(ComponentLibrary lib, ComponentInstance inst) {
        if (lib == null) {
            return false;
        }
        ComponentDef def = lib.get(inst.getComponentId());
        return def != null && VSOURCE_TYPE.equals(def.getModel().getType());
    }

    private static boolean hasVsource(ComponentLibrary lib, PcbDesign design) {
        if (lib == null) {
            return false;
        }
        for (ComponentInstance c : design.getComponents()) {
            if (isVsource(lib, c)) {
                return true;
            }
        }
        return false;
    }
}
