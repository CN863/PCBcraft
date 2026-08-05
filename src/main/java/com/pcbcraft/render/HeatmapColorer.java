package com.pcbcraft.render;

/**
 * 电压热力色计算器（Task 6.4）。
 * <p>
 * 将节点电压映射为蓝→青→绿→黄→红的热力色 RGB，供 {@link PcbVisualizationRender}
 * 在热力图模式下按节点电压渐变染色走线。
 * </p>
 * <p>映射规则：v/vMax 归一化到 [0,1]，按 HSL 色相 240°（蓝）→ 0°（红）线性插值，
 * 饱和度与亮度固定为 1.0/0.5，转 RGB 后返回 0xRRGGBB。</p>
 */
public final class HeatmapColorer {

    private HeatmapColorer() {
    }

    /**
     * 返回电压 v 对应的热力色 RGB。
     *
     * @param v    电压（V）
     * @param vMax 满量程电压（V），v 超出范围按端点取色
     * @return 0xRRGGBB 颜色值
     */
    public static int color(double v, double vMax) {
        double t = (vMax > 0.0) ? (v / vMax) : 0.0;
        if (t < 0.0) {
            t = 0.0;
        } else if (t > 1.0) {
            t = 1.0;
        }
        // 色相 240°(蓝) → 0°(红)
        float hue = (float) (240.0 * (1.0 - t));
        float sat = 1.0f;
        float light = 0.5f;
        int rgb = hslToRgb(hue, sat, light);
        return rgb & 0xFFFFFF;
    }

    /**
     * HSL → RGB 转换（h 单位为度，s/l ∈ [0,1]）。
     *
     * @param h 色相（度）
     * @param s 饱和度
     * @param l 亮度
     * @return 0xRRGGBB
     */
    private static int hslToRgb(float h, float s, float l) {
        float c = (1.0f - Math.abs(2.0f * l - 1.0f)) * s;
        float hp = (h % 360.0f) / 60.0f;
        if (hp < 0.0f) {
            hp += 6.0f;
        }
        float x = c * (1.0f - Math.abs((hp % 2.0f) - 1.0f));
        float r1, g1, b1;
        if (hp < 1.0f) {
            r1 = c; g1 = x; b1 = 0.0f;
        } else if (hp < 2.0f) {
            r1 = x; g1 = c; b1 = 0.0f;
        } else if (hp < 3.0f) {
            r1 = 0.0f; g1 = c; b1 = x;
        } else if (hp < 4.0f) {
            r1 = 0.0f; g1 = x; b1 = c;
        } else if (hp < 5.0f) {
            r1 = x; g1 = 0.0f; b1 = c;
        } else {
            r1 = c; g1 = 0.0f; b1 = x;
        }
        float m = l - c / 2.0f;
        int r = clamp(r1 + m);
        int g = clamp(g1 + m);
        int b = clamp(b1 + m);
        return (r << 16) | (g << 8) | b;
    }

    private static int clamp(float v) {
        int iv = Math.round(v * 255.0f);
        if (iv < 0) {
            return 0;
        }
        if (iv > 255) {
            return 255;
        }
        return iv;
    }
}
