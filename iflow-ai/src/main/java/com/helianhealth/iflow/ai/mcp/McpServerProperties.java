package com.helianhealth.iflow.ai.mcp;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * MCP Server 配置项。
 *
 * <p>通过 {@code iflow.mcp.*} 前缀注入，由
 * {@code iflow-spring-boot-starter} 统一开关。</p>
 *
 * <pre>{@code
 * iflow:
 *   mcp:
 *     enabled: true
 *     base-path: /mcp
 *     protocol-version: 2025-11-25
 *     server-name: iflow
 *     server-version: 1.0.0
 *     session-enabled: true
 *     strict-session: false
 *     session-ttl-minutes: 60
 *     validate-origin: false
 *     allowed-origins: []
 *     instructions: "Use agent_health.check before automated configuration."
 *     schema-location: classpath:mcp/tool-schemas.json
 * }</pre>
 */
@ConfigurationProperties(prefix = "iflow.mcp")
public class McpServerProperties {

    /**
     * 是否启用 MCP Server（默认开启）
     */
    private boolean enabled = true;

    /**
     * Streamable HTTP MCP 端点路径
     */
    private String basePath = "/mcp";

    /**
     * MCP 服务端在 initialize 响应里上报的 serverInfo.name
     */
    private String serverName = "iflow";

    /**
     * MCP 服务端在 initialize 响应里上报的 serverInfo.version
     */
    private String serverVersion = "1.0.0";

    /**
     * 默认/协商后的 MCP 协议版本（规范日期格式 YYYY-MM-DD）
     */
    private String protocolVersion = McpProtocolVersions.LATEST;

    /**
     * 是否在 initialize 时分配 {@code MCP-Session-Id}
     */
    private boolean sessionEnabled = true;

    /**
     * 非 initialize 请求是否强制校验 {@code MCP-Session-Id}
     */
    private boolean strictSession = false;

    /**
     * 会话 TTL（分钟）
     */
    private long sessionTtlMinutes = 60;

    /**
     * 是否校验 HTTP {@code Origin} 头（生产环境建议开启并配置 allowed-origins）
     */
    private boolean validateOrigin = false;

    /**
     * 允许的 Origin 白名单；为空且 validate-origin=true 时仅拒绝明显非法来源
     */
    private List<String> allowedOrigins = new ArrayList<>();

    /**
     * initialize 响应中的可选 instructions，供 MCP Client 展示给模型
     */
    private String instructions =
            "iFlow MCP server. Call agent_health with action=check before automated configuration.";

    /**
     * Tool schema 资源位置（Spring Resource 表达式）
     */
    private String schemaLocation = "classpath:mcp/tool-schemas.json";

    public McpServerProperties() {
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public String getBasePath() {
        return this.basePath;
    }

    public String getServerName() {
        return this.serverName;
    }

    public String getServerVersion() {
        return this.serverVersion;
    }

    public String getProtocolVersion() {
        return this.protocolVersion;
    }

    public boolean isSessionEnabled() {
        return this.sessionEnabled;
    }

    public boolean isStrictSession() {
        return this.strictSession;
    }

    public long getSessionTtlMinutes() {
        return this.sessionTtlMinutes;
    }

    public boolean isValidateOrigin() {
        return this.validateOrigin;
    }

    public List<String> getAllowedOrigins() {
        return this.allowedOrigins;
    }

    public String getInstructions() {
        return this.instructions;
    }

    public String getSchemaLocation() {
        return this.schemaLocation;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public void setServerVersion(String serverVersion) {
        this.serverVersion = serverVersion;
    }

    public void setProtocolVersion(String protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public void setSessionEnabled(boolean sessionEnabled) {
        this.sessionEnabled = sessionEnabled;
    }

    public void setStrictSession(boolean strictSession) {
        this.strictSession = strictSession;
    }

    public void setSessionTtlMinutes(long sessionTtlMinutes) {
        this.sessionTtlMinutes = sessionTtlMinutes;
    }

    public void setValidateOrigin(boolean validateOrigin) {
        this.validateOrigin = validateOrigin;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    public void setInstructions(String instructions) {
        this.instructions = instructions;
    }

    public void setSchemaLocation(String schemaLocation) {
        this.schemaLocation = schemaLocation;
    }

    protected boolean canEqual(Object other) {
        return other instanceof McpServerProperties;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof McpServerProperties other)) {
            return false;
        }
        if (!other.canEqual(this)) {
            return false;
        }
        return isEnabled() == other.isEnabled()
                && Objects.equals(getBasePath(), other.getBasePath())
                && Objects.equals(getServerName(), other.getServerName())
                && Objects.equals(getServerVersion(), other.getServerVersion())
                && Objects.equals(getProtocolVersion(), other.getProtocolVersion())
                && isSessionEnabled() == other.isSessionEnabled()
                && isStrictSession() == other.isStrictSession()
                && getSessionTtlMinutes() == other.getSessionTtlMinutes()
                && isValidateOrigin() == other.isValidateOrigin()
                && Objects.equals(getAllowedOrigins(), other.getAllowedOrigins())
                && Objects.equals(getInstructions(), other.getInstructions())
                && Objects.equals(getSchemaLocation(), other.getSchemaLocation());
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                isEnabled(),
                getBasePath(),
                getServerName(),
                getServerVersion(),
                getProtocolVersion(),
                isSessionEnabled(),
                isStrictSession(),
                getSessionTtlMinutes(),
                isValidateOrigin(),
                getAllowedOrigins(),
                getInstructions(),
                getSchemaLocation());
    }

    @Override
    public String toString() {
        return "McpServerProperties(enabled=" + isEnabled()
                + ", basePath=" + getBasePath()
                + ", serverName=" + getServerName()
                + ", serverVersion=" + getServerVersion()
                + ", protocolVersion=" + getProtocolVersion()
                + ", sessionEnabled=" + isSessionEnabled()
                + ", strictSession=" + isStrictSession()
                + ", sessionTtlMinutes=" + getSessionTtlMinutes()
                + ", validateOrigin=" + isValidateOrigin()
                + ", allowedOrigins=" + getAllowedOrigins()
                + ", instructions=" + getInstructions()
                + ", schemaLocation=" + getSchemaLocation() + ")";
    }
}
