package com.helianhealth.agent.console.config;

import com.helianhealth.agent.common.extension.AgentExtensionLoader;
import com.helianhealth.agent.common.extension.support.DynamicSpiConfigSource;
import com.helianhealth.agent.common.extension.support.DynamicSpiDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import java.util.List;

/**
 * 动态 SPI 启动加载器
 * 在应用启动时通过 AgentExtensionLoader 加载所有启用的动态 SPI 配置
 */
public class SpringDynamicSpiLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SpringDynamicSpiLoader.class);

    private final DynamicSpiConfigSource dynamicSpiConfigSource;

    public SpringDynamicSpiLoader(DynamicSpiConfigSource dynamicSpiConfigSource) {
        this.dynamicSpiConfigSource = dynamicSpiConfigSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (dynamicSpiConfigSource == null) {
            return;
        }

        try {
            List<DynamicSpiDefinition> definitions = dynamicSpiConfigSource.listEnabled();
            if (definitions == null || definitions.isEmpty()) {
                log.debug("No enabled dynamic SPI configurations found");
                return;
            }

            log.info("Loading {} dynamic SPI configurations", definitions.size());

            int successCount = 0;
            int failCount = 0;

            for (DynamicSpiDefinition def : definitions) {
                try {
                    Class<?> interfaceClass = Class.forName(def.getSpiInterface());
                    AgentExtensionLoader<?> loader = AgentExtensionLoader.getExtensionLoader(interfaceClass);

                    loader.registerDynamicExtension(
                            def.getExtensionName(),
                            def.getClassName(),
                            def.getSourceCode(),
                            def.isDynamicCompile()
                    );

                    successCount++;
                    log.debug("Loaded dynamic SPI: {}#{}", def.getSpiInterface(), def.getExtensionName());

                } catch (Exception e) {
                    failCount++;
                    log.error("Failed to load dynamic SPI: interface={}, name={}, class={}",
                            def.getSpiInterface(), def.getExtensionName(), def.getClassName(), e);
                }
            }

            log.info("Dynamic SPI loading completed: {} succeeded, {} failed", successCount, failCount);

        } catch (Exception e) {
            log.error("Exception occurred while loading dynamic SPI configurations", e);
        }
    }
}
