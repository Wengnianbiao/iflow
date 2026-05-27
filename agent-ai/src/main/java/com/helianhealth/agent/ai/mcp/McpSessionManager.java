package com.helianhealth.agent.ai.mcp;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP {@code MCP-Session-Id} 会话管理。
 */
@Component
public class McpSessionManager {

    private final McpServerProperties properties;
    private final Map<String, McpSession> sessions = new ConcurrentHashMap<>();

    public McpSessionManager(McpServerProperties properties) {
        this.properties = properties;
    }

    public McpSession create(String protocolVersion) {
        String sessionId = UUID.randomUUID().toString();
        McpSession session = new McpSession(sessionId, protocolVersion);
        sessions.put(sessionId, session);
        evictExpired();
        return session;
    }

    public McpSession get(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return null;
        }
        McpSession session = sessions.get(sessionId.trim());
        if (session == null) {
            return null;
        }
        if (isExpired(session)) {
            sessions.remove(session.getSessionId());
            return null;
        }
        session.touch();
        return session;
    }

    public void invalidate(String sessionId) {
        if (sessionId != null) {
            sessions.remove(sessionId.trim());
        }
    }

    private boolean isExpired(McpSession session) {
        Duration ttl = Duration.ofMinutes(Math.max(properties.getSessionTtlMinutes(), 1));
        return session.getLastAccessAt().plus(ttl).isBefore(Instant.now());
    }

    private void evictExpired() {
        sessions.entrySet().removeIf(entry -> isExpired(entry.getValue()));
    }
}
