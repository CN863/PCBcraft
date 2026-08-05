package com.pcbcraft.chip;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * 芯片终端屏幕（Task 5.1）。
 * <p>
 * 客户端 {@link Screen}，提供简易固件编辑器：多行代码区（带行号）、控制台输出区、
 * 运行/停止/保存/清空 按钮栏。键盘输入追加到代码缓冲（支持回车换行、退格、Tab 两个空格）；
 * ESC 关闭并上传当前代码。运行/停止/保存通过 {@link ChipScriptPacket} 发往服务端。
 * </p>
 * <p>
 * 控制台区当前仅显示本地操作回执；服务端固件 {@code print} 输出的实时回传留待后续阶段
 * （需服务端 → 客户端 console 包）。
 * </p>
 */
@OnlyIn(Dist.CLIENT)
public class ChipTerminalScreen extends Screen {

    private static final int HEADER = 14;
    private static final int BOTTOM_BAR = 24;
    private static final int PADDING = 6;

    private final BlockPos chipPos;
    private final StringBuilder code = new StringBuilder();
    private final List<String> console = new ArrayList<>();
    private boolean running;

    public ChipTerminalScreen(BlockPos chipPos, String script, boolean running) {
        super(Component.literal("Chip Terminal"));
        this.chipPos = chipPos;
        this.code.append(script != null ? script : "");
        this.running = running;
    }

    @Override
    protected void init() {
        super.init();
        int y = this.height - BOTTOM_BAR + 2;
        int bw = 70;
        int gap = 4;
        int total = bw * 4 + gap * 3;
        int x = (this.width - total) / 2;
        addRenderableWidget(Button.builder(Component.literal("Run"), b -> onRun())
                .bounds(x, y, bw, 16).build());
        x += bw + gap;
        addRenderableWidget(Button.builder(Component.literal("Stop"), b -> onStop())
                .bounds(x, y, bw, 16).build());
        x += bw + gap;
        addRenderableWidget(Button.builder(Component.literal("Save"), b -> onSave())
                .bounds(x, y, bw, 16).build());
        x += bw + gap;
        addRenderableWidget(Button.builder(Component.literal("Clear"), b -> clearConsole())
                .bounds(x, y, bw, 16).build());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ===== 渲染 =====

    @Override
    public void extractRenderState(GuiGraphicsExtractor gg, int mouseX, int mouseY, float partial) {
        // 半透明背景
        gg.fill(0, 0, this.width, this.height, 0xC0101010);

        // 顶部信息条
        gg.fill(0, 0, this.width, HEADER, 0xE01A1A2A);
        String status = running ? "RUNNING" : "STOPPED";
        int statusColor = running ? 0xFF55FF55 : 0xFFFF5555;
        String header = String.format("Chip @ %d,%d,%d  [%s]", chipPos.getX(), chipPos.getY(), chipPos.getZ(), status);
        gg.text(font, Component.literal(header), 4, 3, 0xFFFFFFFF);
        gg.text(font, Component.literal(status), this.width - font.width(status) - 4, 3, statusColor);

        int midY = this.height / 2;
        int codeTop = HEADER + 2;
        int codeBottom = midY - 1;
        int consTop = midY + 2;
        int consBottom = this.height - BOTTOM_BAR - 1;

        // 代码区边框 + 标题
        gg.fill(PADDING, codeTop, this.width - PADDING, codeBottom, 0x80000000);
        gg.text(font, Component.literal("Firmware (type to edit | Enter=newline | Backspace | Tab | Esc=save&close)"),
                PADDING + 2, codeTop + 1, 0xFFAAAACC);
        renderCode(gg, PADDING + 2, codeTop + 12, this.width - PADDING - 2, codeBottom - 2);

        // 控制台区边框 + 标题
        gg.fill(PADDING, consTop, this.width - PADDING, consBottom, 0x80000000);
        gg.text(font, Component.literal("Console"), PADDING + 2, consTop + 1, 0xFFAAAACC);
        renderConsole(gg, PADDING + 2, consTop + 12, this.width - PADDING - 2, consBottom - 2);

        super.extractRenderState(gg, mouseX, mouseY, partial);
    }

    /** 渲染代码区：按行显示，带行号，超出可视范围裁剪。 */
    private void renderCode(GuiGraphicsExtractor gg, int x0, int y0, int x1, int y1) {
        String[] lines = code.toString().split("\n", -1);
        int lineH = font.lineHeight + 1;
        int maxLines = Math.max(0, (y1 - y0) / lineH);
        int start = Math.max(0, lines.length - maxLines);
        int gutterW = Math.max(2, String.valueOf(lines.length).length()) * 6 + 4;
        for (int i = start; i < lines.length; i++) {
            int y = y0 + (i - start) * lineH;
            if (y + lineH > y1) {
                break;
            }
            String num = String.valueOf(i + 1);
            gg.text(font, Component.literal(num), x0, y, 0xFF888888);
            String line = lines[i];
            gg.text(font, Component.literal(line), x0 + gutterW, y, 0xFFE0E0E0);
        }
        // 末行光标提示
        if (lines.length > 0) {
            int y = y0 + (lines.length - 1 - start) * lineH;
            if (y + lineH <= y1) {
                String last = lines[lines.length - 1];
                int cx = x0 + gutterW + font.width(last);
                gg.fill(cx, y, cx + 1, y + lineH - 1, 0xFFFFFFFF);
            }
        }
    }

    /** 渲染控制台：显示最近若干行。 */
    private void renderConsole(GuiGraphicsExtractor gg, int x0, int y0, int x1, int y1) {
        int lineH = font.lineHeight + 1;
        int maxLines = Math.max(0, (y1 - y0) / lineH);
        int start = Math.max(0, console.size() - maxLines);
        for (int i = start; i < console.size(); i++) {
            int y = y0 + (i - start) * lineH;
            if (y + lineH > y1) {
                break;
            }
            gg.text(font, Component.literal(console.get(i)), x0, y, 0xFFB0FFB0);
        }
    }

    // ===== 键盘输入 =====

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        int cp = event.codepoint();
        if (cp >= ' ') {
            code.append((char) cp);
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int key = event.key();
        int scan = event.scancode();
        int mods = event.modifiers();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER) {
            code.append('\n');
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (code.length() > 0) {
                code.deleteCharAt(code.length() - 1);
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_TAB) {
            code.append("  ");
            return true;
        }
        return super.keyPressed(event);
    }

    // ===== 按钮动作 =====

    private void onRun() {
        running = true;
        send(code.toString(), true);
        appendConsole("[sent run] 固件已上传并启动");
    }

    private void onStop() {
        running = false;
        send(code.toString(), false);
        appendConsole("[sent stop] 已请求停止");
    }

    private void onSave() {
        send(code.toString(), running);
        appendConsole("[sent save] 固件已保存" + (running ? "（运行中重启）" : ""));
    }

    private void clearConsole() {
        console.clear();
    }

    private void send(String script, boolean runningFlag) {
        ChipNet.CHANNEL.send(new ChipScriptPacket(chipPos, script, runningFlag), net.minecraftforge.network.PacketDistributor.SERVER.noArg());
    }

    /** 供外部（如服务端控制台回传包）追加控制台文本。 */
    public void appendConsole(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        String[] parts = text.split("\n", -1);
        for (String p : parts) {
            console.add(p);
        }
        while (console.size() > 200) {
            console.remove(0);
        }
    }

    @Override
    public void onClose() {
        // 关闭时上传当前代码（保持运行状态）
        send(code.toString(), running);
        super.onClose();
    }
}
