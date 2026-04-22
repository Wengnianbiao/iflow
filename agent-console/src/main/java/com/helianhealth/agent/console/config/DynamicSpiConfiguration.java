package com.helianhealth.agent.console.config;

import com.helianhealth.agent.common.extension.support.DynamicSpiConfigSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 动态 SPI 配置
 * 注册启动加载器，在应用启动时从 DynamicSpiConfigSource 加载所有启用的动态 SPI
 */
@Configuration
public class DynamicSpiConfiguration {

    @Bean
    public SpringDynamicSpiLoader springDynamicSpiLoader(DynamicSpiConfigSource dynamicSpiConfigSource) {
        return new SpringDynamicSpiLoader(dynamicSpiConfigSource);
    }
}
