package com.pcbcraft.editor;

import com.pcbcraft.data.ComponentInstance;
import com.pcbcraft.data.GridPoint;
import com.pcbcraft.data.Pad;
import com.pcbcraft.data.PcbDesign;
import com.pcbcraft.library.ComponentDef;

import java.util.ArrayList;
import java.util.List;

/**
 * 元件放置辅助：按封装 + 旋转计算实际焊盘坐标，生成位号。
 */
public final class ComponentPlacement {

    private ComponentPlacement() {
    }

    /**
     * 按 {@code def} 的封装与 {@code rotation} 在 {@code origin} 放置元件。
     * <p>
     * 旋转变换（绕原点）：
     * <ul>
     *   <li>0°：  (dx, dy) → (dx, dy)</li>
     *   <li>90°： (dx, dy) → (-dy, dx)</li>
     *   <li>180°：(dx, dy) → (-dx, -dy)</li>
     *   <li>270°：(dx, dy) → (dy, -dx)</li>
     * </ul>
     * 旋转后加上 origin 得到每个焊盘的板内坐标。
     * </p>
     *
     * @param def        元件定义
     * @param origin     放置原点
     * @param rotation   旋转角度（0/90/180/270）
     * @param designator 位号
     * @return 新的元件实例（pads 已按旋转计算）
     */
    public static ComponentInstance place(ComponentDef def, GridPoint origin, int rotation, String designator) {
        List<Pad> pads = new ArrayList<>();
        for (ComponentDef.PadDef pd : def.getFootprint().getPads()) {
            int dx = pd.getDx();
            int dy = pd.getDy();
            int rx;
            int ry;
            switch (rotation) {
                case 90:
                    rx = -dy;
                    ry = dx;
                    break;
                case 180:
                    rx = -dx;
                    ry = -dy;
                    break;
                case 270:
                    rx = dy;
                    ry = -dx;
                    break;
                default:
                    rx = dx;
                    ry = dy;
                    break;
            }
            GridPoint pos = new GridPoint(origin.x() + rx, origin.y() + ry);
            pads.add(new Pad(pd.getPin(), pos, pd.getSize(), pd.getLayer()));
        }
        return new ComponentInstance(def.getId(), designator, origin, rotation, pads);
    }

    /**
     * 按 category 前缀 + 现有最大序号 + 1 生成下一个位号。
     * <p>
     * 前缀映射：passive→R, logic/chip/analog→U, power→V, output→D,
     * connector→J, input→S, 其他→X。
     * </p>
     *
     * @param design   当前设计
     * @param category 元件分类
     * @return 形如 R1/U3 的新位号
     */
    public static String nextDesignator(PcbDesign design, String category) {
        String prefix = prefixFor(category);
        int max = 0;
        for (ComponentInstance c : design.getComponents()) {
            String d = c.getDesignator();
            if (d != null && d.startsWith(prefix)) {
                try {
                    int v = Integer.parseInt(d.substring(prefix.length()));
                    if (v > max) {
                        max = v;
                    }
                } catch (NumberFormatException ignored) {
                    // 非数字后缀，跳过
                }
            }
        }
        return prefix + (max + 1);
    }

    private static String prefixFor(String category) {
        if (category == null) {
            return "X";
        }
        return switch (category) {
            case "passive" -> "R";
            case "logic", "chip", "analog" -> "U";
            case "power" -> "V";
            case "output" -> "D";
            case "connector" -> "J";
            case "input" -> "S";
            default -> "X";
        };
    }
}
