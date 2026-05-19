package com.helianhealth.agent.ai.mcp;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * MCP 模块自包含配置：注册 {@link McpServerProperties}。
 *
 * <p>放在 {@code agent-ai} 内部而不是 starter，是为了让 {@code agent-ai}
 * 模块在被任何方式装配时（agent-web 直依赖、外部业务方引 starter、单独引 agent-ai）
 * 都能自洽，不依赖 starter 的 {@code @EnableConfigurationProperties}。</p>
 */
@Configuration
@EnableConfigurationProperties(McpServerProperties.class)
public class McpServerConfiguration {
}
