package com.helianhealth.agent.extension.spi.loader;

import com.helianhealth.agent.extension.spi.model.DynamicSpiDefinition;
import com.helianhealth.agent.extension.spi.dynamic.DynamicSpiManager;
import com.helianhealth.agent.extension.spi.config.DynamicSpiConfigSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 动态 SPI 启动加载器
 * 通过仓储接口在应用启动时加载所有启用的动态 SPI 配置
 */
@Slf4j
@Component
public class SpringDynamicSpiLoader implements ApplicationRunner {

    @Autowired(required = false)
    private DynamicSpiConfigSource dynamicSpiConfigRepository;

    @Autowired
    private DynamicSpiManager dynamicSpiManager;

    @Override
    public void run(ApplicationArguments args) {
        if (dynamicSpiConfigRepository == null) {
            log.warn("DynamicSpiConfigSource not registered, skipping dynamic SPI loading");
            return;
        }

        try {
            log.info("Start loading dynamic SPI configurations...");

            List<DynamicSpiDefinition> dynamicSpiDefinitions = dynamicSpiConfigRepository.listEnabled();
            if (dynamicSpiDefinitions == null || dynamicSpiDefinitions.isEmpty()) {
                log.info("No enabled dynamic SPI configurations found");
                return;
            }

            log.info("{} enabled dynamic SPI configurations found", dynamicSpiDefinitions.size());
            dynamicSpiManager.batchLoad(dynamicSpiDefinitions);

            log.info("Dynamic SPI configurations loaded successfully");
        } catch (Exception e) {
            log.error("Exception occurred while loading dynamic SPI configurations", e);
        }
    }
}
