package com.pcbcraft.render;

import com.pcbcraft.data.LayerType;

/**
 * PCB 图层颜色计算器（Task 3.3）。
 * <p>
 * 为 {@link LayerType} 提供基色 ARGB，并按上电状态微调亮度。
 * 配合 {@code RegisterColorHandlersEvent} 的 BlockColor tint 实现方块按层类型着色，
 * 无需为每种层单独制作纹理——所有层共用白色基础纹理，颜色由 tint 提供。
 * </p>
 * <ul>
 *   <li>铜层：{@code #B87333}（铜色）</li>
 *   <li>阻焊层：{@code #2E7D32}（阻焊绿）</li>
 *   <li>丝印层：{@code #F5F5F5}（丝印白）</li>
 *   <li>钻孔层：{@code #666666}（灰色，占位）</li>
 * </ul>
 * 上电（{@code powered=true}）时基色与白色 50% 混合以增亮，模拟通电高亮效果。
 */
public final class PcbLayerColorer {

    /** 铜层基色 RGB。 */
    private static final int COPPER = 0xB87333;
    /** 阻焊层基色 RGB。 */
    private static final int MASK = 0x2E7D32;
    /** 丝印层基色 RGB。 */
    private static final int SILK = 0xF5F5F5;
    /** 钻孔层基色 RGB（占位，钻孔层不作为可见方块）。 */
    private static final int DRILL = 0x666666;
    /** 烧毁/故障颜色 RGB（深灰，用于 master 块跳闸或元件烧毁时覆盖铜色）。 */
    private static final int BURNED = 0x2B2B2B;

    private PcbLayerColorer() {
    }

    /**
     * 返回指定图层类型与上电状态对应的 ARGB 颜色。
     *
     * @param type    图层类型
     * @param powered 是否上电
     * @return ARGB 颜色值（高 8 位为 alpha=0xFF）
     */
    public static int colorFor(LayerType type, boolean powered) {
        int base = switch (type) {
            case COPPER -> COPPER;
            case MASK -> MASK;
            case SILK -> SILK;
            case DRILL -> DRILL;
        };
        if (powered) {
            base = blendWhite(base);
        }
        return 0xFF000000 | base;
    }

    /**
     * 返回烧毁/故障颜色（深灰 ARGB），用于 master 块跳闸或元件烧毁时覆盖原铜色，
     * 提供视觉"变黑失效"提示。
     *
     * @return 烧毁颜色 ARGB（0xFF2B2B2B）
     */
    public static int burnedColor() {
        return 0xFF000000 | BURNED;
    }

    /**
     * 将基色与白色 50% 混合，实现上电增亮效果。
     *
     * @param rgb 基色 RGB
     * @return 增亮后的 RGB
     */
    private static int blendWhite(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        r = (r + 255) / 2;
        g = (g + 255) / 2;
        b = (b + 255) / 2;
        return (r << 16) | (g << 8) | b;
    }
}
