package com.helianhealth.agent.ai.mcp;

import lombok.Getter;

import java.time.Instant;

/**
 * MCP Streamable HTTP 会话状态。
 */
@Getter
public class McpSession {

    private final String sessionId;
    private final String protocolVersion;
    private final Instant createdAt;
    private volatile Instant lastAccessAt;
    private volatile boolean initialized;

    public McpSession(String sessionId, String protocolVersion) {
        this.sessionId = sessionId;
        this.protocolVersion = protocolVersion;
        Instant now = Instant.now();
        this.createdAt = now;
        this.lastAccessAt = now;
    }

    public void touch() {
        this.lastAccessAt = Instant.now();
    }

    public void markInitialized() {
        this.initialized = true;
        touch();
    }
}
