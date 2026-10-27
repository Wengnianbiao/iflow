package com.helianhealth.iflow.ai.mcp;

import java.time.Instant;

/**
 * MCP Streamable HTTP 会话状态。
 */
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

    public String getSessionId() {
        return this.sessionId;
    }

    public String getProtocolVersion() {
        return this.protocolVersion;
    }

    public Instant getCreatedAt() {
        return this.createdAt;
    }

    public Instant getLastAccessAt() {
        return this.lastAccessAt;
    }

    public boolean isInitialized() {
        return this.initialized;
    }
}
