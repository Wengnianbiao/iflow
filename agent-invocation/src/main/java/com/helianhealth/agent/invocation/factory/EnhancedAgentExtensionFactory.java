package com.helianhealth.agent.invocation.factory;

import com.helianhealth.agent.extension.spi.dynamic.DynamicSpiManager;
import com.helianhealth.agent.common.utils.ApplicationContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.common.extension.ExtensionFactory;
import org.apache.dubbo.common.extension.SPI;
import org.apache.dubbo.common.utils.StringUtils;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.context.ApplicationContext;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 增强版的Agent SPI扩展工厂
 * 支持:
 * 1. 从Spring容器查找Bean
 * 2. 从动态SPI管理器查找动态编译的扩展类
 */
@Slf4j
public class EnhancedAgentExtensionFactory implements ExtensionFactory {

    /**
     * 动态SPI管理器（延迟初始化）
     */
    private volatile DynamicSpiManager dynamicSpiManager;

    @Override
    public <T> T getExtension(Class<T> type, String name) {
        // 1. 检查是否为SPI接口本身
        if (type.isInterface() && type.isAnnotationPresent(SPI.class)) {
            return null;
        }

        // 2. 先尝试从动态SPI获取
        T dynamicExtension = getDynamicExtension(type, name);
        if (dynamicExtension != null) {
            log.debug("从动态SPI获取扩展: type={}, name={}", type.getName(), name);
            return dynamicExtension;
        }

        // 3. 再从Spring容器获取
        ApplicationContext context = ApplicationContextUtil.getContext();
        if (context == null) {
            return null;
        }

        return getOptionalBean(context, name, type);
    }

    /**
     * 从动态SPI管理器获取扩展
     */
    private <T> T getDynamicExtension(Class<T> type, String name) {
        if (StringUtils.isEmpty(name)) {
            return null;
        }

        DynamicSpiManager manager = getDynamicSpiManager();
        if (manager == null) {
            return null;
        }

        try {
            // 使用类型全名作为spiType
            String spiType = type.getName();
            return manager.getExtension(spiType, name);
        } catch (Exception e) {
            log.warn("从动态SPI获取扩展失败: type={}, name={}", type.getName(), name, e);
            return null;
        }
    }

    /**
     * 延迟初始化动态SPI管理器
     */
    private DynamicSpiManager getDynamicSpiManager() {
        if (dynamicSpiManager == null) {
            synchronized (this) {
                if (dynamicSpiManager == null) {
                    ApplicationContext context = ApplicationContextUtil.getContext();
                    if (context != null) {
                        try {
                            dynamicSpiManager = context.getBean(DynamicSpiManager.class);
                        } catch (Exception e) {
                            log.debug("DynamicSpiManager未注册到Spring容器");
                        }
                    }
                }
            }
        }
        return dynamicSpiManager;
    }

    /**
     * 从Spring容器获取Bean
     */
    private <T> T getOptionalBean(final ListableBeanFactory context, final String name, final Class<T> type) {
        // 1. 先按类型查找所有匹配的Bean
        String[] allBeanNames = context.getBeanNamesForType(type, true, false);
        if (allBeanNames.length == 0) {
            return null;
        }

        // 2. 过滤出单例Bean
        List<String> singletonBeanNames = Arrays.stream(allBeanNames)
                .filter(context::isSingleton)
                .collect(Collectors.toList());

        if (singletonBeanNames.isEmpty()) {
            return null;
        }

        // 3. 类型唯一时直接返回
        if (singletonBeanNames.size() == 1) {
            String beanName = singletonBeanNames.get(0);
            try {
                return context.getBean(beanName, type);
            } catch (Exception e) {
                log.error("获取Bean失败: type={}, name={}", type.getName(), beanName, e);
                return null;
            }
        }

        // 4. 类型多实现时，按名称筛选
        if (StringUtils.isNotEmpty(name)) {
            if (singletonBeanNames.contains(name)) {
                try {
                    return context.getBean(name, type);
                } catch (Exception e) {
                    log.error("按名称获取Bean失败: type={}, name={}", type.getName(), name, e);
                    return null;
                }
            } else {
                return null;
            }
        }

        // 5. 类型多实现且未指定名称
        String errorMsg = String.format("类型 %s 存在多个单例Bean，需指定名称: %s",
                type.getName(), singletonBeanNames);
        log.error(errorMsg);
        throw new IllegalStateException(errorMsg);
    }
}
