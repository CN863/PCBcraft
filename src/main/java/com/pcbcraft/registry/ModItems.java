package com.pcbcraft.registry;

import com.pcbcraft.item.PcbBlockItem;
import com.pcbcraft.item.SchematicItem;
import com.pcbcraft.tool.OscilloscopeItem;
import com.pcbcraft.tool.ProbeItem;
import com.pcbcraft.tool.SolderingIronItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.RegistryObject;

/**
 * 模组物品注册条目。
 * <p>创造模式标签页留待 Phase 7 处理，本阶段不注册 CreativeTab。</p>
 */
public final class ModItems {

    private ModItems() {
    }

    /** PCB 方块对应物品，使用 {@link PcbBlockItem} 以支持从 NBT 读取设计并编译生成。 */
    public static final RegistryObject<PcbBlockItem> PCB_BLOCK_ITEM = ModRegistries.ITEMS.register("pcb_block",
            () -> new PcbBlockItem(ModBlocks.PCB_BLOCK.get(), new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("pcbcraft", "pcb_block")))));

    /** PCB 设计图物品，承载一份可编辑的 {@link com.pcbcraft.data.PcbDesign}。 */
    public static final RegistryObject<SchematicItem> PCB_SCHEMATIC = ModRegistries.ITEMS.register("pcb_schematic",
            () -> new SchematicItem(new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("pcbcraft", "pcb_schematic")))));

    /** 可编程芯片方块对应物品。 */
    public static final RegistryObject<BlockItem> CHIP_BLOCK_ITEM = ModRegistries.ITEMS.register("chip_block",
            () -> new BlockItem(ModBlocks.CHIP_BLOCK.get(), new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("pcbcraft", "chip_block")))));

    /** 探针（Task 6.1）：右键 PCB 读取该格电压/电流/网络。 */
    public static final RegistryObject<ProbeItem> PROBE = ModRegistries.ITEMS.register("probe",
            () -> new ProbeItem(new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("pcbcraft", "probe")))
                    .stacksTo(1)));

    /** 示波器（Task 6.2/6.4）：右键 PCB 切换信号可视化，潜行右键切换热力图模式。 */
    public static final RegistryObject<OscilloscopeItem> OSCILLOSCOPE = ModRegistries.ITEMS.register("oscilloscope",
            () -> new OscilloscopeItem(new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("pcbcraft", "oscilloscope")))
                    .stacksTo(1)));

    /** 电烙铁（Task 6.3）：右键 PCB 焊盘拆焊元件，增量更新网络表。 */
    public static final RegistryObject<SolderingIronItem> SOLDERING_IRON = ModRegistries.ITEMS.register("soldering_iron",
            () -> new SolderingIronItem(new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("pcbcraft", "soldering_iron")))
                    .stacksTo(1)));

    /**
     * 触发本类静态初始化，确保上述条目已加入 DeferredRegister。
     * <p>在 {@link ModRegistries#register} 之后由主类调用，保证 RegisterEvent 触发前条目已入队。</p>
     */
    public static void init() {
    }
}
