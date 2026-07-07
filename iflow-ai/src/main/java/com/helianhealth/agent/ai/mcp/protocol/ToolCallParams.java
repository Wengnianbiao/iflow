package com.helianhealth.agent.ai.mcp.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MCP {@code tools/call} 方法的 params 部分。
 */
@Data
@NoArgsConstructor
public class ToolCallParams {

    /**
     * 要调用的 Tool 名称
     */
    private String name;

    /**
     * Tool 入参（JSON Schema 描述的对象），未类型化
     */
    private JsonNode arguments;
}
