package com.helianhealth.agent.common.extension.config;

import com.helianhealth.agent.common.extension.dynamic.DynamicSpiManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 动态 SPI 相关配置
 */
@Configuration
public class DynamicSpiConfig {

    @Bean
    public DynamicSpiManager dynamicSpiManager() {
        return new DynamicSpiManager();
    }
}
