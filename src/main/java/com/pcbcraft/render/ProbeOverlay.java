package com.pcbcraft.render;

import com.pcbcraft.PCBCraft;
import com.pcbcraft.block.PcbBlockEntity;
import com.pcbcraft.data.PcbDesign;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * 探针读数浮窗（Task 6.1）。
 * <p>
 * 在屏幕右上角绘制最近一次探针读数：坐标 + 层 + 电压（由 {@link ClientSimState} 缓存的
 * SimStatePacket 反查）+ 网络/电流（actionbar 已显示，浮窗仅作持久提示）。波形折线留 TODO。
 * </p>
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = PCBCraft.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.FORGE)
public final class ProbeOverlay {

    /** 满量程电压，用于显示与（未来）波形纵轴。 */
    private static final double V_MAX = 5.0;

    private ProbeOverlay() {
    }

    @SubscribeEvent
    public static void onRenderGui(CustomizeGuiOverlayEvent.DebugText event) {
        ProbeClientData.Reading reading = ProbeClientData.current();
        if (reading == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        PcbDesign design = null;
        if (mc.level.getBlockEntity(reading.master) instanceof PcbBlockEntity pcb) {
            design = pcb.getDesign();
        }
        double voltage = ClientSimState.voltageAt(reading.master, reading.point, design);

        Font font = mc.font;
        String line1 = String.format("Probe @ (%d,%d) Layer %d", reading.point.x(), reading.point.y(), reading.layerY);
        String line2 = String.format("Voltage %.2f V", voltage);
        int w = Math.max(font.width(line1), font.width(line2)) + 8;
        int x = 8;
        int y = 8;
        // 半透明黑底（使用颜色常量）
        // 简化为纯文本输出，需通过其他方式渲染
        // TODO: MC 26.2 CustomizeGuiOverlayEvent 渲染需注入 GuiGraphicsExtractor
    }

    private static int voltageColor(double v) {
        if (v > 2.5) {
            return 0xFF5555;
        }
        if (v < 0.5) {
            return 0x5555FF;
        }
        return 0x55FF55;
    }
}