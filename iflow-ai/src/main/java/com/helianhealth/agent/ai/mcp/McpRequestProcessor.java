package com.helianhealth.agent.ai.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.agent.ai.mcp.dispatcher.McpToolDispatcher;
import com.helianhealth.agent.ai.mcp.jsonrpc.JsonRpcError;
import com.helianhealth.agent.ai.mcp.jsonrpc.JsonRpcRequest;
import com.helianhealth.agent.ai.mcp.jsonrpc.JsonRpcResponse;
import com.helianhealth.agent.ai.mcp.protocol.McpToolPayload;
import com.helianhealth.agent.ai.mcp.protocol.ToolCallParams;
import com.helianhealth.agent.ai.mcp.protocol.ToolCallResult;
import com.helianhealth.agent.ai.mcp.protocol.ToolDefinition;
import com.helianhealth.agent.common.exception.WorkFlowException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP JSON-RPC 方法分发（与传输层解耦）。
 */
@Slf4j
@Component
public class McpRequestProcessor {

    private final ObjectMapper objectMapper;
    private final McpToolRegistry toolRegistry;
    private final McpServerProperties properties;
    private final McpSessionManager sessionManager;

    public McpRequestProcessor(ObjectMapper objectMapper,
                               McpToolRegistry toolRegistry,
                               McpServerProperties properties,
                               McpSessionManager sessionManager) {
        this.objectMapper = objectMapper;
        this.toolRegistry = toolRegistry;
        this.properties = properties;
        this.sessionManager = sessionManager;
    }

    public ProcessResult process(JsonRpcRequest request, McpSession session) {
        if (request == null) {
            return ProcessResult.response(JsonRpcResponse.failure(null,
                    JsonRpcError.of(JsonRpcError.INVALID_REQUEST, "请求体为空")));
        }

        Object id = request.getId();
        String method = request.getMethod();
        if (method == null || method.trim().isEmpty()) {
            return ProcessResult.response(JsonRpcResponse.failure(id,
                    JsonRpcError.of(JsonRpcError.INVALID_REQUEST, "method 不能为空")));
        }

        boolean notification = id == null;
        try {
            switch (method) {
                case "initialize":
                    return handleInitialize(id, request.getParams());
                case "notifications/initialized":
                    if (session != null) {
                        session.markInitialized();
                    }
                    return ProcessResult.notification();
                case "ping":
                    return ProcessResult.response(JsonRpcResponse.success(id, Collections.emptyMap()));
                case "tools/list":
                    return ProcessResult.response(JsonRpcResponse.success(id, listTools(request.getParams())));
                case "tools/call":
                    return ProcessResult.response(JsonRpcResponse.success(id, callTool(request.getParams())));
                default:
                    if (notification) {
                        log.debug("Ignoring unknown MCP notification: {}", method);
                        return ProcessResult.notification();
                    }
                    return ProcessResult.response(JsonRpcResponse.failure(id,
                            JsonRpcError.of(JsonRpcError.METHOD_NOT_FOUND, "未知方法: " + method)));
            }
        } catch (IllegalArgumentException e) {
            log.warn("MCP request parameter error: method={}, message={}", method, e.getMessage());
            if (notification) {
                return ProcessResult.notification();
            }
            return ProcessResult.response(JsonRpcResponse.failure(id,
                    JsonRpcError.of(JsonRpcError.INVALID_PARAMS, e.getMessage())));
        } catch (WorkFlowException e) {
            log.warn("MCP business error: method={}, code={}, message={}", method, e.getErrorCode(), e.getErrorMessage());
            if (notification) {
                return ProcessResult.notification();
            }
            return ProcessResult.response(JsonRpcResponse.failure(id,
                    JsonRpcError.of(JsonRpcError.INTERNAL_ERROR, e.getErrorMessage())));
        } catch (Exception e) {
            log.error("MCP internal error: method={}", method, e);
            if (notification) {
                return ProcessResult.notification();
            }
            return ProcessResult.response(JsonRpcResponse.failure(id,
                    JsonRpcError.of(JsonRpcError.INTERNAL_ERROR, "服务端错误: " + e.getMessage())));
        }
    }

    private ProcessResult handleInitialize(Object id, JsonNode params) {
        String requestedVersion = extractProtocolVersion(params);
        String negotiated = McpProtocolVersions.negotiate(requestedVersion);

        Map<String, Object> result = buildInitializeResult(negotiated);
        JsonRpcResponse response = JsonRpcResponse.success(id, result);

        if (!properties.isSessionEnabled()) {
            return ProcessResult.response(response, negotiated, null);
        }
        McpSession session = sessionManager.create(negotiated);
        return ProcessResult.response(response, negotiated, session.getSessionId());
    }

    private Map<String, Object> buildInitializeResult(String protocolVersion) {
        Map<String, Object> serverInfo = new LinkedHashMap<>();
        serverInfo.put("name", properties.getServerName());
        serverInfo.put("version", properties.getServerVersion());

        Map<String, Object> toolsCapability = new LinkedHashMap<>();
        toolsCapability.put("listChanged", false);

        Map<String, Object> capabilities = new LinkedHashMap<>();
        capabilities.put("tools", toolsCapability);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("protocolVersion", protocolVersion);
        result.put("capabilities", capabilities);
        result.put("serverInfo", serverInfo);
        if (properties.getInstructions() != null && !properties.getInstructions().trim().isEmpty()) {
            result.put("instructions", properties.getInstructions().trim());
        }
        return result;
    }

    private Map<String, Object> listTools(JsonNode paramsNode) {
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
        String toolName = params.getName();
        String action = extractAction(arguments);
        try {
            Object data = dispatcher.call(arguments);
            return ToolCallResult.ok(toJson(McpToolPayload.success(data)));
        } catch (IllegalArgumentException e) {
            log.warn("Tool 调用参数校验失败: tool={}, action={}, message={}",
                    toolName, action, e.getMessage());
            return toolError(toolName, action, -1, e.getMessage());
        } catch (WorkFlowException e) {
            log.warn("Tool 业务异常: tool={}, action={}, code={}, message={}",
                    toolName, action, e.getErrorCode(), e.getErrorMessage());
            return toolError(toolName, action, e.getErrorCode(), e.getErrorMessage());
        } catch (Exception e) {
            log.error("Tool 内部错误: tool={}, action={}", toolName, action, e);
            return toolError(toolName, action, -1, "服务端错误: " + e.getMessage());
        }
    }

    private static String extractProtocolVersion(JsonNode params) {
        if (params == null || !params.hasNonNull("protocolVersion")) {
            return null;
        }
        String version = params.get("protocolVersion").asText();
        return version == null || version.isEmpty() ? null : version;
    }

    private ToolCallResult toolError(String toolName, String action, int code, String message) {
        String summary = formatToolError(toolName, action, message);
        return ToolCallResult.error(toJson(McpToolPayload.failure(toolName, action, code, message)), summary);
    }

    private static String formatToolError(String toolName, String action, String message) {
        if (action == null || action.isEmpty()) {
            return "[" + toolName + "] " + message;
        }
        return "[" + toolName + "." + action + "] " + message;
    }

    private static String extractAction(JsonNode arguments) {
        if (arguments == null || !arguments.hasNonNull("action")) {
            return null;
        }
        String action = arguments.get("action").asText();
        return action == null || action.isEmpty() ? null : action;
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.error("MCP 序列化业务返回失败", e);
            return "{\"code\":-1,\"message\":\"序列化失败\"}";
        }
    }

    @Getter
    public static final class ProcessResult {

        private final JsonRpcResponse response;
        private final boolean notification;
        private final String negotiatedProtocolVersion;
        private final String newSessionId;

        private ProcessResult(JsonRpcResponse response,
                              boolean notification,
                              String negotiatedProtocolVersion,
                              String newSessionId) {
            this.response = response;
            this.notification = notification;
            this.negotiatedProtocolVersion = negotiatedProtocolVersion;
            this.newSessionId = newSessionId;
        }

        static ProcessResult response(JsonRpcResponse response) {
            return new ProcessResult(response, false, null, null);
        }

        static ProcessResult response(JsonRpcResponse response, String negotiatedProtocolVersion, String newSessionId) {
            return new ProcessResult(response, false, negotiatedProtocolVersion, newSessionId);
        }

        static ProcessResult notification() {
            return new ProcessResult(null, true, null, null);
        }
    }
}
