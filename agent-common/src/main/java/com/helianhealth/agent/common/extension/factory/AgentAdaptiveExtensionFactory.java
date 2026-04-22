package com.helianhealth.agent.common.extension.factory;

import com.helianhealth.agent.common.extension.AgentAdaptive;
import com.helianhealth.agent.common.extension.AgentExtensionFactory;
import com.helianhealth.agent.common.extension.AgentExtensionLoader;

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
@AgentAdaptive
public class AgentAdaptiveExtensionFactory implements AgentExtensionFactory {

    private final List<AgentExtensionFactory> factories;

    public AgentAdaptiveExtensionFactory() {
        AgentExtensionLoader<AgentExtensionFactory> loader =
            AgentExtensionLoader.getExtensionLoader(AgentExtensionFactory.class);

        List<AgentExtensionFactory> list = new ArrayList<>();
        for (String name : loader.getSupportedExtensions()) {
            list.add(loader.getExtension(name));
        }
        factories = Collections.unmodifiableList(list);
    }

    @Override
    public <T> T getExtension(Class<T> type, String name) {
        // 按顺序尝试所有工厂，返回第一个非null结果
        for (AgentExtensionFactory factory : factories) {
            T extension = factory.getExtension(type, name);
            if (extension != null) {
                return extension;
            }
        }
        return null;
    }
}
