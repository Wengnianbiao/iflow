package com.helianhealth.agent.common.extension.factory;

import com.helianhealth.agent.common.extension.AgentSPI;
import com.helianhealth.agent.common.extension.dynamic.DynamicSpiManager;
import com.helianhealth.agent.common.utils.ApplicationContextUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.context.ApplicationContext;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 增强版的 Agent SPI 扩展工厂
 * 支持：
 * 1. 从动态 SPI 管理器获取扩展
 * 2. 从 Spring 容器查找 Bean
 */
public class EnhancedAgentExtensionFactory implements AgentExtensionFactory {

    private static final Logger log = LoggerFactory.getLogger(EnhancedAgentExtensionFactory.class);

    private volatile DynamicSpiManager dynamicSpiManager;

    @Override
    public <T> T getExtension(Class<T> type, String name) {
        // 跳过 @AgentSPI 接口，由 AgentSpiExtensionFactory 处理
        if (type.isInterface() && type.isAnnotationPresent(AgentSPI.class)) {
            return null;
        }

        T dynamicExtension = getDynamicExtension(type, name);
        if (dynamicExtension != null) {
            return dynamicExtension;
        }

        ApplicationContext context = ApplicationContextUtil.getContext();
        if (context == null) {
            return null;
        }

        return getOptionalBean(context, name, type);
    }

    private <T> T getDynamicExtension(Class<T> type, String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }

        // DynamicSpiManager 只支持接口类型，非接口类型直接跳过
        if (!type.isInterface()) {
            return null;
        }

        DynamicSpiManager manager = getDynamicSpiManager();
        if (manager == null) {
            return null;
        }

        try {
            String spiType = type.getName();
            return manager.getExtension(spiType, name);
        } catch (Exception e) {
            // 只有真正异常时才记录警告（接口类型但查找失败）
            log.debug("Failed to retrieve extension from dynamic SPI: type={}, name={}", type.getName(), name, e);
            return null;
        }
    }

    private DynamicSpiManager getDynamicSpiManager() {
        if (dynamicSpiManager == null) {
            synchronized (this) {
                if (dynamicSpiManager == null) {
                    ApplicationContext context = ApplicationContextUtil.getContext();
                    if (context != null) {
                        try {
                            dynamicSpiManager = context.getBean(DynamicSpiManager.class);
                            log.debug("DynamicSpiManager loaded from Spring context");
                        } catch (Exception e) {
                            log.debug("DynamicSpiManager not found in Spring context");
                        }
                    }
                }
            }
        }
        return dynamicSpiManager;
    }

    private <T> T getOptionalBean(final ListableBeanFactory context, final String name, final Class<T> type) {
        String[] allBeanNames = context.getBeanNamesForType(type, true, false);
        if (allBeanNames.length == 0) {
            return null;
        }

        List<String> singletonBeanNames = Arrays.stream(allBeanNames)
                .filter(context::isSingleton)
                .collect(Collectors.toList());

        if (singletonBeanNames.isEmpty()) {
            return null;
        }

        if (name != null && !name.isEmpty() && singletonBeanNames.contains(name)) {
            return context.getBean(name, type);
        }

        if (singletonBeanNames.size() == 1) {
            return context.getBean(singletonBeanNames.get(0), type);
        }

        log.warn("Found multiple singleton beans of type {}, unable to determine unique bean: {}", type.getName(), singletonBeanNames);
        return null;
    }
}
