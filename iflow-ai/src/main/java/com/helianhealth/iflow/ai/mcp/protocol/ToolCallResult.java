package com.helianhealth.iflow.ai.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

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

    public List<ToolContent> getContent() {
        return this.content;
    }

    public Boolean getIsError() {
        return this.isError;
    }

    public String getError() {
        return this.error;
    }

    public void setContent(List<ToolContent> content) {
        this.content = content;
    }

    public void setIsError(Boolean isError) {
        this.isError = isError;
    }

    public void setError(String error) {
        this.error = error;
    }

    protected boolean canEqual(Object other) {
        return other instanceof ToolCallResult;
    }

    public ToolCallResult() {
    }

    public ToolCallResult(List<ToolContent> content, Boolean isError, String error) {
        this.content = content;
        this.isError = isError;
        this.error = error;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof ToolCallResult other)) {
            return false;
        }
        if (!other.canEqual(this)) {
            return false;
        }
        return Objects.equals(getContent(), other.getContent())
                && Objects.equals(getIsError(), other.getIsError())
                && Objects.equals(getError(), other.getError());
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                getContent(),
                getIsError(),
                getError());
    }

    @Override
    public String toString() {
        return "ToolCallResult(content=" + getContent()
                + ", isError=" + getIsError()
                + ", error=" + getError() + ")";
    }
}
