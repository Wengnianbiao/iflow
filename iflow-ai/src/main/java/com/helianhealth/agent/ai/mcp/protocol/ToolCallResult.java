package com.helianhealth.agent.ai.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

/**
 * MCP {@code tools/call} 返回结构。
 *
 * <p>本框架的约定：
 * <ul>
 *   <li>无论业务成功失败，content 中始终回 1 个 {@code text} 项，内容为
 *       {@link McpToolPayload} 序列化后的 JSON 字符串。</li>
 *   <li>业务失败时 {@link #isError} 置 true，并在 {@link #error} 中重复错误摘要，
 *       便于客户端在不解析 content 时也能把原因传给大模型。</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolCallResult {

    private List<ToolContent> content;

    private Boolean isError;

    /**
     * 业务错误摘要（与 content.text 中的 error/message 一致）。
     */
    private String error;

    public static ToolCallResult ok(String text) {
        return new ToolCallResult(Collections.singletonList(ToolContent.text(text)), Boolean.FALSE, null);
    }

    public static ToolCallResult error(String text, String errorSummary) {
        return new ToolCallResult(Collections.singletonList(ToolContent.text(text)), Boolean.TRUE, errorSummary);
    }
}
