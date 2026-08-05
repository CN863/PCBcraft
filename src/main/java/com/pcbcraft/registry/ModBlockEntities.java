package com.pcbcraft.registry;

import com.pcbcraft.block.PcbBlockEntity;
import com.pcbcraft.chip.ChipBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.RegistryObject;

import java.util.Set;

/**
 * 模组 BlockEntityType 注册条目。
 */
public final class ModBlockEntities {

    private ModBlockEntities() {
    }

    /** PCB 方块实体类型，绑定到 {@link ModBlocks#PCB_BLOCK}。 */
    public static final RegistryObject<BlockEntityType<PcbBlockEntity>> PCB_BLOCK_ENTITY =
            ModRegistries.BLOCK_ENTITIES.register("pcb_block",
                    () -> new BlockEntityType<>(PcbBlockEntity::new, Set.of(ModBlocks.PCB_BLOCK.get())));

    /** 可编程芯片方块实体类型，绑定到 {@link ModBlocks#CHIP_BLOCK}。 */
    public static final RegistryObject<BlockEntityType<ChipBlockEntity>> CHIP_BLOCK_ENTITY =
            ModRegistries.BLOCK_ENTITIES.register("chip_block",
                    () -> new BlockEntityType<>(ChipBlockEntity::new, Set.of(ModBlocks.CHIP_BLOCK.get())));

    /**
     * 触发本类静态初始化，确保 {@link #PCB_BLOCK_ENTITY} 与 {@link #CHIP_BLOCK_ENTITY} 已加入 DeferredRegister。
     * <p>在 {@link ModRegistries#register} 之后由主类调用，保证 RegisterEvent 触发前条目已入队。</p>
     */
    public static void init() {
    }
}
