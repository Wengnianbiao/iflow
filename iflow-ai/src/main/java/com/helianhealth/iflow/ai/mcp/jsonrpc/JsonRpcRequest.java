package com.helianhealth.iflow.ai.mcp.jsonrpc;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;

/**
 * JSON-RPC 2.0 请求体。
 *
 * <p>MCP 协议规定客户端通过 JSON-RPC 2.0 与服务端通信。
 * {@link #id} 为空时表示该请求是 notification，不需要返回响应。</p>
 */
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

    public String getJsonrpc() {
        return this.jsonrpc;
    }

    public Object getId() {
        return this.id;
    }

    public String getMethod() {
        return this.method;
    }

    public JsonNode getParams() {
        return this.params;
    }

    public void setJsonrpc(String jsonrpc) {
        this.jsonrpc = jsonrpc;
    }

    public void setId(Object id) {
        this.id = id;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public void setParams(JsonNode params) {
        this.params = params;
    }

    protected boolean canEqual(Object other) {
        return other instanceof JsonRpcRequest;
    }

    public JsonRpcRequest() {
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof JsonRpcRequest other)) {
            return false;
        }
        if (!other.canEqual(this)) {
            return false;
        }
        return Objects.equals(getJsonrpc(), other.getJsonrpc())
                && Objects.equals(getId(), other.getId())
                && Objects.equals(getMethod(), other.getMethod())
                && Objects.equals(getParams(), other.getParams());
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                getJsonrpc(),
                getId(),
                getMethod(),
                getParams());
    }

    @Override
    public String toString() {
        return "JsonRpcRequest(jsonrpc=" + getJsonrpc()
                + ", id=" + getId()
                + ", method=" + getMethod()
                + ", params=" + getParams() + ")";
    }
}
