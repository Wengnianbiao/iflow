package com.helianhealth.agent.ai.mcp.jsonrpc;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * JSON-RPC 2.0 错误对象。
 *
 * <p>常用错误码：
 * <ul>
 *   <li>-32700 Parse error</li>
 *   <li>-32600 Invalid Request</li>
 *   <li>-32601 Method not found</li>
 *   <li>-32602 Invalid params</li>
 *   <li>-32603 Internal error</li>
 *   <li>-32000 ~ -32099 Server error（MCP 实现可在该区段自定义）</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JsonRpcError {

    public static final int PARSE_ERROR = -32700;
    public static final int INVALID_REQUEST = -32600;
    public static final int METHOD_NOT_FOUND = -32601;
    public static final int INVALID_PARAMS = -32602;
    public static final int INTERNAL_ERROR = -32603;

    private int code;

    private String message;

    private Object data;

    public static JsonRpcError of(int code, String message) {
        return new JsonRpcError(code, message, null);
    }

    public static JsonRpcError of(int code, String message, Object data) {
        return new JsonRpcError(code, message, data);
    }
}
