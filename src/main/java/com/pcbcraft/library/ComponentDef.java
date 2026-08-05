package com.pcbcraft.library;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 不可变元件定义，从 {@code data/pcbcraft/components/<id>.json} 解析而来。
 * <p>
 * 一个 {@code ComponentDef} 描述元件的符号图、封装焊盘、引脚列表与仿真模型参数，
 * 由 {@link ComponentLibrary} 在资源重载时加载，供编辑器与仿真引擎共享使用。
 * 所有字段在构造后不可变；集合字段通过 {@link List#copyOf} / 不可变视图防御性拷贝。
 * </p>
 */
public final class ComponentDef {

    private final String id;
    private final String name;
    private final String category;
    private final SymbolInfo symbol;
    private final Footprint footprint;
    private final List<PinDef> pins;
    private final ComponentModel model;

    private ComponentDef(String id, String name, String category, SymbolInfo symbol,
                        Footprint footprint, List<PinDef> pins, ComponentModel model) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.symbol = symbol;
        this.footprint = footprint;
        this.pins = List.copyOf(pins);
        this.model = model;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public SymbolInfo getSymbol() {
        return symbol;
    }

    public Footprint getFootprint() {
        return footprint;
    }

    public List<PinDef> getPins() {
        return pins;
    }

    public ComponentModel getModel() {
        return model;
    }

    /**
     * 焊盘数量。
     *
     * @return 封装内焊盘总数
     */
    public int padCount() {
        return footprint.pads.size();
    }

    /**
     * 按引脚编号查询引脚名称。
     *
     * @param number 引脚编号
     * @return 引脚名称；不存在返回 {@code null}
     */
    public String pinName(int number) {
        for (PinDef p : pins) {
            if (p.number == number) {
                return p.name;
            }
        }
        return null;
    }

    /**
     * 从 JSON 对象解析元件定义。
     * <p>
     * 规范 id 由加载器以文件名推导后传入（{@code fileId}），覆盖 JSON 内的 {@code id} 字段，
     * 因此本方法不读取 JSON 的 {@code id} 字段；字段一致性校验由加载器负责。
     * </p>
     *
     * @param json   元件 JSON 根对象
     * @param fileId 由文件名推导出的规范 id（与文件名一致）
     * @return 解析后的不可变 {@link ComponentDef}
     * @throws IllegalStateException 任何必填字段缺失或格式错误时抛出，消息含文件 id，由加载器捕获记日志跳过
     */
    public static ComponentDef parse(JsonObject json, String fileId) {
        try {
            String name = requireString(json, "name", fileId);
            String category = requireString(json, "category", fileId);

            // 符号图
            JsonObject symJson = requireObject(json, "symbol", fileId);
            SymbolInfo symbol = new SymbolInfo(
                    requireString(symJson, "shape", fileId),
                    requireInt(symJson, "width", fileId),
                    requireInt(symJson, "height", fileId));

            // 封装焊盘
            JsonObject fpJson = requireObject(json, "footprint", fileId);
            JsonArray padsArr = requireArray(fpJson, "pads", fileId);
            List<PadDef> pads = new ArrayList<>();
            for (JsonElement pe : padsArr) {
                if (!pe.isJsonObject()) {
                    throw new IllegalStateException("元件 " + fileId + " 的 pads 数组包含非对象元素");
                }
                JsonObject po = pe.getAsJsonObject();
                pads.add(new PadDef(
                        requireInt(po, "pin", fileId),
                        requireInt(po, "dx", fileId),
                        requireInt(po, "dy", fileId),
                        requireInt(po, "size", fileId),
                        requireInt(po, "layer", fileId)));
            }
            Footprint footprint = new Footprint(pads);

            // 引脚
            JsonArray pinsArr = requireArray(json, "pins", fileId);
            List<PinDef> pins = new ArrayList<>();
            for (JsonElement pe : pinsArr) {
                if (!pe.isJsonObject()) {
                    throw new IllegalStateException("元件 " + fileId + " 的 pins 数组包含非对象元素");
                }
                JsonObject po = pe.getAsJsonObject();
                pins.add(new PinDef(
                        requireInt(po, "number", fileId),
                        requireString(po, "name", fileId)));
            }

            // 仿真模型
            JsonObject modelJson = requireObject(json, "model", fileId);
            String type = requireString(modelJson, "type", fileId);
            Map<String, Object> params = new LinkedHashMap<>();
            if (modelJson.has("params") && modelJson.get("params").isJsonObject()) {
                JsonObject paramsJson = modelJson.getAsJsonObject("params");
                for (Map.Entry<String, JsonElement> e : paramsJson.entrySet()) {
                    params.put(e.getKey(), toParamValue(e.getValue()));
                }
            }
            ComponentModel model = new ComponentModel(type, params);

            return new ComponentDef(fileId, name, category, symbol, footprint, pins, model);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("元件 " + fileId + " 解析失败: " + e.getMessage(), e);
        }
    }

    // ===== 解析辅助方法 =====

    private static String requireString(JsonObject json, String field, String fileId) {
        if (!json.has(field) || !json.get(field).isJsonPrimitive() || !json.get(field).getAsJsonPrimitive().isString()) {
            throw new IllegalStateException("元件 " + fileId + " 缺少字符串字段 " + field + " 或类型不正确");
        }
        return json.get(field).getAsString();
    }

    private static int requireInt(JsonObject json, String field, String fileId) {
        if (!json.has(field) || !json.get(field).isJsonPrimitive() || !json.get(field).getAsJsonPrimitive().isNumber()) {
            throw new IllegalStateException("元件 " + fileId + " 缺少整数字段 " + field + " 或类型不正确");
        }
        return json.get(field).getAsInt();
    }

    private static JsonObject requireObject(JsonObject json, String field, String fileId) {
        if (!json.has(field) || !json.get(field).isJsonObject()) {
            throw new IllegalStateException("元件 " + fileId + " 缺少对象字段 " + field);
        }
        return json.getAsJsonObject(field);
    }

    private static JsonArray requireArray(JsonObject json, String field, String fileId) {
        if (!json.has(field) || !json.get(field).isJsonArray()) {
            throw new IllegalStateException("元件 " + fileId + " 缺少数组字段 " + field);
        }
        return json.getAsJsonArray(field);
    }

    /**
     * 将 JSON 基本类型转换为 Java 值：布尔→{@link Boolean}，数字→{@link Double}，
     * 字符串→{@link String}；null 或非基本类型回退为 {@code null} 或字符串形式。
     */
    private static Object toParamValue(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonPrimitive()) {
            JsonPrimitive prim = element.getAsJsonPrimitive();
            if (prim.isBoolean()) {
                return prim.getAsBoolean();
            }
            if (prim.isNumber()) {
                return prim.getAsDouble();
            }
            if (prim.isString()) {
                return prim.getAsString();
            }
        }
        return element.toString();
    }

    // ===== 内部数据类 =====

    /** 符号图信息：形状标识与逻辑尺寸。 */
    public static final class SymbolInfo {
        private final String shape;
        private final int width;
        private final int height;

        private SymbolInfo(String shape, int width, int height) {
            this.shape = shape;
            this.width = width;
            this.height = height;
        }

        public String getShape() {
            return shape;
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }
    }

    /** 封装信息，包含焊盘列表。 */
    public static final class Footprint {
        private final List<PadDef> pads;

        private Footprint(List<PadDef> pads) {
            this.pads = List.copyOf(pads);
        }

        public List<PadDef> getPads() {
            return pads;
        }
    }

    /** 单个焊盘定义。{@code layer} 为默认所在铜层索引（0=顶层铜）。 */
    public static final class PadDef {
        private final int pin;
        private final int dx;
        private final int dy;
        private final int size;
        private final int layer;

        private PadDef(int pin, int dx, int dy, int size, int layer) {
            this.pin = pin;
            this.dx = dx;
            this.dy = dy;
            this.size = size;
            this.layer = layer;
        }

        public int getPin() {
            return pin;
        }

        public int getDx() {
            return dx;
        }

        public int getDy() {
            return dy;
        }

        public int getSize() {
            return size;
        }

        public int getLayer() {
            return layer;
        }
    }

    /** 引脚定义。 */
    public static final class PinDef {
        private final int number;
        private final String name;

        private PinDef(int number, String name) {
            this.number = number;
            this.name = name;
        }

        public int getNumber() {
            return number;
        }

        public String getName() {
            return name;
        }
    }

    /** 仿真模型定义：类型字符串与自由参数映射。 */
    public static final class ComponentModel {
        private final String type;
        private final Map<String, Object> params;

        private ComponentModel(String type, Map<String, Object> params) {
            this.type = type;
            this.params = Collections.unmodifiableMap(new LinkedHashMap<>(params));
        }

        public String getType() {
            return type;
        }

        public Map<String, Object> getParams() {
            return params;
        }
    }
}
