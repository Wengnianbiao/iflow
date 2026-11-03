package com.helianhealth.iflow.ai.mcp;

import com.helianhealth.iflow.ai.mcp.dispatcher.HealthCheckToolDispatcher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** MCP 模块配置：属性绑定、健康检查 Tool 与 Streamable HTTP 入口。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "iflow.mcp", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(McpServerProperties.class)
@Import({McpServerEndpoint.class, McpSessionManager.class, McpRequestProcessor.class,
        McpToolRegistry.class, HealthCheckToolDispatcher.class})
public class McpServerConfiguration {
}
