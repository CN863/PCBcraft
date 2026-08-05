package com.pcbcraft.editor;

import com.pcbcraft.data.GridPoint;

import java.util.List;

/**
 * DRC 检查结果。
 *
 * @param fatal  是否存在致命错误（任一 error 严重级别为 "fatal"）
 * @param errors 全部错误列表（fatal 在前，warning 其后，按检出顺序）
 */
public record DrcResult(boolean fatal, List<DrcError> errors) {

    /**
     * 单条 DRC 错误。
     *
     * @param severity 严重级别："fatal" 或 "warning"
     * @param message  人类可读描述
     * @param location 错误定位（板内网格坐标）
     */
    public record DrcError(String severity, String message, GridPoint location) {
    }
}
