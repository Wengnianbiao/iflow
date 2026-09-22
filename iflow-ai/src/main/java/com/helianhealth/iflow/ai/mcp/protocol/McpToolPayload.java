package com.helianhealth.iflow.ai.mcp.protocol;

import com.helianhealth.iflow.common.base.ResultData;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MCP Tool 统一响应体，序列化后写入 {@link ToolContent#text}。
 *
 * <p>成功时保持 {@link ResultData} 兼容结构；失败时额外提供 {@code error} 字段，
 * 便于 Agent / SDK 在不解析嵌套 JSON 时也能直接拿到错误原因。</p>
 */
public final class McpToolPayload {

    private McpToolPayload() {
    }

    public static Map<String, Object> success(Object data) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        payload.put("code", 200);
        payload.put("message", "OK");
        payload.put("data", data);
        return payload;
    }

    public static Map<String, Object> failure(String toolName, String action, int code, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", false);
        payload.put("code", code);
        payload.put("error", message);
        payload.put("message", message);
        payload.put("tool", toolName);
        if (action != null && !action.isEmpty()) {
            payload.put("action", action);
        }
        return payload;
    }

    public static Map<String, Object> failure(String toolName, String action, String message) {
        return failure(toolName, action, -1, message);
    }
}
