package com.pcbcraft.block;

import com.pcbcraft.data.PcbDesign;
import com.pcbcraft.registry.ModBlockEntities;
import com.pcbcraft.sim.FaultModel;
import com.pcbcraft.sim.SimTickScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * PCB 方块实体。
 * <p>
 * 仅存在于 {@link PcbBlock#MASTER} 为 true 的方块上，持有整块电路板的
 * {@link PcbDesign}、仿真上电状态与最近一次仿真 tick。
 * </p>
 * <ul>
 *   <li>持久化：1.20.1 经典 {@link #save(CompoundTag)} / {@link #load(CompoundTag)} 模式，
 *       设计数据委托 {@link PcbDesign#save(CompoundTag)} / {@link PcbDesign#load(CompoundTag)}。</li>
 *   <li>客户端同步：{@link #getUpdateTag()} 返回完整 save 结果，
 *       {@link #getUpdatePacket()} 返回 {@link ClientboundBlockEntityDataPacket#create(BlockEntity)}。</li>
 * </ul>
 */
public class PcbBlockEntity extends BlockEntity {

    /** 当前 PCB 设计（仅 master 方块有意义）。 */
    private PcbDesign design;
    /** 仿真上电状态，Phase 4 仿真引擎使用。 */
    private boolean powered;
    /** 最近一次仿真执行的游戏 tick。 */
    private long lastSimTick;
    /**
     * 当前可见层索引，-1 表示显示所有层；0..copperLayerCount+1 表示仅显示该层。
     * <p>
     * TODO Phase 3.x：完整渲染隐藏需 BlockEntityRenderer 配合，当前阶段仅记录值并同步客户端。
     * </p>
     */
    private int visibleLayer = -1;
    /** 故障模型（跳闸/烧毁/温度），与 {@link SimTickScheduler} 注册表共享同一引用。 */
    private FaultModel fault;

    public PcbBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PCB_BLOCK_ENTITY.get(), pos, state);
    }

    public PcbDesign getDesign() {
        return design;
    }

    public void setDesign(PcbDesign design) {
        this.design = design;
        // 编辑器修改设计后，若已上电注册则重建仿真器（保留故障状态）
        if (this.level != null && !this.level.isClientSide() && this.powered) {
            SimTickScheduler.updateDesign(getBlockPos(), design);
        }
        setChanged();
    }

    public boolean isPowered() {
        return powered;
    }

    public void setPowered(boolean powered) {
        this.powered = powered;
        if (this.level != null && !this.level.isClientSide()) {
            if (powered) {
                SimTickScheduler.register(getBlockPos(), getDesign(), (ServerLevel) this.level);
                // 复用既有 fault（保留跳闸/烧毁状态跨上电周期），否则新建并绑定到注册表
                if (this.fault == null) {
                    this.fault = new FaultModel();
                }
                SimTickScheduler.setFault(getBlockPos(), this.fault);
            } else {
                SimTickScheduler.unregister(getBlockPos());
            }
        }
        setChanged();
    }

    /**
     * 返回故障模型（可为 {@code null}）。
     *
     * @return 故障模型
     */
    public FaultModel getFault() {
        return fault;
    }

    public long getLastSimTick() {
        return lastSimTick;
    }

    public void setLastSimTick(long lastSimTick) {
        this.lastSimTick = lastSimTick;
    }

    /**
     * 方块移除/区块卸载时从仿真调度注册表注销，停止仿真。
     */
    @Override
    public void setRemoved() {
        if (this.level != null && !this.level.isClientSide()) {
            SimTickScheduler.unregister(getBlockPos());
        }
        super.setRemoved();
    }

    /**
     * 返回当前可见层索引。-1 表示显示所有层。
     *
     * @return 可见层索引
     */
    public int getVisibleLayer() {
        return visibleLayer;
    }

    /**
     * 设置可见层索引。
     *
     * @param visibleLayer 可见层索引，-1 表示显示所有层
     */
    public void setVisibleLayer(int visibleLayer) {
        this.visibleLayer = visibleLayer;
        setChanged();
    }

    /**
     * 循环切换可见层：-1（全部）→ 0 → 1 → ... → 最大层索引 → -1。
     * <p>
     * 最大层索引 = 铜层数 + 1（阻焊 + 丝印）。设计为 null 时仅切换 -1/0。
     * </p>
     */
    public void cycleVisibleLayer() {
        int max = this.design != null ? this.design.copperLayerCount() + 1 : 0;
        this.visibleLayer = (this.visibleLayer >= max) ? -1 : this.visibleLayer + 1;
        setChanged();
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
    public void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.design = input.read("Design", CompoundTag.CODEC).map(PcbDesign::load).orElse(null);
        this.powered = input.getBooleanOr("Powered", false);
        this.lastSimTick = input.getLongOr("LastSim", 0L);
        // 兼容旧数据：无 VisibleLayer 标签时默认 -1（显示所有层）
        this.visibleLayer = input.getIntOr("VisibleLayer", -1);
        // 恢复故障模型（跳闸/烧毁/温度），跨存档保留
        this.fault = input.read("Fault", CompoundTag.CODEC).map(FaultModel::load).orElse(null);
        // 已上电且处于服务端时重新注册到仿真调度
        if (this.powered && this.level != null && !this.level.isClientSide()) {
            SimTickScheduler.register(getBlockPos(), this.design, (ServerLevel) this.level);
            if (this.fault == null) {
                this.fault = new FaultModel();
            }
            SimTickScheduler.setFault(getBlockPos(), this.fault);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (this.design != null) {
            output.store("Design", CompoundTag.CODEC, this.design.save());
        }
        output.putBoolean("Powered", this.powered);
        output.putLong("LastSim", this.lastSimTick);
        output.putInt("VisibleLayer", this.visibleLayer);
        if (this.fault != null) {
            output.store("Fault", CompoundTag.CODEC, this.fault.save());
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithFullMetadata(registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
