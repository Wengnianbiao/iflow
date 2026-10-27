package com.helianhealth.iflow.ai.mcp.protocol;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;

/**
 * MCP {@code tools/call} 方法的 params 部分。
 */
public class ToolCallParams {

    /**
     * 要调用的 Tool 名称
     */
    private String name;

    /**
     * Tool 入参（JSON Schema 描述的对象），未类型化
     */
    private JsonNode arguments;

    public String getName() {
        return this.name;
    }

    public JsonNode getArguments() {
        return this.arguments;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setArguments(JsonNode arguments) {
        this.arguments = arguments;
    }

    protected boolean canEqual(Object other) {
        return other instanceof ToolCallParams;
    }

    public ToolCallParams() {
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof ToolCallParams other)) {
            return false;
        }
        if (!other.canEqual(this)) {
            return false;
        }
        return Objects.equals(getName(), other.getName())
                && Objects.equals(getArguments(), other.getArguments());
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                getName(),
                getArguments());
    }

    @Override
    public String toString() {
        return "ToolCallParams(name=" + getName()
                + ", arguments=" + getArguments() + ")";
    }
}
