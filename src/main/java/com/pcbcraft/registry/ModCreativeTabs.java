package com.pcbcraft.registry;

import com.pcbcraft.PCBCraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;

/**
 * 创造模式标签页注册（Phase 7）。
 * <p>
 * 自建 {@link DeferredRegister}（{@code Registries.CREATIVE_MODE_TAB}）挂载到模组事件总线，
 * 不改动 {@link ModRegistries}。注册 {@link #MAIN_TAB} 主标签页，展示 PCBcraft 全部物品：
 * PCB 设计图、PCB 方块、芯片方块、探针、示波器、焊台。
 * </p>
 * <p>
 * 图标使用 {@link ModItems#PCB_SCHEMATIC}，标题使用 {@code itemGroup.pcbcraft} 翻译键。
 * </p>
 */
public final class ModCreativeTabs {

    /** 创造模式标签页 DeferredRegister。 */
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, PCBCraft.MOD_ID);

    /** 主标签页：展示 PCBcraft 全部物品。 */
    public static final RegistryObject<CreativeModeTab> MAIN_TAB = CREATIVE_TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.pcbcraft"))
                    .icon(() -> new ItemStack(ModItems.PCB_SCHEMATIC.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.PCB_SCHEMATIC.get());
                        output.accept(ModItems.PCB_BLOCK_ITEM.get());
                        output.accept(ModItems.CHIP_BLOCK_ITEM.get());
                        output.accept(ModItems.PROBE.get());
                        output.accept(ModItems.OSCILLOSCOPE.get());
                        output.accept(ModItems.SOLDERING_IRON.get());
                    })
                    .build());

    private ModCreativeTabs() {
    }

    /**
     * 将创造标签页 DeferredRegister 注册到模组事件总线。
     * <p>在主类构造函数中、{@link ModRegistries#register} 之后调用。</p>
     *
     * @param modBusGroup 模组事件总线
     */
    public static void register(BusGroup modBusGroup) {
        CREATIVE_TABS.register(modBusGroup);
    }

    /**
     * 触发本类静态初始化，确保 {@link #MAIN_TAB} 已加入 DeferredRegister。
     * <p>在主类构造函数中、{@link #register} 之后调用，保证 RegisterEvent 触发前条目已入队。</p>
     */
    public static void init() {
    }
}
