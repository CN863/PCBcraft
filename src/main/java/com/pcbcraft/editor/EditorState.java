package com.pcbcraft.editor;

import com.pcbcraft.data.GridPoint;
import com.pcbcraft.data.Layer;
import com.pcbcraft.data.LayerType;
import com.pcbcraft.data.PcbDesign;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * PCB 编辑器可变状态：持有工作 {@link PcbDesign} 副本与全部交互状态。
 * <p>
 * 每次修改设计前调用 {@link #pushUndo()} 将当前设计深拷贝压入撤销栈；
 * {@link #undo()} / {@link #redo()} 在两栈间交换设计。
 * 视口由 {@code panX/panY/zoom} 描述，{@code zoom} 为每格像素数；
 * {@link #screenToGrid(double, double)} / {@link #gridToScreen(GridPoint)} 完成坐标映射。
 * </p>
 */
public final class EditorState {

    /** 撤销/重做栈最大容量。 */
    public static final int UNDO_LIMIT = 50;

    /** 当前工作设计（可变）。 */
    private PcbDesign design;
    /** 当前工具。 */
    private EditorTool currentTool = EditorTool.SELECT;
    /** PLACE 工具下选中的元件库定义 id。 */
    private String selectedComponentId;
    /** SELECT 工具下选中的已放置元件位号。 */
    private String selectedDesignator;
    /** 当前可编辑铜层索引（默认 0=顶层铜）。 */
    private int activeLayerIndex = 0;
    /** 放置旋转角度（0/90/180/270）。 */
    private int rotation = 0;
    /** 视口：网格原点 (0,0) 在屏幕上的 x 像素。 */
    private double panX;
    /** 视口：网格原点 (0,0) 在屏幕上的 y 像素。 */
    private double panY;
    /** 视口：每格像素数。 */
    private double zoom = 16.0;
    /** 布线进行中的累积点列表。 */
    private final List<GridPoint> routePoints = new ArrayList<>();
    /** 撤销栈（栈顶为最近状态）。 */
    private final Deque<PcbDesign> undoStack = new ArrayDeque<>();
    /** 重做栈。 */
    private final Deque<PcbDesign> redoStack = new ArrayDeque<>();

    /**
     * 构造编辑器状态。
     *
     * @param design 初始设计（不为 null）
     */
    public EditorState(PcbDesign design) {
        this.design = design;
    }

    public PcbDesign getDesign() {
        return design;
    }

    public void setDesign(PcbDesign design) {
        this.design = design;
    }

    public EditorTool getCurrentTool() {
        return currentTool;
    }

    public void setCurrentTool(EditorTool tool) {
        this.currentTool = tool;
    }

    public String getSelectedComponentId() {
        return selectedComponentId;
    }

    public void setSelectedComponentId(String id) {
        this.selectedComponentId = id;
    }

    public String getSelectedDesignator() {
        return selectedDesignator;
    }

    public void setSelectedDesignator(String designator) {
        this.selectedDesignator = designator;
    }

    public int getActiveLayerIndex() {
        return activeLayerIndex;
    }

    public void setActiveLayerIndex(int index) {
        this.activeLayerIndex = index;
    }

    public int getRotation() {
        return rotation;
    }

    public void setRotation(int rotation) {
        this.rotation = ((rotation % 360) + 360) % 360;
    }

    /** 增加旋转角度（自动归一到 0..359）。 */
    public void addRotation(int deg) {
        setRotation(this.rotation + deg);
    }

    public double getPanX() {
        return panX;
    }

    public double getPanY() {
        return panY;
    }

    public double getZoom() {
        return zoom;
    }

    public void setZoom(double zoom) {
        this.zoom = zoom;
    }

    public void setPan(double x, double y) {
        this.panX = x;
        this.panY = y;
    }

    /** 平移视口（屏幕像素增量）。 */
    public void panBy(double dx, double dy) {
        this.panX += dx;
        this.panY += dy;
    }

    public List<GridPoint> getRoutePoints() {
        return routePoints;
    }

    /**
     * 将当前设计深拷贝压入撤销栈，并清空重做栈。
     * 应在任何修改设计的操作前调用。
     */
    public void pushUndo() {
        undoStack.push(design.copy());
        while (undoStack.size() > UNDO_LIMIT) {
            undoStack.removeLast();
        }
        redoStack.clear();
    }

    /**
     * 撤销：将当前设计压入重做栈，弹出撤销栈顶作为新当前设计。
     *
     * @return true 表示撤销成功；撤销栈为空返回 false
     */
    public boolean undo() {
        if (undoStack.isEmpty()) {
            return false;
        }
        redoStack.push(design.copy());
        design = undoStack.pop();
        return true;
    }

    /**
     * 重做：将当前设计压入撤销栈，弹出重做栈顶作为新当前设计。
     *
     * @return true 表示重做成功；重做栈为空返回 false
     */
    public boolean redo() {
        if (redoStack.isEmpty()) {
            return false;
        }
        undoStack.push(design.copy());
        design = redoStack.pop();
        return true;
    }

    /**
     * 屏幕像素坐标 → 板内网格坐标（向下取整到整格）。
     *
     * @param mx 屏幕 x
     * @param my 屏幕 y
     * @return 包含该像素的网格坐标
     */
    public GridPoint screenToGrid(double mx, double my) {
        double gx = (mx - panX) / zoom;
        double gy = (my - panY) / zoom;
        return new GridPoint((int) Math.floor(gx), (int) Math.floor(gy));
    }

    /**
     * 板内网格坐标 → 屏幕像素坐标（网格点左上角像素）。
     *
     * @param p 网格坐标
     * @return 长度 2 的 double 数组 [x, y]
     */
    public double[] gridToScreen(GridPoint p) {
        return new double[]{panX + p.x() * zoom, panY + p.y() * zoom};
    }

    /**
     * 将视口居中到画布区（左侧画布、右侧侧栏宽 140、顶部信息条高 14）。
     *
     * @param screenWidth  屏幕总宽
     * @param screenHeight 屏幕总高
     */
    public void centerViewport(int screenWidth, int screenHeight) {
        int sidebarWidth = 140;
        int headerHeight = 14;
        int canvasWidth = screenWidth - sidebarWidth;
        int canvasHeight = screenHeight - headerHeight;
        panX = (canvasWidth - design.getWidth() * zoom) / 2.0;
        panY = headerHeight + (canvasHeight - design.getHeight() * zoom) / 2.0;
    }

    /**
     * 将 activeLayerIndex 切换到下一个铜层（循环）。
     * 若设计中无铜层则不动。
     */
    public void cycleActiveCopperLayer() {
        List<Integer> copperIndices = new ArrayList<>();
        for (Layer l : design.getLayers()) {
            if (l.getType() == LayerType.COPPER) {
                copperIndices.add(l.getIndex());
            }
        }
        if (copperIndices.isEmpty()) {
            return;
        }
        int idx = copperIndices.indexOf(activeLayerIndex);
        int next = idx < 0 ? 0 : (idx + 1) % copperIndices.size();
        activeLayerIndex = copperIndices.get(next);
    }
}
