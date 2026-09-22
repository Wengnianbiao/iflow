package com.helianhealth.iflow.ai.mcp.jsonrpc;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * JSON-RPC 2.0 请求体。
 *
 * <p>MCP 协议规定客户端通过 JSON-RPC 2.0 与服务端通信。
 * {@link #id} 为空时表示该请求是 notification，不需要返回响应。</p>
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JsonRpcRequest {

    /**
     * 协议版本，固定为 "2.0"
     */
    private String jsonrpc = "2.0";

    /**
     * 请求 ID（数字或字符串）。为空时是 notification。
     */
    private Object id;

    /**
     * 调用的方法名，例如 initialize / tools/list / tools/call
     */
    private String method;

    /**
     * 方法参数（未类型化的 JSON，供各 handler 自己解析）
     */
    private JsonNode params;
}
