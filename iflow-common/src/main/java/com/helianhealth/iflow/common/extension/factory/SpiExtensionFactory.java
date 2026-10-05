package com.helianhealth.iflow.common.extension.factory;

import com.helianhealth.iflow.common.extension.ExtensionFactory;
import com.helianhealth.iflow.common.extension.ExtensionLoader;
import com.helianhealth.iflow.common.extension.SPI;

/**
 * SPI Extension Factory
 * 从SPI扩展中获取对象
 */
public class SpiExtensionFactory implements ExtensionFactory {

    @Override
    public <T> T getExtension(Class<T> type, String name) {
        if (type.isInterface() && type.isAnnotationPresent(SPI.class)) {
            ExtensionLoader<T> loader = ExtensionLoader.getExtensionLoader(type);
            if (!loader.getSupportedExtensions().isEmpty()) {
                return loader.getAdaptiveExtension();
            }
        }
        return null;
    }
}
