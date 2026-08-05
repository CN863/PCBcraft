package com.pcbcraft.registry;

import com.pcbcraft.PCBCraft;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.registries.DeferredRegister;

/**
 * 模组注册集中点。
 * <p>
 * 持有方块、物品、BlockEntityType、MenuType 四类 {@link DeferredRegister}，
 * 由 {@link #register(BusGroup)} 统一挂载到模组事件总线。具体注册条目分别由
 * {@link ModBlocks}、{@link ModItems}、{@link ModBlockEntities} 通过引用本类的
 * DeferredRegister 完成；本类不直接持有任何具体条目。
 * </p>
 */
public final class ModRegistries {

    /** 方块 DeferredRegister。 */
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, PCBCraft.MOD_ID);

    /** 物品 DeferredRegister。 */
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, PCBCraft.MOD_ID);

    /** BlockEntityType DeferredRegister。 */
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PCBCraft.MOD_ID);

    /** MenuType DeferredRegister（供后续编辑器 GUI 使用）。 */
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, PCBCraft.MOD_ID);

    private ModRegistries() {
    }

    /**
     * 将四个 DeferredRegister 注册到模组事件总线。
     * <p>
     * 调用方需随后触发各持有者类（{@link ModBlocks}/{@link ModItems}/{@link ModBlockEntities}）
     * 的静态初始化，以确保其 {@code RegistryObject} 在 RegisterEvent 触发前已入队。
     *
     * @param modBusGroup 模组事件总线
     */
    public static void register(BusGroup modBusGroup) {
        BLOCKS.register(modBusGroup);
        ITEMS.register(modBusGroup);
        BLOCK_ENTITIES.register(modBusGroup);
        MENU_TYPES.register(modBusGroup);
    }
}
