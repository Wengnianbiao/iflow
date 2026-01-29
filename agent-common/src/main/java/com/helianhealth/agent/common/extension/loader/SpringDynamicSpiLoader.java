package com.helianhealth.agent.common.extension.loader;

import com.helianhealth.agent.common.extension.AgentExtensionLoader;
import com.helianhealth.agent.common.extension.config.DynamicSpiConfigSource;
import com.helianhealth.agent.common.extension.model.DynamicSpiDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 动态 SPI 启动加载器
 * 在应用启动时通过 AgentExtensionLoader 加载所有启用的动态 SPI 配置
 */
@Component
public class SpringDynamicSpiLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SpringDynamicSpiLoader.class);

    @Autowired(required = false)
    private DynamicSpiConfigSource dynamicSpiConfigSource;

    @Override
    public void run(ApplicationArguments args) {
        if (dynamicSpiConfigSource == null) {
            log.warn("DynamicSpiConfigSource not registered, skipping dynamic SPI loading");
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
                    // 获取接口Class
                    Class<?> interfaceClass = Class.forName(def.getSpiInterface());

                    // 获取对应的AgentExtensionLoader
                    AgentExtensionLoader<?> loader = AgentExtensionLoader.getExtensionLoader(interfaceClass);

                    // 通过loader注册动态扩展
                    loader.registerDynamicExtension(
                            def.getExtensionName(),
                            def.getClassName(),
                            def.getSourceCode()
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
