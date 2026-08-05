package com.pcbcraft.item;

import com.pcbcraft.data.PcbDesign;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * PCB 设计图物品。
 * <p>
 * 在物品 NBT 的 "Design" 字段承载一份 {@link PcbDesign}，供玩家保存/携带/载入电路设计。
 * 设计数据的序列化委托给 {@link PcbDesign#save(CompoundTag)} / {@link PcbDesign#load(CompoundTag)}；
 * 后续阶段由编辑器 GUI 读写本物品。
 * </p>
 */
public class SchematicItem extends Item {

    public SchematicItem(Item.Properties properties) {
        super(properties);
    }

    /**
     * 读取物品中保存的 PCB 设计。
     *
     * @param stack 物品栈
     * @return 设计实例；未保存时返回 {@code null}
     */
    public static PcbDesign getDesign(ItemStack stack) {
        CustomData d = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = d != null ? d.copyTag() : new CompoundTag();
        if (tag.contains("Design")) {
            return PcbDesign.load(tag.getCompound("Design").orElse(new CompoundTag()));
        }
        return null;
    }

    /**
     * 将 PCB 设计写入物品 NBT。
     *
     * @param stack  物品栈
     * @param design 设计实例；为 {@code null} 时写入空 CompoundTag 以清除既有设计
     */
    public static void setDesign(ItemStack stack, PcbDesign design) {
        CustomData d = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = d != null ? d.copyTag() : new CompoundTag();
        tag.put("Design", design != null ? design.save() : new CompoundTag());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
}
