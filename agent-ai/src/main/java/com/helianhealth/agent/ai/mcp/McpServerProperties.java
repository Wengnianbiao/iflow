package com.helianhealth.agent.ai.mcp;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MCP Server 配置项。
 *
 * <p>通过 {@code interface-agent.mcp.*} 前缀注入，由
 * {@code agent-spring-boot-starter} 统一开关。</p>
 *
 * <pre>{@code
 * interface-agent:
 *   mcp:
 *     enabled: true            # 是否启用 MCP 协议入口
 *     base-path: /mcp          # JSON-RPC 入口路径
 *     server-name: interface-agent
 *     server-version: 1.2.5
 *     schema-location: classpath:mcp/tool-schemas.json
 * }</pre>
 */
@Data
@ConfigurationProperties(prefix = "interface-agent.mcp")
public class McpServerProperties {

    /**
     * 是否启用 MCP Server（默认开启）
     */
    private boolean enabled = true;

    /**
     * JSON-RPC over HTTP 的入口路径
     */
    private String basePath = "/mcp";

    /**
     * MCP 服务端在 initialize 响应里上报的 serverInfo.name
     */
    private String serverName = "interface-agent";

    /**
     * MCP 服务端在 initialize 响应里上报的 serverInfo.version
     */
    private String serverVersion = "1.0.0";

    /**
     * MCP 协议版本（按官方说明的 YYYY-MM-DD 格式）
     */
    private String protocolVersion = "2024-11-05";

    /**
     * Tool schema 资源位置（Spring Resource 表达式）
     */
    private String schemaLocation = "classpath:mcp/tool-schemas.json";
}
