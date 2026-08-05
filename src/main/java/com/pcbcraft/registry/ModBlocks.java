package com.pcbcraft.registry;

import com.pcbcraft.block.PcbBlock;
import com.pcbcraft.chip.ChipBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;

/**
 * 模组方块注册条目。
 * <p>复用 {@link ModRegistries#BLOCKS} 注册方块，具体方块以 {@link RegistryObject} 形式暴露。</p>
 */
public final class ModBlocks {

    /** 复用 ModRegistries 的方块 DeferredRegister。 */
    public static final DeferredRegister<Block> BLOCKS = ModRegistries.BLOCKS;

    private ModBlocks() {
    }

    /** PCB 方块：多层电路板结构的基本构成单元。 */
    public static final RegistryObject<PcbBlock> PCB_BLOCK = BLOCKS.register("pcb_block",
            () -> new PcbBlock(BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("pcbcraft", "pcb_block")))
                    .mapColor(MapColor.STONE)
                    .strength(2.0f)
                    .noOcclusion()
                    .sound(SoundType.METAL)));

    /** 可编程芯片方块：独立可放置，右键打开终端编辑 Lua 固件。 */
    public static final RegistryObject<ChipBlock> CHIP_BLOCK = BLOCKS.register("chip_block",
            () -> new ChipBlock(BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("pcbcraft", "chip_block")))
                    .mapColor(MapColor.STONE)
                    .strength(1.5f)
                    .sound(SoundType.METAL)));

    /**
     * 触发本类静态初始化，确保 {@link #PCB_BLOCK} 与 {@link #CHIP_BLOCK} 已加入 DeferredRegister。
     * <p>在 {@link ModRegistries#register} 之后由主类调用，保证 RegisterEvent 触发前条目已入队。</p>
     */
    public static void init() {
    }
}
