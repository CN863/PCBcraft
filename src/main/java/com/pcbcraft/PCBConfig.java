package com.pcbcraft;

import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * PCBcraft 配置类。
 * <p>
 * 维护 COMMON（服务端/通用）与 CLIENT（客户端）两份 {@link ForgeConfigSpec}，
 * 通过 {@link #register()} 注册到 Forge 配置体系。所有配置项均以静态字段暴露，
 * 并提供便捷 getter 以便业务代码读取当前值。
 * </p>
 * <ul>
 *   <li>COMMON：DRC 规则、仿真预算、Lua 沙盒上限、信号延迟等。</li>
 *   <li>CLIENT：渲染与视觉相关开关。</li>
 * </ul>
 */
public class PCBConfig {

    // ===== COMMON 配置 =====
    private static final ForgeConfigSpec.Builder COMMON_BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec COMMON_SPEC;

    public static final ForgeConfigSpec.IntValue DRC_MIN_TRACE_WIDTH;
    public static final ForgeConfigSpec.IntValue DRC_MIN_SPACING;
    public static final ForgeConfigSpec.IntValue DRC_MIN_VIA_HOLE;
    public static final ForgeConfigSpec.IntValue SIM_BUDGET_MICROS;
    public static final ForgeConfigSpec.IntValue SIM_TICK_INTERVAL;
    public static final ForgeConfigSpec.ConfigValue<String> SHORT_CIRCUIT_POLICY;
    public static final ForgeConfigSpec.IntValue LUA_INSTRUCTION_LIMIT_PER_TICK;
    public static final ForgeConfigSpec.IntValue LUA_MEMORY_LIMIT_KB;
    public static final ForgeConfigSpec.IntValue SIGNAL_DELAY_TICKS_PER_BLOCK;

    // ===== CLIENT 配置 =====
    private static final ForgeConfigSpec.Builder CLIENT_BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec CLIENT_SPEC;

    public static final ForgeConfigSpec.BooleanValue SHOW_SIGNAL_PARTICLES;
    public static final ForgeConfigSpec.BooleanValue VOLTAGE_HEATMAP;
    public static final ForgeConfigSpec.IntValue DEFAULT_COPPER_LAYERS;

    static {
        // ---------- COMMON ----------
        COMMON_BUILDER.comment("PCBcraft 通用配置（DRC / 仿真 / Lua 沙盒）").push("common");

        COMMON_BUILDER.comment("DRC：最小走线宽度（方块数）");
        DRC_MIN_TRACE_WIDTH = COMMON_BUILDER.defineInRange("drcMinTraceWidth", 2, 1, Integer.MAX_VALUE);

        COMMON_BUILDER.comment("DRC：最小间距（方块数）");
        DRC_MIN_SPACING = COMMON_BUILDER.defineInRange("drcMinSpacing", 2, 1, Integer.MAX_VALUE);

        COMMON_BUILDER.comment("DRC：最小过孔孔径（方块数）");
        DRC_MIN_VIA_HOLE = COMMON_BUILDER.defineInRange("drcMinViaHole", 1, 1, Integer.MAX_VALUE);

        COMMON_BUILDER.comment("单板每 tick 仿真预算（微秒）");
        SIM_BUDGET_MICROS = COMMON_BUILDER.defineInRange("simBudgetMicros", 1000, 1, Integer.MAX_VALUE);

        COMMON_BUILDER.comment("仿真每隔多少 game tick 跑一次");
        SIM_TICK_INTERVAL = COMMON_BUILDER.defineInRange("simTickInterval", 1, 1, Integer.MAX_VALUE);

        COMMON_BUILDER.comment("短路策略：trip（跳闸断电）或 limit（限流）");
        SHORT_CIRCUIT_POLICY = COMMON_BUILDER.define("shortCircuitPolicy", "trip");

        COMMON_BUILDER.comment("Lua 沙盒每 tick 指令上限");
        LUA_INSTRUCTION_LIMIT_PER_TICK = COMMON_BUILDER.defineInRange("luaInstructionLimitPerTick", 10000, 1, Integer.MAX_VALUE);

        COMMON_BUILDER.comment("Lua 沙盒内存上限（KB）");
        LUA_MEMORY_LIMIT_KB = COMMON_BUILDER.defineInRange("luaMemoryLimitKB", 256, 1, Integer.MAX_VALUE);

        COMMON_BUILDER.comment("信号每方块延迟（game tick）");
        SIGNAL_DELAY_TICKS_PER_BLOCK = COMMON_BUILDER.defineInRange("signalDelayTicksPerBlock", 2, 1, Integer.MAX_VALUE);

        COMMON_BUILDER.pop();
        COMMON_SPEC = COMMON_BUILDER.build();

        // ---------- CLIENT ----------
        CLIENT_BUILDER.comment("PCBcraft 客户端配置（渲染 / 视觉）").push("client");

        CLIENT_BUILDER.comment("是否显示信号粒子");
        SHOW_SIGNAL_PARTICLES = CLIENT_BUILDER.define("showSignalParticles", true);

        CLIENT_BUILDER.comment("是否启用电压热力图");
        VOLTAGE_HEATMAP = CLIENT_BUILDER.define("voltageHeatmap", false);

        CLIENT_BUILDER.comment("默认铜层层数");
        DEFAULT_COPPER_LAYERS = CLIENT_BUILDER.defineInRange("defaultCopperLayers", 2, 1, 16);

        CLIENT_BUILDER.pop();
        CLIENT_SPEC = CLIENT_BUILDER.build();
    }

    private PCBConfig() {
    }

    /**
     * 将 COMMON 与 CLIENT spec 注册到 Forge 配置体系。
     * 应在模组主类构造期间调用。
     */
    @SuppressWarnings("deprecation")
    public static void register() {
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, COMMON_SPEC);
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC);
    }

    // ===== 便捷 getter =====

    public static int drcMinTraceWidth() {
        return DRC_MIN_TRACE_WIDTH.get();
    }

    public static int drcMinSpacing() {
        return DRC_MIN_SPACING.get();
    }

    public static int drcMinViaHole() {
        return DRC_MIN_VIA_HOLE.get();
    }

    public static int simBudgetMicros() {
        return SIM_BUDGET_MICROS.get();
    }

    public static int simTickInterval() {
        return SIM_TICK_INTERVAL.get();
    }

    public static String shortCircuitPolicy() {
        return SHORT_CIRCUIT_POLICY.get();
    }

    public static int luaInstructionLimitPerTick() {
        return LUA_INSTRUCTION_LIMIT_PER_TICK.get();
    }

    public static int luaMemoryLimitKB() {
        return LUA_MEMORY_LIMIT_KB.get();
    }

    public static int signalDelayTicksPerBlock() {
        return SIGNAL_DELAY_TICKS_PER_BLOCK.get();
    }

    public static boolean showSignalParticles() {
        return SHOW_SIGNAL_PARTICLES.get();
    }

    public static boolean voltageHeatmap() {
        return VOLTAGE_HEATMAP.get();
    }

    public static int defaultCopperLayers() {
        return DEFAULT_COPPER_LAYERS.get();
    }
}
