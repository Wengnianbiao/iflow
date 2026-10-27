package com.helianhealth.iflow.ai.mcp.jsonrpc;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Objects;

/**
 * JSON-RPC 2.0 响应体。
 *
 * <p>{@link #result} 与 {@link #error} 互斥，只允许一个非空。</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JsonRpcResponse {

    private String jsonrpc = "2.0";

    private Object id;

    private Object result;

    private JsonRpcError error;

    public static JsonRpcResponse success(Object id, Object result) {
        JsonRpcResponse response = new JsonRpcResponse();
        response.setId(id);
        response.setResult(result);
        return response;
    }

    public static JsonRpcResponse failure(Object id, JsonRpcError error) {
        JsonRpcResponse response = new JsonRpcResponse();
        response.setId(id);
        response.setError(error);
        return response;
    }

    public String getJsonrpc() {
        return this.jsonrpc;
    }

    public Object getId() {
        return this.id;
    }

    public Object getResult() {
        return this.result;
    }

    public JsonRpcError getError() {
        return this.error;
    }

    public void setJsonrpc(String jsonrpc) {
        this.jsonrpc = jsonrpc;
    }

    public void setId(Object id) {
        this.id = id;
    }

    public void setResult(Object result) {
        this.result = result;
    }

    public void setError(JsonRpcError error) {
        this.error = error;
    }

    protected boolean canEqual(Object other) {
        return other instanceof JsonRpcResponse;
    }

    public JsonRpcResponse() {
    }

    public JsonRpcResponse(String jsonrpc, Object id, Object result, JsonRpcError error) {
        this.jsonrpc = jsonrpc;
        this.id = id;
        this.result = result;
        this.error = error;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof JsonRpcResponse other)) {
            return false;
        }
        if (!other.canEqual(this)) {
            return false;
        }
        return Objects.equals(getJsonrpc(), other.getJsonrpc())
                && Objects.equals(getId(), other.getId())
                && Objects.equals(getResult(), other.getResult())
                && Objects.equals(getError(), other.getError());
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                getJsonrpc(),
                getId(),
                getResult(),
                getError());
    }

    @Override
    public String toString() {
        return "JsonRpcResponse(jsonrpc=" + getJsonrpc()
                + ", id=" + getId()
                + ", result=" + getResult()
                + ", error=" + getError() + ")";
    }
}
