package com.pcbcraft.editor;

import com.pcbcraft.PCBConfig;
import com.pcbcraft.data.GridPoint;
import com.pcbcraft.data.Net;
import com.pcbcraft.data.PcbDesign;
import com.pcbcraft.data.Trace;
import com.pcbcraft.data.Via;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 设计规则检查器（Task 2.6）。
 * <p>
 * 静态 {@link #check(PcbDesign)} 对设计执行以下检查：
 * <ul>
 *   <li>最小走线宽度：{@code trace.width < drcMinTraceWidth()} → fatal，定位 path[0]</li>
 *   <li>最小间距：两条不同 net 的走线任意点对曼哈顿距离 {@code < drcMinSpacing()} → fatal</li>
 *   <li>最小过孔孔径：{@code via.holeSize < drcMinViaHole()} → fatal</li>
 *   <li>未连接网络：net.nodes 数 &lt; 2 且非电源/地 → warning</li>
 *   <li>短路网络：同一 GridPoint 出现在两个不同 net 的 nodes 中 → fatal</li>
 * </ul>
 * 任一 fatal 错误存在则 {@link DrcResult#fatal()} 为 true。
 * </p>
 */
public final class DrcChecker {

    private DrcChecker() {
    }

    /**
     * 执行 DRC 检查。
     *
     * @param design 待检设计
     * @return 检查结果
     */
    public static DrcResult check(PcbDesign design) {
        List<DrcResult.DrcError> errors = new ArrayList<>();
        int minTrace = PCBConfig.drcMinTraceWidth();
        int minSpacing = PCBConfig.drcMinSpacing();
        int minVia = PCBConfig.drcMinViaHole();

        // 1. 最小走线宽度
        for (Trace t : design.getTraces()) {
            if (t.getWidth() < minTrace) {
                GridPoint loc = t.getPath().isEmpty() ? new GridPoint(0, 0) : t.getPath().get(0);
                errors.add(new DrcResult.DrcError("fatal",
                        "走线宽度 " + t.getWidth() + " < 最小 " + minTrace, loc));
            }
        }

        // 2. 最小间距：两不同 net 走线任意点对曼哈顿距离
        DrcResult.DrcError spacingErr = checkTraceSpacing(design.getTraces(), minSpacing);
        if (spacingErr != null) {
            errors.add(spacingErr);
        }

        // 3. 最小过孔孔径
        for (Via v : design.getVias()) {
            if (v.getHoleSize() < minVia) {
                errors.add(new DrcResult.DrcError("fatal",
                        "过孔孔径 " + v.getHoleSize() + " < 最小 " + minVia, v.getPos()));
            }
        }

        // 4. 未连接网络（节点数 < 2 且非电源/地）
        for (Net n : design.getNets()) {
            if (n.getNodes().size() < 2) {
                if (n.getType() == Net.ElectricalType.POWER
                        || n.getType() == Net.ElectricalType.GROUND) {
                    continue;
                }
                GridPoint loc = n.getNodes().isEmpty()
                        ? new GridPoint(0, 0)
                        : n.getNodes().iterator().next();
                errors.add(new DrcResult.DrcError("warning",
                        "网络 " + n.getName() + " 节点数 < 2，未连接", loc));
            }
        }

        // 5. 短路：同一 GridPoint 出现在两个不同 net 的 nodes 中
        Map<GridPoint, String> nodeToNet = new HashMap<>();
        for (Net n : design.getNets()) {
            for (GridPoint g : n.getNodes()) {
                String prev = nodeToNet.put(g, n.getName());
                if (prev != null && !prev.equals(n.getName())) {
                    errors.add(new DrcResult.DrcError("fatal",
                            "短路：节点 (" + g.x() + "," + g.y() + ") 同时属于网络 "
                                    + prev + " 和 " + n.getName(), g));
                }
            }
        }

        boolean fatal = errors.stream().anyMatch(e -> "fatal".equals(e.severity()));
        return new DrcResult(fatal, errors);
    }

    /**
     * 检查两不同 net 走线之间的最小间距（曼哈顿距离）。
     * 返回第一条违规错误；无违规返回 null。
     */
    private static DrcResult.DrcError checkTraceSpacing(List<Trace> traces, int minSpacing) {
        // 预展开每条走线为整数点列表
        List<List<GridPoint>> expanded = new ArrayList<>(traces.size());
        for (Trace t : traces) {
            expanded.add(expandPath(t.getPath()));
        }
        for (int i = 0; i < traces.size(); i++) {
            Trace a = traces.get(i);
            for (int j = i + 1; j < traces.size(); j++) {
                Trace b = traces.get(j);
                if (a.getNet() != null && a.getNet().equals(b.getNet())) {
                    continue;
                }
                List<GridPoint> pa = expanded.get(i);
                List<GridPoint> pb = expanded.get(j);
                for (GridPoint g1 : pa) {
                    for (GridPoint g2 : pb) {
                        int md = Math.abs(g1.x() - g2.x()) + Math.abs(g1.y() - g2.y());
                        if (md < minSpacing) {
                            return new DrcResult.DrcError("fatal",
                                    "走线间距 " + md + " < 最小 " + minSpacing
                                            + " (net " + a.getNet() + " vs " + b.getNet() + ")", g1);
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * 将曼哈顿路径展开为所有整数网格点列表（含端点）。
     */
    private static List<GridPoint> expandPath(List<GridPoint> path) {
        List<GridPoint> out = new ArrayList<>();
        if (path.isEmpty()) {
            return out;
        }
        if (path.size() == 1) {
            out.add(path.get(0));
            return out;
        }
        for (int i = 1; i < path.size(); i++) {
            GridPoint a = path.get(i - 1);
            GridPoint b = path.get(i);
            int sx = Integer.signum(b.x() - a.x());
            int sy = Integer.signum(b.y() - a.y());
            int x = a.x();
            int y = a.y();
            out.add(new GridPoint(x, y));
            while (x != b.x() || y != b.y()) {
                if (x != b.x()) {
                    x += sx;
                } else if (y != b.y()) {
                    y += sy;
                }
                out.add(new GridPoint(x, y));
            }
        }
        return out;
    }
}
