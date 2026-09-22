package com.helianhealth.iflow.ai.mcp;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

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
@Data
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
}
