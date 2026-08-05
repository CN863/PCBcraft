package com.pcbcraft.editor;

import com.pcbcraft.PCBCraft;
import com.pcbcraft.data.PcbDesign;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * 编辑器入口：监听客户端右键物品事件，若主手物品为 {@code pcbcraft:pcb_schematic}
 * 则打开 {@link PcbEditorScreen}。
 * <p>
 * 直接从物品 NBT 的 {@code Design} 子标签读取设计，不依赖 {@code com.pcbcraft.item.SchematicItem}
 * （Phase 3.1 才创建），避免编译期耦合。
 * </p>
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = PCBCraft.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.FORGE)
public final class EditorOpener {

    /** 触发编辑器的物品 id。 */
    public static final Identifier SCHEMATIC_ITEM_ID =
            Identifier.fromNamespaceAndPath(PCBCraft.MOD_ID, "pcb_schematic");

    private EditorOpener() {
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        // 仅在客户端处理
        if (!event.getLevel().isClientSide()) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) {
            return;
        }
        Identifier key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (key == null || !key.equals(SCHEMATIC_ITEM_ID)) {
            return;
        }

        // 直接从 NBT 读取设计，避免依赖 SchematicItem 类
        CompoundTag tag = stack.has(DataComponents.CUSTOM_DATA) ? stack.get(DataComponents.CUSTOM_DATA).copyTag() : new CompoundTag();
        CompoundTag designTag = tag.getCompound("Design").orElse(new CompoundTag());
        PcbDesign design = designTag.isEmpty() ? null : PcbDesign.load(designTag);

        Minecraft.getInstance().setScreenAndShow(new PcbEditorScreen(design));
        event.setCancellationResult(InteractionResult.SUCCESS);
    }
}
