package com.pcbcraft.library;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pcbcraft.PCBCraft;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.Reader;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 元件库，从 datapack 加载 {@code data/pcbcraft/components/*.json}。
 * <p>
 * 继承 {@link SimplePreparableReloadListener}，使用 {@link ResourceManager} 加载 JSON 文件。
 * 重载时由 {@link LibraryEvents} 在 {@code AddReloadListenerEvent} 中注册新实例，
 * {@link #apply} 完成后于主线程将当前实例写入 {@link #instance} 静态字段供全局查询。
 * </p>
 */
public class ComponentLibrary extends SimplePreparableReloadListener<Map<Identifier, JsonElement>> {

    /** 全局单例：apply 完成后于主线程赋值，volatile 保证可见性。 */
    private static volatile ComponentLibrary instance;

    private static final FileToIdConverter CONVERTER = FileToIdConverter.json("pcbcraft/components");
    private static final Gson GSON = new Gson();

    private final Map<String, ComponentDef> components = new HashMap<>();

    public ComponentLibrary() {
    }

    @Override
    protected Map<Identifier, JsonElement> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<Identifier, JsonElement> map = new HashMap<>();
        Map<Identifier, Resource> resources = resourceManager.listResources(CONVERTER.prefix(), p -> p.getPath().endsWith(".json"));
        for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
            Identifier id = CONVERTER.fileToId(entry.getKey());
            try (Reader reader = entry.getValue().openAsReader()) {
                JsonElement element = JsonParser.parseReader(reader);
                map.put(id, element);
            } catch (Exception e) {
                PCBCraft.LOGGER.error("无法加载元件文件 {}: {}", entry.getKey(), e.getMessage());
            }
        }
        return map;
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> object, ResourceManager resourceManager, ProfilerFiller profiler) {
        components.clear();
        for (Map.Entry<Identifier, JsonElement> entry : object.entrySet()) {
            // 从 Identifier 路径提取文件名（最后一个 '/' 之后、'.json' 之前）作为规范 id
            String path = entry.getKey().getPath();
            String fileName = path.substring(path.lastIndexOf('/') + 1);
            String id = fileName.endsWith(".json")
                    ? fileName.substring(0, fileName.length() - ".json".length())
                    : fileName;

            try {
                JsonElement element = entry.getValue();
                if (element == null || !element.isJsonObject()) {
                    PCBCraft.LOGGER.error("跳过元件 {}: 根节点不是 JSON 对象", id);
                    continue;
                }
                JsonObject json = element.getAsJsonObject();

                // 若 JSON 的 id 字段与文件名不一致，记警告但以文件名为准
                if (json.has("id") && json.get("id").isJsonPrimitive() && json.get("id").getAsJsonPrimitive().isString()) {
                    String jsonId = json.get("id").getAsString();
                    if (!jsonId.equals(id)) {
                        PCBCraft.LOGGER.warn("元件 {} 的 JSON id 字段({})与文件名不一致，以文件名为准", id, jsonId);
                    }
                }

                ComponentDef def = ComponentDef.parse(json, id);
                if (components.containsKey(id)) {
                    PCBCraft.LOGGER.warn("元件 id {} 重复，后加载的定义将覆盖先前的", id);
                }
                components.put(id, def);
            } catch (Exception e) {
                PCBCraft.LOGGER.error("跳过元件 {}: {}", id, e.getMessage());
            }
        }
        PCBCraft.LOGGER.info("PCBcraft 元件库加载完成，共 {} 个元件", components.size());
        // apply 在重载完成后于主线程调用，安全地发布当前实例
        instance = this;
    }

    /**
     * 按 id 查询元件定义。
     *
     * @param id 元件 id（与文件名一致）
     * @return 元件定义；不存在返回 {@code null}
     */
    public ComponentDef get(String id) {
        return components.get(id);
    }

    /**
     * 返回全部元件定义。返回集合为内部 map 的视图，调用方不应修改。
     *
     * @return 全部元件定义
     */
    public Collection<ComponentDef> all() {
        return components.values();
    }

    /**
     * 按分类筛选元件。
     *
     * @param cat 分类名（如 passive）
     * @return 该分类下的元件列表
     */
    public List<ComponentDef> byCategory(String cat) {
        return components.values().stream()
                .filter(d -> d.getCategory().equals(cat))
                .collect(Collectors.toList());
    }

    /**
     * 判断指定 id 的元件是否存在。
     *
     * @param id 元件 id
     * @return 存在返回 true
     */
    public boolean exists(String id) {
        return components.containsKey(id);
    }

    /**
     * 获取当前已加载的元件库单例。
     *
     * @return 元件库实例；首次重载完成前为 {@code null}
     */
    public static ComponentLibrary get() {
        return instance;
    }

    /**
     * 手动设置元件库单例（主要用于测试或外部注入）。
     *
     * @param lib 元件库实例
     */
    public static void setInstance(ComponentLibrary lib) {
        instance = lib;
    }
}
