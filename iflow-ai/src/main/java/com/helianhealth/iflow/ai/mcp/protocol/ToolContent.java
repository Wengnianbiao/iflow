package com.helianhealth.iflow.ai.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MCP {@code tools/call} 返回结果中的单个 content 项。
 *
 * <p>MCP 规定 content 可以是 text / image / resource 等多种类型。
 * 本框架统一使用 {@code type="text"}，{@link #text} 中放序列化后的
 * {@link McpToolPayload} JSON 字符串。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolContent {

    private String type;

    private String text;

    public static ToolContent text(String text) {
        return new ToolContent("text", text);
    }
}
