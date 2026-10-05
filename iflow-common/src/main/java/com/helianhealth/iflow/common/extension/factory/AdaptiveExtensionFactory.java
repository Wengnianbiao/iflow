package com.helianhealth.iflow.common.extension.factory;

import com.helianhealth.iflow.common.extension.Adaptive;
import com.helianhealth.iflow.common.extension.ExtensionFactory;
import com.helianhealth.iflow.common.extension.ExtensionLoader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Adaptive Extension Factory
 * 自适应扩展工厂，按顺序尝试所有ExtensionFactory实现
 *
 * <p>默认顺序：
 * 1. SpiExtensionFactory - 从SPI扩展获取
 * 2. SpringExtensionFactory - 从Spring容器获取
 * </p>
 */
@Adaptive
public class AdaptiveExtensionFactory implements ExtensionFactory {

    private final List<ExtensionFactory> factories;

    public AdaptiveExtensionFactory() {
        ExtensionLoader<ExtensionFactory> loader =
            ExtensionLoader.getExtensionLoader(ExtensionFactory.class);

        List<ExtensionFactory> list = new ArrayList<>();
        for (String name : loader.getSupportedExtensions()) {
            list.add(loader.getExtension(name));
        }
        factories = Collections.unmodifiableList(list);
    }

    @Override
    public <T> T getExtension(Class<T> type, String name) {
        // 按顺序尝试所有工厂，返回第一个非null结果
        for (ExtensionFactory factory : factories) {
            T extension = factory.getExtension(type, name);
            if (extension != null) {
                return extension;
            }
        }
        return null;
    }
}
