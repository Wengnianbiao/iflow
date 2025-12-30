package com.helianhealth.agent.starter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Interface Agent 自动配置类
 * 其他项目引入 agent-spring-boot-starter 后，该配置会自动生效
 */
@Configuration
@ConditionalOnProperty(prefix = "interface-agent", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(InterfaceAgentProperties.class)
@ComponentScan(basePackages = "com.helianhealth.agent")
public class InterfaceAgentAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(InterfaceAgentAutoConfiguration.class);

    public InterfaceAgentAutoConfiguration() {
        logger.info("========================================");
        logger.info("Interface Agent Auto Configuration initialized!");
        logger.info("========================================");
    }
}
