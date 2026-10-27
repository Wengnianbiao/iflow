package com.helianhealth.iflow.ai.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Objects;

/**
 * MCP {@code tools/call} 返回结果中的单个 content 项。
 *
 * <p>MCP 规定 content 可以是 text / image / resource 等多种类型。
 * 本框架统一使用 {@code type="text"}，{@link #text} 中放序列化后的
 * {@link McpToolPayload} JSON 字符串。</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolContent {

    private String type;

    private String text;

    public static ToolContent text(String text) {
        return new ToolContent("text", text);
    }

    public String getType() {
        return this.type;
    }

    public String getText() {
        return this.text;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setText(String text) {
        this.text = text;
    }

    protected boolean canEqual(Object other) {
        return other instanceof ToolContent;
    }

    public ToolContent() {
    }

    public ToolContent(String type, String text) {
        this.type = type;
        this.text = text;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof ToolContent other)) {
            return false;
        }
        if (!other.canEqual(this)) {
            return false;
        }
        return Objects.equals(getType(), other.getType())
                && Objects.equals(getText(), other.getText());
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                getType(),
                getText());
    }

    @Override
    public String toString() {
        return "ToolContent(type=" + getType()
                + ", text=" + getText() + ")";
    }
}
