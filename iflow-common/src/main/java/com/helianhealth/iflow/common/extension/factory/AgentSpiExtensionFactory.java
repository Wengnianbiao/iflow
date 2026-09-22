package com.helianhealth.iflow.common.extension.factory;

import com.helianhealth.iflow.common.extension.AgentExtensionFactory;
import com.helianhealth.iflow.common.extension.AgentExtensionLoader;
import com.helianhealth.iflow.common.extension.AgentSPI;

/**
 * SPI Extension Factory
 * 从SPI扩展中获取对象
 */
public class AgentSpiExtensionFactory implements AgentExtensionFactory {

    @Override
    public <T> T getExtension(Class<T> type, String name) {
        if (type.isInterface() && type.isAnnotationPresent(AgentSPI.class)) {
            AgentExtensionLoader<T> loader = AgentExtensionLoader.getExtensionLoader(type);
            if (!loader.getSupportedExtensions().isEmpty()) {
                return loader.getAdaptiveExtension();
            }
        }
        return null;
    }
}
