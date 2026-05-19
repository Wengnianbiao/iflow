package com.helianhealth.agent.ai.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MCP {@code tools/list} 返回的单个 Tool 描述。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
}
