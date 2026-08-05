package com.pcbcraft;

import com.pcbcraft.chip.ChipNet;
import com.pcbcraft.net.ModNet;
import com.pcbcraft.registry.ModBlockEntities;
import com.pcbcraft.registry.ModBlocks;
import com.pcbcraft.registry.ModCreativeTabs;
import com.pcbcraft.registry.ModItems;
import com.pcbcraft.registry.ModRegistries;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * PCBcraft 模组主类。
 * <p>
 * 模组 ID 为 {@code pcbcraft}，在 Minecraft 内实现 PCB 电路设计、自动生成多层方块、
 * 实时电气仿真、可编程芯片（Lua 沙盒）以及调试工具。
 * </p>
 * <p>
 * 后续阶段的注册类（方块/物品/BlockEntityType/MenuType 等）将统一在
 * {@code com.pcbcraft.registry} 包内通过 {@link #getModBusGroup()} 返回的
 * 模组事件总线以 {@code DeferredRegister} 形式完成注册。
 * </p>
 */
@Mod(PCBCraft.MOD_ID)
public class PCBCraft {

    /** 模组唯一标识，全小写，需与 mods.toml 中 modId 一致。 */
    public static final String MOD_ID = "pcbcraft";

    /** 模组共享日志器。 */
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    /** 模组事件总线引用，便于后续注册类调用。 */
    private static BusGroup modBusGroup;

    public PCBCraft() {
        modBusGroup = FMLJavaModLoadingContext.get().getModBusGroup();
        FMLCommonSetupEvent.getBus(modBusGroup).addListener(this::commonSetup);

        // 注册 COMMON / CLIENT 配置
        PCBConfig.register();

        // 注册方块 / 物品 / BlockEntity / MenuType 等 DeferredRegister
        ModRegistries.register(modBusGroup);
        // 触发持有者类静态初始化，确保 RegistryObject 在 RegisterEvent 触发前已入队
        ModBlocks.init();
        ModItems.init();
        ModBlockEntities.init();

        // 注册创造模式标签页（Phase 7）
        ModCreativeTabs.register(modBusGroup);
        ModCreativeTabs.init();

        // 注册芯片网络通道（Phase 5）
        ChipNet.register(modBusGroup);

        // 注册仿真状态同步网络通道（Phase 6：探针/示波器可视化）
        ModNet.register(modBusGroup);

        LOGGER.info("PCBcraft 主类已构造 (Phase 0 脚手架)");
    }

    /**
     * 通用初始化回调，当前阶段仅记录日志；后续阶段在此执行不需要世界上下文的初始化。
     *
     * @param event 通用初始化事件
     */
    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("PCBcraft commonSetup 触发");
    }

    /**
     * 获取模组事件总线，供后续注册类注册 DeferredRegister 等。
     *
     * @return 模组事件总线
     */
    public static BusGroup getModBusGroup() {
        return modBusGroup;
    }
}
