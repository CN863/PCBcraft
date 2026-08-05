package com.pcbcraft.editor;

import com.pcbcraft.PCBConfig;
import com.pcbcraft.data.ComponentInstance;
import com.pcbcraft.data.GridPoint;
import com.pcbcraft.data.Layer;
import com.pcbcraft.data.LayerType;
import com.pcbcraft.data.Net;
import com.pcbcraft.data.Pad;
import com.pcbcraft.data.PcbDesign;
import com.pcbcraft.data.Trace;
import com.pcbcraft.data.Via;
import com.pcbcraft.library.ComponentDef;
import com.pcbcraft.library.ComponentLibrary;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraft.core.registries.BuiltInRegistries;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * PCB 编辑器主屏幕（Task 2.1-2.5）。
 * <p>
 * 纯客户端 {@link Screen}，不依赖 {@code MenuType}，由 {@link EditorOpener} 通过
 * {@code Minecraft.getInstance().setScreen(...)} 打开。内部持有可变 {@link EditorState}，
 * 支持图层管理、元件放置/旋转/删除、曼哈顿布线、过孔、DRC 检查与撤销/重做。
 * </p>
 * <p>
 * 布局：左侧画布区（板边框 + 网格 + 各层内容），右侧 140px 侧栏
 * （工具栏 + 图层面板 + 元件库面板 + 错误列表），顶部 14px 信息条。
 * 坐标映射：板内网格坐标 (gx, gy) ↔ 屏幕像素，通过视口偏移 {@code panX/panY} 与
 * 缩放 {@code zoom}（每格像素数）映射，见 {@link EditorState#screenToGrid}。
 * </p>
 */
@OnlyIn(Dist.CLIENT)
public class PcbEditorScreen extends Screen {

    /** 右侧侧栏宽度（像素）。 */
    private static final int SIDEBAR_WIDTH = 140;
    /** 顶部信息条高度（像素）。 */
    private static final int HEADER_HEIGHT = 14;

    /** 编辑器状态。 */
    private final EditorState state;
    /** DRC 错误列表（运行 DRC 后填充）。 */
    private List<DrcResult.DrcError> errors = new ArrayList<>();
    /** 元件库列表滚动偏移。 */
    private int compScroll = 0;
    /** 是否处于平移拖动中。 */
    private boolean panning = false;

    /** 侧栏可点击区域列表（每帧 render 时重建，mouseClicked 时查询）。 */
    private final List<ClickRegion> clickRegions = new ArrayList<>();

    /**
     * 构造编辑器屏幕。
     *
     * @param initial 初始设计；为 null 时使用默认设计
     *                ({@code 24x16} 板，铜层数取自 {@link PCBConfig#defaultCopperLayers()})
     */
    public PcbEditorScreen(PcbDesign initial) {
        super(Component.literal("PCB Editor"));
        PcbDesign design = initial != null
                ? initial
                : PcbDesign.createDefault(24, 16, PCBConfig.defaultCopperLayers());
        this.state = new EditorState(design);
    }

    @Override
    protected void init() {
        super.init();
        state.centerViewport(this.width, this.height);
        clickRegions.clear();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ===== 渲染 =====

    @Override
    public void extractRenderState(GuiGraphicsExtractor gg, int mouseX, int mouseY, float partial) {
        // 半透明背景
        gg.fill(0, 0, this.width, this.height, 0xC0151515);

        renderCanvas(gg, mouseX, mouseY);
        renderSidebar(gg, mouseX, mouseY);
        renderHeader(gg);
        renderBottomHint(gg);
    }

    /** 渲染画布区：板背景、网格、各层内容、选区、布线预览、边框。 */
    private void renderCanvas(GuiGraphicsExtractor gg, int mouseX, int mouseY) {
        int canvasRight = this.width - SIDEBAR_WIDTH;
        gg.enableScissor(0, HEADER_HEIGHT, canvasRight, this.height);

        int ox = (int) state.getPanX();
        int oy = (int) state.getPanY();
        int z = (int) state.getZoom();
        int w = state.getDesign().getWidth();
        int h = state.getDesign().getHeight();
        int bx2 = ox + w * z;
        int by2 = oy + h * z;

        // 板背景（深绿基底）
        gg.fill(ox, oy, bx2, by2, 0xFF1A3A1A);

        // 网格
        renderGrid(gg, ox, oy, z, w, h);

        // 各可见层内容
        renderLayers(gg, ox, oy, z);

        // 选区高亮
        renderSelection(gg, ox, oy, z);

        // 布线预览
        renderRoutePreview(gg, mouseX, mouseY, ox, oy, z);

        // 板边框
        gg.fill(ox, oy, bx2, oy + 1, 0xFFFFFFFF);
        gg.fill(ox, by2 - 1, bx2, by2, 0xFFFFFFFF);
        gg.fill(ox, oy, ox + 1, by2, 0xFFFFFFFF);
        gg.fill(bx2 - 1, oy, bx2, by2, 0xFFFFFFFF);

        // 当前 activeLayer 高亮（板边框四角加亮）
        for (Layer l : state.getDesign().getLayers()) {
            if (l.getType() == LayerType.COPPER && l.getIndex() == state.getActiveLayerIndex()) {
                gg.fill(ox, oy - 1, ox + 9, oy, 0xFF80FF80);
                gg.fill(bx2 - 9, oy - 1, bx2, oy, 0xFF80FF80);
                gg.fill(ox, by2, ox + 9, by2 + 1, 0xFF80FF80);
                gg.fill(bx2 - 9, by2, bx2, by2 + 1, 0xFF80FF80);
                break;
            }
        }

        gg.disableScissor();
    }

    /** 渲染网格线，每 4 格加深。 */
    private void renderGrid(GuiGraphicsExtractor gg, int ox, int oy, int z, int w, int h) {
        for (int gx = 0; gx <= w; gx++) {
            int x = ox + gx * z;
            int color = (gx % 4 == 0) ? 0xFF3A6A3A : 0xFF2A4A2A;
            gg.fill(x, oy, x + 1, oy + h * z + 1, color);
        }
        for (int gy = 0; gy <= h; gy++) {
            int y = oy + gy * z;
            int color = (gy % 4 == 0) ? 0xFF3A6A3A : 0xFF2A4A2A;
            gg.fill(ox, y, ox + w * z + 1, y + 1, color);
        }
    }

    /** 按 design.layers 顺序渲染各可见层内容。 */
    private void renderLayers(GuiGraphicsExtractor gg, int ox, int oy, int z) {
        PcbDesign d = state.getDesign();
        for (Layer layer : d.getLayers()) {
            if (!layer.isVisible()) {
                continue;
            }
            switch (layer.getType()) {
                case MASK -> renderMaskLayer(gg, ox, oy, z, d);
                case COPPER -> renderCopperLayer(gg, layer, ox, oy, z);
                case SILK -> renderSilkLayer(gg, ox, oy, z);
                case DRILL -> { /* 钻孔层：过孔已在铜层渲染，此处无额外内容 */ }
            }
        }
    }

    /** 阻焊层：板面绿色半透明覆盖。 */
    private void renderMaskLayer(GuiGraphicsExtractor gg, int ox, int oy, int z, PcbDesign d) {
        gg.fill(ox, oy, ox + d.getWidth() * z, oy + d.getHeight() * z, 0x301F6B2E);
    }

    /** 铜层：走线（铜色 #B87333）+ 焊盘（金色）+ 过孔（深色）。 */
    private void renderCopperLayer(GuiGraphicsExtractor gg, Layer layer, int ox, int oy, int z) {
        int li = layer.getIndex();
        // 走线
        for (Trace t : state.getDesign().getTraces()) {
            if (t.getLayerIndex() != li) {
                continue;
            }
            renderTrace(gg, t, ox, oy, z);
        }
        // 焊盘
        for (ComponentInstance c : state.getDesign().getComponents()) {
            for (Pad p : c.getPads()) {
                if (p.getLayerIndex() != li) {
                    continue;
                }
                renderPad(gg, p, ox, oy, z);
            }
        }
        // 过孔（连接此铜层时渲染）
        for (Via v : state.getDesign().getVias()) {
            if (v.getConnectedLayers().contains(li)) {
                renderVia(gg, v, ox, oy, z);
            }
        }
    }

    /** 渲染单条走线（铜色粗线）。 */
    private void renderTrace(GuiGraphicsExtractor gg, Trace t, int ox, int oy, int z) {
        List<GridPoint> path = t.getPath();
        int thickness = Math.max(2, z / 6);
        int color = 0xFFB87333;
        for (int i = 1; i < path.size(); i++) {
            GridPoint a = path.get(i - 1);
            GridPoint b = path.get(i);
            int x1 = ox + a.x() * z + z / 2;
            int y1 = oy + a.y() * z + z / 2;
            int x2 = ox + b.x() * z + z / 2;
            int y2 = oy + b.y() * z + z / 2;
            drawThickLine(gg, x1, y1, x2, y2, thickness, color);
        }
    }

    /** 渲染焊盘（金色方块，居中于网格单元）。 */
    private void renderPad(GuiGraphicsExtractor gg, Pad p, int ox, int oy, int z) {
        int cx = ox + p.getPos().x() * z + z / 2;
        int cy = oy + p.getPos().y() * z + z / 2;
        int sz = Math.max(3, z / 3);
        gg.fill(cx - sz / 2, cy - sz / 2, cx + sz / 2 + 1, cy + sz / 2 + 1, 0xFFFFD700);
    }

    /** 渲染过孔（深色方块，居中于网格单元）。 */
    private void renderVia(GuiGraphicsExtractor gg, Via v, int ox, int oy, int z) {
        int cx = ox + v.getPos().x() * z + z / 2;
        int cy = oy + v.getPos().y() * z + z / 2;
        int sz = Math.max(3, z / 3);
        gg.fill(cx - sz / 2, cy - sz / 2, cx + sz / 2 + 1, cy + sz / 2 + 1, 0xFF222222);
    }

    /** 丝印层：元件位号（白色文字）。 */
    private void renderSilkLayer(GuiGraphicsExtractor gg, int ox, int oy, int z) {
        for (ComponentInstance c : state.getDesign().getComponents()) {
            int x = ox + c.getOrigin().x() * z + 2;
            int y = oy + c.getOrigin().y() * z + 2;
            gg.text(font, Component.literal(c.getDesignator()), x, y, 0xFFFFFFFF, false);
        }
    }

    /** 渲染选中元件的红色边框。 */
    private void renderSelection(GuiGraphicsExtractor gg, int ox, int oy, int z) {
        String sel = state.getSelectedDesignator();
        if (sel == null) {
            return;
        }
        for (ComponentInstance c : state.getDesign().getComponents()) {
            if (!sel.equals(c.getDesignator())) {
                continue;
            }
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
            for (Pad p : c.getPads()) {
                minX = Math.min(minX, p.getPos().x());
                minY = Math.min(minY, p.getPos().y());
                maxX = Math.max(maxX, p.getPos().x());
                maxY = Math.max(maxY, p.getPos().y());
            }
            minX = Math.min(minX, c.getOrigin().x());
            minY = Math.min(minY, c.getOrigin().y());
            maxX = Math.max(maxX, c.getOrigin().x());
            maxY = Math.max(maxY, c.getOrigin().y());
            int x1 = ox + minX * z - 1;
            int y1 = oy + minY * z - 1;
            int x2 = ox + (maxX + 1) * z;
            int y2 = oy + (maxY + 1) * z;
            gg.fill(x1, y1, x2, y1 + 1, 0xFFFF0000);
            gg.fill(x1, y2 - 1, x2, y2, 0xFFFF0000);
            gg.fill(x1, y1, x1 + 1, y2, 0xFFFF0000);
            gg.fill(x2 - 1, y1, x2, y2, 0xFFFF0000);
            break;
        }
    }

    /** ROUTE 工具下，若已点起点，渲染到鼠标位置的曼哈顿预览（半透明铜色）。 */
    private void renderRoutePreview(GuiGraphicsExtractor gg, int mouseX, int mouseY, int ox, int oy, int z) {
        if (state.getCurrentTool() != EditorTool.ROUTE) {
            return;
        }
        List<GridPoint> pts = state.getRoutePoints();
        if (pts.isEmpty()) {
            return;
        }
        GridPoint start = pts.get(0);
        GridPoint end = state.screenToGrid(mouseX, mouseY);
        int x1 = ox + start.x() * z + z / 2;
        int y1 = oy + start.y() * z + z / 2;
        int xm = ox + end.x() * z + z / 2;
        int ym = y1;
        int x2 = xm;
        int y2 = oy + end.y() * z + z / 2;
        int thickness = Math.max(2, z / 6);
        int color = 0x80B87333;
        drawThickLine(gg, x1, y1, xm, ym, thickness, color);
        drawThickLine(gg, xm, ym, x2, y2, thickness, color);
    }

    /** 绘制水平/垂直粗线段。 */
    private void drawThickLine(GuiGraphicsExtractor gg, int x1, int y1, int x2, int y2, int t, int color) {
        if (y1 == y2) {
            int minX = Math.min(x1, x2);
            int maxX = Math.max(x1, x2);
            gg.fill(minX, y1 - t / 2, maxX + 1, y1 + t / 2 + 1, color);
        } else if (x1 == x2) {
            int minY = Math.min(y1, y2);
            int maxY = Math.max(y1, y2);
            gg.fill(x1 - t / 2, minY, x1 + t / 2 + 1, maxY + 1, color);
        }
    }

    /** 渲染顶部信息条：设计名、当前工具、active layer、旋转。 */
    private void renderHeader(GuiGraphicsExtractor gg) {
        gg.fill(0, 0, this.width, HEADER_HEIGHT, 0xE0101010);
        String activeLayerName = "?";
        for (Layer l : state.getDesign().getLayers()) {
            if (l.getIndex() == state.getActiveLayerIndex()) {
                activeLayerName = l.getName();
                break;
            }
        }
        String header = String.format("%s  |  Tool: %s  |  Layer: %s  |  Rot: %d°",
                state.getDesign().getName(), state.getCurrentTool().name(),
                activeLayerName, state.getRotation());
        gg.text(font, Component.literal(header), 4, 3, 0xFFFFFFFF);
    }

    /** 渲染底部工具提示。 */
    private void renderBottomHint(GuiGraphicsExtractor gg) {
        String hint = switch (state.getCurrentTool()) {
            case SELECT -> "左键: 选择元件 | R: 旋转 | Del: 删除选中";
            case PLACE -> "左键: 放置 "
                    + (state.getSelectedComponentId() != null ? state.getSelectedComponentId() : "(先在右侧选元件)")
                    + " | R: 旋转";
            case ROUTE -> state.getRoutePoints().isEmpty() ? "左键: 点击起点" : "左键: 点击终点完成布线";
            case VIA -> "左键: 在走线点上放置过孔";
            case DELETE -> "左键: 删除元件/走线/过孔";
            case DRC -> "左键: 运行 DRC | G: 编译";
        };
        gg.text(font, Component.literal(hint), 4, this.height - 10, 0xFFAAAAAA, false);
    }

    /** 渲染右侧侧栏：工具栏 + 图层面板 + 元件库面板 + 错误列表，并重建点击区域。 */
    private void renderSidebar(GuiGraphicsExtractor gg, int mouseX, int mouseY) {
        int sx = this.width - SIDEBAR_WIDTH;
        gg.fill(sx, 0, this.width, this.height, 0xE0202020);
        gg.fill(sx, 0, sx + 1, this.height, 0xFF404040);

        clickRegions.clear();
        int x = sx + 4;
        int w = SIDEBAR_WIDTH - 8;
        int y = 4;

        // 工具栏
        y = drawSectionHeader(gg, x, y, w, "Tools");
        EditorTool[] tools = EditorTool.values();
        for (int i = 0; i < tools.length; i++) {
            EditorTool t = tools[i];
            boolean active = state.getCurrentTool() == t;
            int bg = active ? 0xFF404080 : 0xFF303030;
            gg.fill(x, y, x + w, y + 12, bg);
            String label = (i + 1) + ". " + t.name();
            gg.text(font, Component.literal(label), x + 2, y + 2, 0xFFFFFFFF, false);
            clickRegions.add(new ClickRegion(x, y, w, 12, "tool:" + t.name()));
            y += 13;
        }
        y += 3;

        // 图层面板
        y = drawSectionHeader(gg, x, y, w, "Layers");
        for (Layer l : state.getDesign().getLayers()) {
            boolean isActive = l.getType() == LayerType.COPPER
                    && l.getIndex() == state.getActiveLayerIndex();
            String vis = l.isVisible() ? "✓" : "·";
            String act = isActive ? "●" : " ";
            String label = vis + act + " " + l.getName();
            int color = l.isVisible() ? 0xFFFFFFFF : 0xFF888888;
            if (isActive) {
                gg.fill(x, y, x + w, y + 12, 0xFF30304A);
            }
            gg.text(font, Component.literal(label), x + 2, y + 2, color, false);
            clickRegions.add(new ClickRegion(x, y, w, 12, "layer:" + l.getIndex()));
            y += 13;
        }
        y += 3;

        // 元件库面板
        y = drawSectionHeader(gg, x, y, w, "Components");
        ComponentLibrary lib = ComponentLibrary.get();
        if (lib == null) {
            gg.text(font, Component.literal("(库未加载)"), x + 2, y + 2, 0xFF888888, false);
            y += 14;
        } else {
            List<ComponentDef> all = new ArrayList<>(lib.all());
            all.sort(Comparator.comparing(ComponentDef::getCategory)
                    .thenComparing(ComponentDef::getName));
            int rowH = 12;
            int errorsSectionHeight = 14 + 11 * 8 + 4;
            int compAreaBottom = this.height - errorsSectionHeight - 4;
            int visibleRows = Math.max(0, (compAreaBottom - y) / (rowH + 1));
            int maxScroll = Math.max(0, all.size() - visibleRows);
            if (compScroll > maxScroll) {
                compScroll = maxScroll;
            }
            if (compScroll < 0) {
                compScroll = 0;
            }
            int end = Math.min(all.size(), compScroll + visibleRows);
            for (int i = compScroll; i < end; i++) {
                ComponentDef def = all.get(i);
                boolean selected = def.getId().equals(state.getSelectedComponentId());
                int bg = selected ? 0xFF404080 : 0xFF2A2A2A;
                gg.fill(x, y, x + w, y + rowH, bg);
                String label = "[" + def.getCategory() + "] " + def.getName();
                gg.text(font, Component.literal(label), x + 2, y + 2, 0xFFFFFFFF, false);
                clickRegions.add(new ClickRegion(x, y, w, rowH, "comp:" + def.getId()));
                y += rowH + 1;
            }
            if (maxScroll > 0) {
                gg.text(font, Component.literal("scroll " + compScroll + "/" + maxScroll),
                        x, y, 0xFF888888, false);
                y += 11;
            }
        }
        y += 3;

        // 错误列表
        y = drawSectionHeader(gg, x, y, w, "Errors (" + errors.size() + ")");
        int errShown = 0;
        for (DrcResult.DrcError e : errors) {
            if (errShown >= 8) {
                break;
            }
            String tag = "fatal".equals(e.severity()) ? "[F]" : "[W]";
            int color = "fatal".equals(e.severity()) ? 0xFFFF5555 : 0xFFFFFF55;
            String msg = e.message();
            int maxW = w - font.width(tag);
            while (font.width(msg) > maxW && msg.length() > 1) {
                msg = msg.substring(0, msg.length() - 1);
            }
            if (font.width(msg) > maxW) {
                msg = "";
            }
            gg.text(font, Component.literal(tag + msg), x, y, color, false);
            y += 11;
            errShown++;
        }
    }

    /** 绘制侧栏分节标题，返回下一行 y。 */
    private int drawSectionHeader(GuiGraphicsExtractor gg, int x, int y, int w, String title) {
        gg.fill(x, y, x + w, y + 12, 0xFF1A1A1A);
        gg.text(font, Component.literal(title), x + 2, y + 2, 0xFFAAAAFF, false);
        return y + 13;
    }

    // ===== 鼠标输入 =====

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean isFromKeyboard) {
        double mx = event.x();
        double my = event.y();
        int button = event.button();
        int imx = (int) mx;
        int imy = (int) my;

        // 侧栏
        if (imx >= this.width - SIDEBAR_WIDTH) {
            for (ClickRegion r : clickRegions) {
                if (r.hit(imx, imy)) {
                    handleSidebarAction(r.action, button);
                    return true;
                }
            }
            return super.mouseClicked(event, isFromKeyboard);
        }

        // 顶部信息条
        if (imy < HEADER_HEIGHT) {
            return super.mouseClicked(event, isFromKeyboard);
        }

        // 画布：右键/中键开始平移
        if (button == 1 || button == 2) {
            panning = true;
            return true;
        }

        // 左键：分发到当前工具
        handleCanvasClick(mx, my);
        return true;
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dragX, double dragY) {
        double mx = event.x();
        double my = event.y();
        int button = event.button();
        if (panning) {
            state.panBy(dragX, dragY);
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        double mx = event.x();
        double my = event.y();
        int button = event.button();
        if (button == 1 || button == 2) {
            panning = false;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        // 侧栏滚动元件库
        if (mx >= this.width - SIDEBAR_WIDTH) {
            if (scrollY > 0) {
                compScroll = Math.max(0, compScroll - 1);
            } else if (scrollY < 0) {
                compScroll++;
            }
            return true;
        }
        // 画布缩放（围绕鼠标点）
        double oldZoom = state.getZoom();
        double newZoom = oldZoom * (1.0 + scrollY * 0.1);
        newZoom = Math.max(4.0, Math.min(32.0, newZoom));
        if (newZoom != oldZoom) {
            double factor = newZoom / oldZoom;
            double newPanX = mx - (mx - state.getPanX()) * factor;
            double newPanY = my - (my - state.getPanY()) * factor;
            state.setZoom(newZoom);
            state.setPan(newPanX, newPanY);
        }
        return true;
    }

    // ===== 键盘输入 =====

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int key = event.key();
        int scan = event.scancode();
        int mods = event.modifiers();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        if (event.hasControlDown()) {
            if (key == GLFW.GLFW_KEY_Z) {
                state.undo();
                return true;
            }
            if (key == GLFW.GLFW_KEY_Y) {
                state.redo();
                return true;
            }
        }
        switch (key) {
            case GLFW.GLFW_KEY_R -> {
                state.addRotation(90);
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                deleteSelected();
                return true;
            }
            case GLFW.GLFW_KEY_1 -> { setTool(EditorTool.SELECT); return true; }
            case GLFW.GLFW_KEY_2 -> { setTool(EditorTool.PLACE); return true; }
            case GLFW.GLFW_KEY_3 -> { setTool(EditorTool.ROUTE); return true; }
            case GLFW.GLFW_KEY_4 -> { setTool(EditorTool.VIA); return true; }
            case GLFW.GLFW_KEY_5 -> { setTool(EditorTool.DELETE); return true; }
            case GLFW.GLFW_KEY_6 -> { setTool(EditorTool.DRC); runDrc(); return true; }
            case GLFW.GLFW_KEY_L -> { state.cycleActiveCopperLayer(); return true; }
            case GLFW.GLFW_KEY_G -> { compileAndReport(); return true; }
        }
        return super.keyPressed(event);
    }

    // ===== 工具切换 =====

    /** 切换工具，离开 ROUTE 时清空布线累积点。 */
    private void setTool(EditorTool t) {
        if (state.getCurrentTool() == EditorTool.ROUTE && t != EditorTool.ROUTE) {
            state.getRoutePoints().clear();
        }
        state.setCurrentTool(t);
    }

    // ===== 侧栏动作分发 =====

    private void handleSidebarAction(String action, int button) {
        if (action.startsWith("tool:")) {
            EditorTool t = EditorTool.valueOf(action.substring(5));
            setTool(t);
            if (t == EditorTool.DRC) {
                runDrc();
            }
            return;
        }
        if (action.startsWith("layer:")) {
            int idx = Integer.parseInt(action.substring(6));
            for (int i = 0; i < state.getDesign().getLayers().size(); i++) {
                Layer l = state.getDesign().getLayers().get(i);
                if (l.getIndex() == idx) {
                    if (l.getType() == LayerType.COPPER) {
                        // 铜层：设为 active 并确保可见
                        state.setActiveLayerIndex(idx);
                        if (!l.isVisible()) {
                            state.getDesign().getLayers().set(i, l.withVisible(true));
                        }
                    } else {
                        // 非铜层：切换可见性
                        state.getDesign().getLayers().set(i, l.withVisible(!l.isVisible()));
                    }
                    break;
                }
            }
            return;
        }
        if (action.startsWith("comp:")) {
            String id = action.substring(5);
            state.setSelectedComponentId(id);
            setTool(EditorTool.PLACE);
            return;
        }
    }

    // ===== 画布点击分发 =====

    private void handleCanvasClick(double mx, double my) {
        GridPoint g = state.screenToGrid(mx, my);
        PcbDesign d = state.getDesign();
        // 越界点击忽略
        if (g.x() < 0 || g.y() < 0 || g.x() >= d.getWidth() || g.y() >= d.getHeight()) {
            return;
        }
        switch (state.getCurrentTool()) {
            case SELECT -> handleSelect(g);
            case PLACE -> handlePlace(g);
            case ROUTE -> handleRoute(g);
            case VIA -> handleVia(g);
            case DELETE -> handleDelete(g);
            case DRC -> runDrc();
        }
    }

    /** SELECT：点中元件→选中（高亮）。 */
    private void handleSelect(GridPoint g) {
        ComponentInstance c = findComponentAt(g);
        state.setSelectedDesignator(c != null ? c.getDesignator() : null);
    }

    /** PLACE：放置元件，焊盘与已有焊盘重叠则取消。 */
    private void handlePlace(GridPoint g) {
        String id = state.getSelectedComponentId();
        if (id == null) {
            return;
        }
        ComponentLibrary lib = ComponentLibrary.get();
        if (lib == null) {
            return;
        }
        ComponentDef def = lib.get(id);
        if (def == null) {
            return;
        }
        String designator = ComponentPlacement.nextDesignator(state.getDesign(), def.getCategory());
        ComponentInstance ci = ComponentPlacement.place(def, g, state.getRotation(), designator);

        // 检测焊盘与已有焊盘重叠
        Set<GridPoint> existingPads = new HashSet<>();
        for (ComponentInstance c : state.getDesign().getComponents()) {
            for (Pad p : c.getPads()) {
                existingPads.add(p.getPos());
            }
        }
        for (Pad p : ci.getPads()) {
            if (existingPads.contains(p.getPos())) {
                return; // 重叠，取消放置
            }
        }

        state.pushUndo();
        state.getDesign().getComponents().add(ci);
    }

    /** ROUTE：累积点击点；两点→生成曼哈顿路径（先水平后垂直）写 Trace。 */
    private void handleRoute(GridPoint g) {
        List<GridPoint> pts = state.getRoutePoints();
        if (pts.isEmpty()) {
            pts.add(g);
            return;
        }
        GridPoint start = pts.get(0);
        // 曼哈顿路径：先水平后垂直
        List<GridPoint> path = new ArrayList<>();
        path.add(start);
        if (start.x() != g.x() && start.y() != g.y()) {
            path.add(new GridPoint(g.x(), start.y()));
        }
        path.add(g);

        String netName = nextNetName();
        int width = PCBConfig.drcMinTraceWidth();
        Trace trace = new Trace(state.getActiveLayerIndex(), path, width, netName);
        state.pushUndo();
        state.getDesign().getTraces().add(trace);

        // 同步创建 Net（含两端节点）
        Set<GridPoint> nodes = new LinkedHashSet<>();
        nodes.add(start);
        nodes.add(g);
        state.getDesign().getNets().add(new Net(netName, Net.ElectricalType.SIGNAL, nodes));

        pts.clear();
    }

    /** VIA：点击走线点→放 Via，连接所有铜层。 */
    private void handleVia(GridPoint g) {
        Set<Integer> copperLayers = new TreeSet<>();
        for (Layer l : state.getDesign().getLayers()) {
            if (l.getType() == LayerType.COPPER) {
                copperLayers.add(l.getIndex());
            }
        }
        Via via = new Via(g, PCBConfig.drcMinViaHole(), copperLayers);
        state.pushUndo();
        state.getDesign().getVias().add(via);
    }

    /** DELETE：点击元件/走线/过孔→移除。 */
    private void handleDelete(GridPoint g) {
        ComponentInstance c = findComponentAt(g);
        if (c != null) {
            state.pushUndo();
            state.getDesign().getComponents().remove(c);
            if (c.getDesignator().equals(state.getSelectedDesignator())) {
                state.setSelectedDesignator(null);
            }
            return;
        }
        Trace t = findTraceAt(g);
        if (t != null) {
            state.pushUndo();
            state.getDesign().getTraces().remove(t);
            return;
        }
        Via v = findViaAt(g);
        if (v != null) {
            state.pushUndo();
            state.getDesign().getVias().remove(v);
        }
    }

    /** 删除当前选中的元件（DELETE 键）。 */
    private void deleteSelected() {
        String sel = state.getSelectedDesignator();
        if (sel == null) {
            return;
        }
        ComponentInstance toRemove = null;
        for (ComponentInstance c : state.getDesign().getComponents()) {
            if (sel.equals(c.getDesignator())) {
                toRemove = c;
                break;
            }
        }
        if (toRemove != null) {
            state.pushUndo();
            state.getDesign().getComponents().remove(toRemove);
            state.setSelectedDesignator(null);
        }
    }

    // ===== DRC 与编译 =====

    /** 运行 DRC 检查，结果填入 errors。 */
    private void runDrc() {
        DrcResult result = DrcChecker.check(state.getDesign());
        errors = result.errors();
    }

    /** G 键：先 DRC，若无 fatal 错误则调用编译钩子。 */
    private void compileAndReport() {
        DrcResult result = DrcChecker.check(state.getDesign());
        errors = result.errors();
        if (!result.fatal()) {
            PcbCompilerHook.compile(state.getDesign());
        }
    }

    // ===== 命中检测 =====

    private ComponentInstance findComponentAt(GridPoint g) {
        for (ComponentInstance c : state.getDesign().getComponents()) {
            if (c.getOrigin().equals(g)) {
                return c;
            }
            for (Pad p : c.getPads()) {
                if (p.getPos().equals(g)) {
                    return c;
                }
            }
        }
        return null;
    }

    private Trace findTraceAt(GridPoint g) {
        for (Trace t : state.getDesign().getTraces()) {
            List<GridPoint> path = t.getPath();
            for (int i = 1; i < path.size(); i++) {
                if (onSegment(path.get(i - 1), path.get(i), g)) {
                    return t;
                }
            }
        }
        return null;
    }

    private boolean onSegment(GridPoint a, GridPoint b, GridPoint g) {
        int minX = Math.min(a.x(), b.x());
        int maxX = Math.max(a.x(), b.x());
        int minY = Math.min(a.y(), b.y());
        int maxY = Math.max(a.y(), b.y());
        if (g.x() < minX || g.x() > maxX || g.y() < minY || g.y() > maxY) {
            return false;
        }
        // 仅水平/垂直段
        return a.x() == b.x() || a.y() == b.y();
    }

    private Via findViaAt(GridPoint g) {
        for (Via v : state.getDesign().getVias()) {
            if (v.getPos().equals(g)) {
                return v;
            }
        }
        return null;
    }

    // ===== 辅助 =====

    /** 生成下一个网络名 N1/N2...（取现有最大 N<number> + 1）。 */
    private String nextNetName() {
        int max = 0;
        for (Net n : state.getDesign().getNets()) {
            String name = n.getName();
            if (name.startsWith("N")) {
                try {
                    int v = Integer.parseInt(name.substring(1));
                    if (v > max) {
                        max = v;
                    }
                } catch (NumberFormatException ignored) {
                    // 非数字后缀跳过
                }
            }
        }
        return "N" + (max + 1);
    }

    @Override
    public void onClose() {
        saveDesignToItem();
        super.onClose();
    }

    /** 关闭时将工作设计写回主手 pcb_schematic 物品 NBT（若主手确为该物品）。 */
    private void saveDesignToItem() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        ItemStack stack = mc.player.getMainHandItem();
        if (stack.isEmpty()) {
            return;
        }
        Identifier key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (key == null || !key.equals(EditorOpener.SCHEMATIC_ITEM_ID)) {
            return;
        }
        CustomData d = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = d != null ? d.copyTag() : new CompoundTag();
        tag.put("Design", state.getDesign().save());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    /** 侧栏可点击区域。 */
    private static final class ClickRegion {
        final int x;
        final int y;
        final int w;
        final int h;
        final String action;

        ClickRegion(int x, int y, int w, int h, String action) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.action = action;
        }

        boolean hit(int mx, int my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }
}
