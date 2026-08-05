package com.pcbcraft.editor;

import com.pcbcraft.PCBCraft;
import com.pcbcraft.data.PcbDesign;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * PCB 编译钩子，避免 {@code com.pcbcraft.editor} 包对 {@code com.pcbcraft.block} 包形成编译依赖。
 * <p>
 * 编辑器 GUI 在客户端运行，而多方块编译必须在服务端执行（放置方块是服务端操作）。
 * 为避免引入网络包复杂度，本阶段采用如下方案：
 * </p>
 * <ol>
 *   <li>编辑器按 G 键编译时，本钩子将设计写入主手 schematic 物品 NBT 并显示提示消息</li>
 *   <li>玩家切换到 PCB 方块物品（或副手持有 schematic），右键空地在服务端触发
 *       {@code PcbBlockItem.useOn} → {@code PcbCompiler.compile} 完成实际多方块生成</li>
 * </ol>
 * <p>
 * 本钩子始终返回 false（不直接完成编译），仅完成客户端数据准备与用户引导。
 * </p>
 */
@OnlyIn(Dist.CLIENT)
public final class PcbCompilerHook {

    private PcbCompilerHook() {
    }

    /**
     * 编译当前设计：将设计写入主手 schematic 物品 NBT 并提示玩家用 PCB 方块物品放置。
     * <p>
     * 实际多方块生成由 {@code PcbBlockItem.useOn} 在服务端调用
     * {@code com.pcbcraft.block.PcbCompiler.compile} 完成。
     * </p>
     *
     * @param design 当前 PCB 设计
     * @return 始终返回 false（编译不在客户端完成）
     */
    public static boolean compile(PcbDesign design) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return false;
        }

        // 将设计写入主手物品 NBT（pcb_schematic），供 PCB 方块物品 useOn 读取
        ItemStack stack = mc.player.getMainHandItem();
        if (!stack.isEmpty()) {
            CustomData d = stack.get(DataComponents.CUSTOM_DATA);
            CompoundTag tag = d != null ? d.copyTag() : new CompoundTag();
            tag.put("Design", design.save());
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }

        mc.player.sendSystemMessage(
                Component.literal("设计已保存，用 PCB 方块物品右键空地生成"));

        PCBCraft.LOGGER.info("PCB 设计已保存到原理图：{} ({}x{} 铜层{})",
                design.getName(), design.getWidth(), design.getHeight(), design.copperLayerCount());
        return false;
    }
}
