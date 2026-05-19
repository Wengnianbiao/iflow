package com.helianhealth.agent.ai.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.agent.ai.mcp.dispatcher.McpToolDispatcher;
import com.helianhealth.agent.ai.mcp.jsonrpc.JsonRpcError;
import com.helianhealth.agent.ai.mcp.jsonrpc.JsonRpcRequest;
import com.helianhealth.agent.ai.mcp.jsonrpc.JsonRpcResponse;
import com.helianhealth.agent.ai.mcp.protocol.ToolCallParams;
import com.helianhealth.agent.ai.mcp.protocol.ToolCallResult;
import com.helianhealth.agent.ai.mcp.protocol.ToolDefinition;
import com.helianhealth.agent.common.base.ResultData;
import com.helianhealth.agent.common.exception.AgentException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP Server 协议入口。
 *
 * <p>实现 JSON-RPC 2.0 over HTTP，支持以下方法：</p>
 * <ul>
 *   <li>{@code initialize} - 协议握手，返回 protocolVersion / capabilities / serverInfo</li>
 *   <li>{@code tools/list} - 列出所有 Tool 的 schema</li>
 *   <li>{@code tools/call} - 调用具体 Tool</li>
 *   <li>{@code ping} - 心跳</li>
 *   <li>{@code notifications/initialized} - notification，无响应</li>
 * </ul>
 *
 * <p>路径来自 {@link McpServerProperties#getBasePath()}（默认 {@code /mcp}），
 * 可通过 {@code interface-agent.mcp.enabled=false} 关闭整个入口。</p>
 *
 * <h3>错误约定</h3>
 * <ul>
 *   <li>协议级错误（方法不存在、参数缺失等）→ JSON-RPC {@code error} 字段</li>
 *   <li>业务级错误（业务校验失败、域异常）→ 正常 result，但 {@code isError=true}，
 *       text 中是 {@link ResultData} 的错误 JSON</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("${interface-agent.mcp.base-path:/mcp}")
@ConditionalOnProperty(prefix = "interface-agent.mcp", name = "enabled", havingValue = "true", matchIfMissing = true)
public class McpServerEndpoint {

    private final ObjectMapper objectMapper;
    private final McpToolRegistry toolRegistry;
    private final McpServerProperties properties;

    public McpServerEndpoint(ObjectMapper objectMapper,
                             McpToolRegistry toolRegistry,
                             McpServerProperties properties) {
        this.objectMapper = objectMapper;
        this.toolRegistry = toolRegistry;
        this.properties = properties;
    }

    @PostMapping
    public JsonRpcResponse handle(@RequestBody JsonRpcRequest request) {
        if (request == null) {
            return JsonRpcResponse.failure(null, JsonRpcError.of(JsonRpcError.INVALID_REQUEST, "请求体为空"));
        }
        Object id = request.getId();
        String method = request.getMethod();
        if (method == null || method.trim().isEmpty()) {
            return JsonRpcResponse.failure(id, JsonRpcError.of(JsonRpcError.INVALID_REQUEST, "method 不能为空"));
        }

        try {
            switch (method) {
                case "initialize":
                    return JsonRpcResponse.success(id, initialize());
                case "notifications/initialized":
                    return null; // notification，按 JSON-RPC 不返回响应
                case "ping":
                    return JsonRpcResponse.success(id, Collections.emptyMap());
                case "tools/list":
                    return JsonRpcResponse.success(id, listTools());
                case "tools/call":
                    return JsonRpcResponse.success(id, callTool(request.getParams()));
                default:
                    return JsonRpcResponse.failure(id,
                            JsonRpcError.of(JsonRpcError.METHOD_NOT_FOUND, "未知方法: " + method));
            }
        } catch (IllegalArgumentException e) {
            log.warn("MCP 请求参数错误: method={}, message={}", method, e.getMessage());
            return JsonRpcResponse.failure(id,
                    JsonRpcError.of(JsonRpcError.INVALID_PARAMS, e.getMessage()));
        } catch (AgentException e) {
            log.warn("MCP 业务异常: method={}, code={}, message={}", method, e.getErrorCode(), e.getErrorMessage());
            return JsonRpcResponse.failure(id,
                    JsonRpcError.of(JsonRpcError.INTERNAL_ERROR, e.getErrorMessage()));
        } catch (Exception e) {
            log.error("MCP 内部错误: method={}", method, e);
            return JsonRpcResponse.failure(id,
                    JsonRpcError.of(JsonRpcError.INTERNAL_ERROR, "服务端错误: " + e.getMessage()));
        }
    }

    private Map<String, Object> initialize() {
        Map<String, Object> serverInfo = new LinkedHashMap<>();
        serverInfo.put("name", properties.getServerName());
        serverInfo.put("version", properties.getServerVersion());

        Map<String, Object> toolsCapability = new LinkedHashMap<>();
        toolsCapability.put("listChanged", false);

        Map<String, Object> capabilities = new LinkedHashMap<>();
        capabilities.put("tools", toolsCapability);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("protocolVersion", properties.getProtocolVersion());
        result.put("capabilities", capabilities);
        result.put("serverInfo", serverInfo);
        return result;
    }

    private Map<String, Object> listTools() {
        List<ToolDefinition> tools = toolRegistry.listTools();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tools", tools);
        return result;
    }

    private ToolCallResult callTool(JsonNode paramsNode) {
        if (paramsNode == null || paramsNode.isNull()) {
            throw new IllegalArgumentException("tools/call 缺少 params");
        }
        ToolCallParams params = objectMapper.convertValue(paramsNode, ToolCallParams.class);
        if (params.getName() == null || params.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("tools/call.params.name 不能为空");
        }
        McpToolDispatcher dispatcher = toolRegistry.findDispatcher(params.getName());
        if (dispatcher == null) {
            throw new IllegalArgumentException("未注册的 tool: " + params.getName());
        }

        JsonNode arguments = params.getArguments();
        try {
            Object data = dispatcher.call(arguments);
            ResultData<Object> payload = ResultData.ok(data);
            return ToolCallResult.ok(toJson(payload));
        } catch (IllegalArgumentException e) {
            log.warn("Tool 调用参数校验失败: tool={}, message={}", params.getName(), e.getMessage());
            ResultData<Object> payload = ResultData.error(e.getMessage());
            return ToolCallResult.error(toJson(payload));
        } catch (AgentException e) {
            log.warn("Tool 业务异常: tool={}, code={}, message={}",
                    params.getName(), e.getErrorCode(), e.getErrorMessage());
            ResultData<Object> payload = ResultData.error(e.getErrorMessage());
            return ToolCallResult.error(toJson(payload));
        } catch (Exception e) {
            log.error("Tool 内部错误: tool={}", params.getName(), e);
            ResultData<Object> payload = ResultData.error("服务端错误: " + e.getMessage());
            return ToolCallResult.error(toJson(payload));
        }
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.error("MCP 序列化业务返回失败", e);
            return "{\"code\":-1,\"message\":\"序列化失败\"}";
        }
    }
}
