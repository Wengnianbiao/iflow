package com.helianhealth.iflow.ai.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;

/**
 * MCP {@code tools/list} 返回的单个 Tool 描述。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolDefinition {

    /**
     * Tool 名称，MCP 客户端调用 {@code tools/call} 时传这个值
     */
    private String name;

    /**
     * Tool 描述，给 LLM 看用来决策是否调用
     */
    private String description;

    /**
     * Tool 入参 schema（JSON Schema）
     */
    private JsonNode inputSchema;

    /**
     * Tool 出参 schema（JSON Schema），描述 {@code tools/call} 成功时 {@code McpToolPayload.data} 的结构。
     * 失败时无 data，见 {@code content[].text} 内 {@code error} / {@code message}。
     */
    private JsonNode outputSchema;

    public String getName() {
        return this.name;
    }

    public String getDescription() {
        return this.description;
    }

    public JsonNode getInputSchema() {
        return this.inputSchema;
    }

    public JsonNode getOutputSchema() {
        return this.outputSchema;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setInputSchema(JsonNode inputSchema) {
        this.inputSchema = inputSchema;
    }

    public void setOutputSchema(JsonNode outputSchema) {
        this.outputSchema = outputSchema;
    }

    protected boolean canEqual(Object other) {
        return other instanceof ToolDefinition;
    }

    public ToolDefinition() {
    }

    public ToolDefinition(String name, String description, JsonNode inputSchema, JsonNode outputSchema) {
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema;
        this.outputSchema = outputSchema;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof ToolDefinition other)) {
            return false;
        }
        if (!other.canEqual(this)) {
            return false;
        }
        return Objects.equals(getName(), other.getName())
                && Objects.equals(getDescription(), other.getDescription())
                && Objects.equals(getInputSchema(), other.getInputSchema())
                && Objects.equals(getOutputSchema(), other.getOutputSchema());
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                getName(),
                getDescription(),
                getInputSchema(),
                getOutputSchema());
    }

    @Override
    public String toString() {
        return "ToolDefinition(name=" + getName()
                + ", description=" + getDescription()
                + ", inputSchema=" + getInputSchema()
                + ", outputSchema=" + getOutputSchema() + ")";
    }

    public static class ToolDefinitionBuilder {

        private String name;
        private String description;
        private JsonNode inputSchema;
        private JsonNode outputSchema;

        ToolDefinitionBuilder() {
        }

        public ToolDefinitionBuilder name(String name) {
            this.name = name;
            return this;
        }

        public ToolDefinitionBuilder description(String description) {
            this.description = description;
            return this;
        }

        public ToolDefinitionBuilder inputSchema(JsonNode inputSchema) {
            this.inputSchema = inputSchema;
            return this;
        }

        public ToolDefinitionBuilder outputSchema(JsonNode outputSchema) {
            this.outputSchema = outputSchema;
            return this;
        }

        public ToolDefinition build() {
            return new ToolDefinition(
                    name,
                    description,
                    inputSchema,
                    outputSchema);
        }

        @Override
        public String toString() {
            return "ToolDefinition.ToolDefinitionBuilder(name=" + name
                    + ", description=" + description
                    + ", inputSchema=" + inputSchema
                    + ", outputSchema=" + outputSchema + ")";
        }
    }

    public static ToolDefinitionBuilder builder() {
        return new ToolDefinitionBuilder();
    }
}
