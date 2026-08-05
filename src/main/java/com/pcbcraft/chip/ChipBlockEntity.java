package com.pcbcraft.chip;

import com.pcbcraft.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 可编程芯片方块实体（Task 5.1）。
 * <p>
 * 持有 Lua 固件源码 {@link #script}、运行标志 {@link #running}、绑定的 PCB master 坐标
 * {@link #boundMaster}（可为 {@code null}）、8 路引脚输出驱动电压 {@link #pinVoltages} 与
 * 8 路引脚输入电压 {@link #pinInputs}。运行时句柄 {@link #runtime} 不持久化。
 * </p>
 * <p>
 * 引脚编号约定（与 {@code data/pcbcraft/components/mcu.json} 的 MCU 封装焊盘顺序一致）：
 * <ul>
 *   <li>0 = VCC（只读感知，固件不应驱动）</li>
 *   <li>1 = GND（只读感知，固件不应驱动）</li>
 *   <li>2..7 = D0/D1/D2/D3/A0/A1（可读写 GPIO）</li>
 * </ul>
 * 实际驱动注入见 {@link ChipIoBridge#sync}，仅向 MCU 的 GPIO 节点写入，避免与电源网络冲突。
 * </p>
 */
public class ChipBlockEntity extends BlockEntity {

    /** 芯片引脚数。 */
    public static final int PIN_COUNT = 8;

    /** 默认固件：D0 引脚闪烁。 */
    public static final String DEFAULT_SCRIPT =
            "-- 默认固件：D0(引脚2)闪烁\n" +
            "while true do\n" +
            "  pin.write(2, 1)\n" +
            "  print(\"on\")\n" +
            "  sleep(10)\n" +
            "  pin.write(2, 0)\n" +
            "  print(\"off\")\n" +
            "  sleep(10)\n" +
            "end\n";

    /** 固件源码。 */
    private String script = DEFAULT_SCRIPT;
    /** 是否正在运行固件。 */
    private boolean running;
    /** 绑定的 PCB master 坐标；{@code null} 表示未绑定。 */
    private BlockPos boundMaster;
    /** 8 路引脚当前驱动电压（固件写入）。 */
    private final double[] pinVoltages = new double[PIN_COUNT];
    /** 8 路引脚读入电压（仿真回填）。 */
    private final double[] pinInputs = new double[PIN_COUNT];

    /** Lua 运行时句柄（不持久化，仅服务端有效）。 */
    private ChipRuntime runtime;

    public ChipBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CHIP_BLOCK_ENTITY.get(), pos, state);
    }

    public String getScript() {
        return script;
    }

    public void setScript(String script) {
        this.script = (script != null) ? script : "";
        setChanged();
    }

    public boolean isRunning() {
        return running;
    }

    /**
     * 设置运行状态；幂等：状态未变时不重启运行时。
     * <p>仅服务端执行注册/注销与运行时启停。</p>
     */
    public void setRunning(boolean running) {
        if (running == this.running) {
            setChanged();
            return;
        }
        this.running = running;
        if (this.level != null && !this.level.isClientSide()) {
            if (running) {
                startRuntime();
                ChipTickScheduler.register(getBlockPos(), this);
            } else {
                ChipTickScheduler.unregister(getBlockPos());
                stopRuntime();
            }
        }
        setChanged();
    }

    /** 以当前 script 重启运行时（用于运行中改写固件后立即生效）。 */
    public void restartRuntime() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        stopRuntime();
        startRuntime();
    }

    /** 构造 Lua 运行时；失败时记录日志且 runtime 留空。 */
    public void startRuntime() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        try {
            this.runtime = ChipRuntime.create(this);
        } catch (Throwable t) {
            this.runtime = null;
            com.pcbcraft.PCBCraft.LOGGER.warn("芯片@{} 运行时创建失败: {}", getBlockPos(), t.toString());
        }
    }

    /** 停止运行时并释放句柄。 */
    public void stopRuntime() {
        if (this.runtime != null) {
            this.runtime.stop();
        }
        this.runtime = null;
    }

    public ChipRuntime getRuntime() {
        return runtime;
    }

    public BlockPos getBoundMaster() {
        return boundMaster;
    }

    public void setBoundMaster(BlockPos boundMaster) {
        this.boundMaster = boundMaster;
        setChanged();
        markUpdated();
    }

    /** 返回引脚 i 的输出驱动电压。 */
    public double getPinOutput(int i) {
        if (i < 0 || i >= PIN_COUNT) {
            return 0.0;
        }
        return pinVoltages[i];
    }

    /** 设置引脚 i 的输出驱动电压（由固件 pin.write/pwm 调用）。 */
    public void setPinOutput(int i, double v) {
        if (i < 0 || i >= PIN_COUNT) {
            return;
        }
        pinVoltages[i] = v;
    }

    /** 返回引脚 i 的输入电压（由仿真回填）。 */
    public double getPinInput(int i) {
        if (i < 0 || i >= PIN_COUNT) {
            return 0.0;
        }
        return pinInputs[i];
    }

    /** 设置引脚 i 的输入电压（由 {@link ChipIoBridge} 回填）。 */
    public void setPinInput(int i, double v) {
        if (i < 0 || i >= PIN_COUNT) {
            return;
        }
        pinInputs[i] = v;
    }

    /** 标记数据已变更并通知客户端重同步。 */
    public void markUpdated() {
        setChanged();
        Level lvl = this.level;
        if (lvl != null && !lvl.isClientSide()) {
            BlockState state = getBlockState();
            lvl.sendBlockUpdated(getBlockPos(), state, state, 3);
        }
    }

    @Override
    public void setRemoved() {
        if (this.level != null && !this.level.isClientSide()) {
            ChipTickScheduler.unregister(getBlockPos());
            stopRuntime();
        }
        super.setRemoved();
    }

    @Override
    public void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.script = input.getStringOr("Script", DEFAULT_SCRIPT);
        this.running = input.getBooleanOr("Running", false);
        this.boundMaster = input.read("Bound", CompoundTag.CODEC)
                .map(t -> BlockPos.of(t.getLongOr("pos", 0L)))
                .orElse(null);
        input.read("PinOut", CompoundTag.CODEC).ifPresent(t -> loadDoubleArray(t, "values", this.pinVoltages));
        input.read("PinIn", CompoundTag.CODEC).ifPresent(t -> loadDoubleArray(t, "values", this.pinInputs));
        // 服务端且原本运行时：重新注册到芯片调度并重建运行时
        if (this.running && this.level != null && !this.level.isClientSide()) {
            this.runtime = null; // 重建
            startRuntime();
            ChipTickScheduler.register(getBlockPos(), this);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("Script", this.script);
        output.putBoolean("Running", this.running);
        if (this.boundMaster != null) {
            CompoundTag boundTag = new CompoundTag();
            boundTag.putLong("pos", this.boundMaster.asLong());
            output.store("Bound", CompoundTag.CODEC, boundTag);
        }
        CompoundTag pinOutTag = new CompoundTag();
        putDoubleArray(pinOutTag, "values", this.pinVoltages);
        output.store("PinOut", CompoundTag.CODEC, pinOutTag);
        CompoundTag pinInTag = new CompoundTag();
        putDoubleArray(pinInTag, "values", this.pinInputs);
        output.store("PinIn", CompoundTag.CODEC, pinInTag);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithFullMetadata(registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ===== NBT 辅助 =====

    private static void putDoubleArray(CompoundTag tag, String key, double[] arr) {
        ListTag list = new ListTag();
        for (double v : arr) {
            list.add(net.minecraft.nbt.DoubleTag.valueOf(v));
        }
        tag.put(key, list);
    }

    private static void loadDoubleArray(CompoundTag tag, String key, double[] arr) {
        if (!tag.contains(key)) {
            return;
        }
        ListTag list = tag.getListOrEmpty(key);
        for (int i = 0; i < arr.length && i < list.size(); i++) {
            arr[i] = list.getDoubleOr(i, 0.0);
        }
    }
}
