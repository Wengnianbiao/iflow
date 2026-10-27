package com.helianhealth.iflow.starter;

import com.helianhealth.iflow.core.config.IflowProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * iFlow 自动配置类
 *
 * <p>注：{@code McpServerProperties} 由 {@code iflow-ai} 模块内的
 * {@code McpServerConfiguration} 自行注册，无需在此重复声明。</p>
 */
@Configuration
@ConditionalOnProperty(prefix = "iflow", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(IflowProperties.class)
@ComponentScan(basePackages = "com.helianhealth.iflow")
public class IflowAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(IflowAutoConfiguration.class);

    public IflowAutoConfiguration() {
        logger.info("========================================");
        logger.info("iFlow Auto Configuration initialized!");
        logger.info("========================================");
    }
}
