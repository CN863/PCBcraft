package com.pcbcraft.chip;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 客户端专属：打开芯片终端屏幕。
 * <p>由 {@link ChipBlock#use} 通过 {@code DistExecutor} 在客户端侧调用，避免服务端加载客户端类。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class ClientChipOpener {

    private ClientChipOpener() {
    }

    /**
     * 打开指定芯片方块的终端屏幕，从客户端 BlockEntity 读取当前固件源码。
     *
     * @param pos 芯片方块坐标
     */
    public static void open(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        String script = ChipBlockEntity.DEFAULT_SCRIPT;
        boolean running = false;
        if (mc.level != null) {
            net.minecraft.world.level.block.entity.BlockEntity be = mc.level.getBlockEntity(pos);
            if (be instanceof ChipBlockEntity chip) {
                script = chip.getScript();
                running = chip.isRunning();
            }
        }
        mc.setScreenAndShow(new ChipTerminalScreen(pos, script, running));
    }
}
