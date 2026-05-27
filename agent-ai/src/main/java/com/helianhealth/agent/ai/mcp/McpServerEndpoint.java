package com.helianhealth.agent.ai.mcp;

import com.helianhealth.agent.ai.mcp.jsonrpc.JsonRpcError;
import com.helianhealth.agent.ai.mcp.jsonrpc.JsonRpcRequest;
import com.helianhealth.agent.ai.mcp.jsonrpc.JsonRpcResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * MCP Server 协议入口（Streamable HTTP，规范 2025-11-25）。
 *
 * <p>支持：</p>
 * <ul>
 *   <li>{@code POST} — JSON-RPC 请求/通知（通知返回 202 Accepted）</li>
 *   <li>{@code GET} — 本实现不提供 SSE 流，返回 405</li>
 *   <li>{@code DELETE} — 终止 {@code MCP-Session-Id} 会话</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("${interface-agent.mcp.base-path:/mcp}")
@ConditionalOnProperty(prefix = "interface-agent.mcp", name = "enabled", havingValue = "true", matchIfMissing = true)
public class McpServerEndpoint {

    public static final String HEADER_SESSION_ID = "MCP-Session-Id";
    public static final String HEADER_PROTOCOL_VERSION = "MCP-Protocol-Version";

    private final McpServerProperties properties;
    private final McpSessionManager sessionManager;
    private final McpRequestProcessor requestProcessor;

    public McpServerEndpoint(McpServerProperties properties,
                             McpSessionManager sessionManager,
                             McpRequestProcessor requestProcessor) {
        this.properties = properties;
        this.sessionManager = sessionManager;
        this.requestProcessor = requestProcessor;
    }

    @PostMapping
    public ResponseEntity<?> handlePost(
            @RequestHeader(value = HEADER_PROTOCOL_VERSION, required = false) String protocolVersionHeader,
            @RequestHeader(value = HEADER_SESSION_ID, required = false) String sessionIdHeader,
            @RequestHeader(value = HttpHeaders.ORIGIN, required = false) String origin,
            @RequestBody(required = false) JsonRpcRequest request) {

        if (properties.isValidateOrigin() && !isAllowedOrigin(origin)) {
            log.warn("MCP Origin validation failed: origin={}", origin);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(JsonRpcResponse.failure(null,
                            JsonRpcError.of(JsonRpcError.INVALID_REQUEST, "Invalid Origin")));
        }

        String method = request != null ? request.getMethod() : null;
        boolean isInitialize = "initialize".equals(method);

        if (!isInitialize) {
            ResponseEntity<?> protocolError = validateProtocolVersionHeader(protocolVersionHeader, sessionIdHeader);
            if (protocolError != null) {
                return protocolError;
            }
            ResponseEntity<?> sessionError = validateSession(sessionIdHeader, method);
            if (sessionError != null) {
                return sessionError;
            }
        }

        McpSession session = sessionManager.get(sessionIdHeader);
        McpRequestProcessor.ProcessResult result = requestProcessor.process(request, session);

        if (result.isNotification()) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).build();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (result.getNewSessionId() != null) {
            headers.set(HEADER_SESSION_ID, result.getNewSessionId());
        }
        String protocolVersion = result.getNegotiatedProtocolVersion();
        if (protocolVersion != null) {
            headers.set(HEADER_PROTOCOL_VERSION, protocolVersion);
        } else if (session != null) {
            headers.set(HEADER_PROTOCOL_VERSION, session.getProtocolVersion());
        } else if (protocolVersionHeader != null && !protocolVersionHeader.isEmpty()) {
            headers.set(HEADER_PROTOCOL_VERSION, protocolVersionHeader);
        }

        return ResponseEntity.ok().headers(headers).body(result.getResponse());
    }

    /**
     * 规范允许 Server 不提供 SSE 流，此时对 GET 返回 405。
     */
    @GetMapping
    public ResponseEntity<Void> handleGet() {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
    }

    @DeleteMapping
    public ResponseEntity<Void> handleDelete(
            @RequestHeader(value = HEADER_SESSION_ID, required = false) String sessionIdHeader) {
        if (sessionIdHeader == null || sessionIdHeader.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        sessionManager.invalidate(sessionIdHeader);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<?> validateProtocolVersionHeader(String protocolVersionHeader, String sessionIdHeader) {
        if (protocolVersionHeader == null || protocolVersionHeader.trim().isEmpty()) {
            return null;
        }
        if (!McpProtocolVersions.isSupported(protocolVersionHeader.trim())) {
            log.warn("Unsupported MCP protocol version: {}", protocolVersionHeader);
            return ResponseEntity.badRequest()
                    .body(JsonRpcResponse.failure(null,
                            JsonRpcError.of(JsonRpcError.INVALID_PARAMS, "Unsupported MCP-Protocol-Version")));
        }
        return null;
    }

    private ResponseEntity<?> validateSession(String sessionIdHeader, String method) {
        if (!properties.isSessionEnabled() || !properties.isStrictSession()) {
            return null;
        }
        if ("initialize".equals(method)) {
            return null;
        }
        McpSession session = sessionManager.get(sessionIdHeader);
        if (session == null) {
            log.warn("MCP session missing or invalid: sessionId={}", sessionIdHeader);
            return ResponseEntity.badRequest()
                    .body(JsonRpcResponse.failure(null,
                            JsonRpcError.of(JsonRpcError.INVALID_REQUEST, "Missing or invalid MCP-Session-Id")));
        }
        return null;
    }

    private boolean isAllowedOrigin(String origin) {
        if (origin == null || origin.trim().isEmpty()) {
            return true;
        }
        if (properties.getAllowedOrigins() == null || properties.getAllowedOrigins().isEmpty()) {
            return true;
        }
        return properties.getAllowedOrigins().contains(origin.trim());
    }
}
