package com.helianhealth.agent.extension.spi.loader;

import com.helianhealth.agent.extension.spi.model.DynamicSpiDefinition;
import com.helianhealth.agent.extension.spi.dynamic.DynamicSpiManager;
import com.helianhealth.agent.extension.spi.config.DynamicSpiConfigSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 动态 SPI 启动加载器
 * 通过仓储接口在应用启动时加载所有启用的动态 SPI 配置
 */
@Component
public class SpringDynamicSpiLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SpringDynamicSpiLoader.class);

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
            List<DynamicSpiDefinition> dynamicSpiDefinitions = dynamicSpiConfigRepository.listEnabled();
            if (dynamicSpiDefinitions == null || dynamicSpiDefinitions.isEmpty()) {
                log.debug("No enabled dynamic SPI configurations found");
                return;
            }

            log.info("Loading {} dynamic SPI configurations", dynamicSpiDefinitions.size());
            dynamicSpiManager.batchLoad(dynamicSpiDefinitions);

            log.info("Dynamic SPI loaded successfully");
        } catch (Exception e) {
            log.error("Exception occurred while loading dynamic SPI configurations", e);
        }
    }
}
