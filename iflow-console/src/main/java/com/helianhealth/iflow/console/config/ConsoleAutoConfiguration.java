package com.helianhealth.iflow.console.config;

import com.helianhealth.iflow.core.config.AgentCoreConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * iflow-console 自动配置：工作流定义 CRUD 与 UI；不暴露真实工作流执行 API。
 */
@Configuration
@ConditionalOnProperty(prefix = "agent.console", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ConsoleProperties.class)
@ComponentScan(basePackages = "com.helianhealth.iflow.console")
@Import(AgentCoreConfiguration.class)
public class ConsoleAutoConfiguration {
}
