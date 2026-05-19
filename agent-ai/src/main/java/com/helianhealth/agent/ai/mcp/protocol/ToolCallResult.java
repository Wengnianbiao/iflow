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
 *       {@link com.helianhealth.agent.common.base.ResultData} 的 JSON 字符串。</li>
 *   <li>业务失败时 {@link #isError} 置 true，但响应体仍是正常 JSON-RPC result，
 *       让 AI 端能拿到错误明细而不是 JSON-RPC 协议级 error。</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolCallResult {

    private List<ToolContent> content;

    private Boolean isError;

    public static ToolCallResult ok(String text) {
        return new ToolCallResult(Collections.singletonList(ToolContent.text(text)), Boolean.FALSE);
    }

    public static ToolCallResult error(String text) {
        return new ToolCallResult(Collections.singletonList(ToolContent.text(text)), Boolean.TRUE);
    }
}
