package com.pcbcraft.editor;

/**
 * PCB 编辑器工具枚举。
 * <ul>
 *   <li>{@link #SELECT}：选择/移动已有元件</li>
 *   <li>{@link #PLACE}：从元件库放置新元件</li>
 *   <li>{@link #ROUTE}：在铜层上布曼哈顿走线</li>
 *   <li>{@link #VIA}：放置过孔连接不同铜层</li>
 *   <li>{@link #DELETE}：删除元件/走线/过孔</li>
 *   <li>{@link #DRC}：运行设计规则检查</li>
 * </ul>
 */
public enum EditorTool {
    SELECT,
    PLACE,
    ROUTE,
    VIA,
    DELETE,
    DRC
}
