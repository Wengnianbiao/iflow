package com.helianhealth.agent.invocation.factory;

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
 * agent的spi扩展工厂，用于注入属性
 * 复刻SpringExtensionInjector,但优先按照type查找
 */
@Slf4j
public class AgentExtensionFactory implements ExtensionFactory {

    @Override
    public <T> T getExtension(Class<T> type, String name) {
        ApplicationContext context = ApplicationContextUtil.getContext();
        if (context == null){
            return null;
        }

        // check @SPI annotation
        if (type.isInterface() && type.isAnnotationPresent(SPI.class)) {
            return null;
        }

        T bean = getOptionalBean(context, name, type);

        return bean;
    }

    private <T> T getOptionalBean(final ListableBeanFactory context, final String name, final Class<T> type) {
        // 1. 先按类型查找所有匹配的Bean
        String[] allBeanNames = context.getBeanNamesForType(type, true, false);
        if (allBeanNames.length == 0) {
            return null;
        }

        // 2. 过滤出单例Bean（SPI注入通常期望单例）
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
                T bean = context.getBean(beanName, type);
                return bean;
            } catch (Exception e) {
                log.error("获取Bean失败（类型：{}，名称：{}）", type.getName(), beanName, e);
                return null;
            }
        }

        // 4. 类型多实现时，按名称筛选（核心优化：仅在多实现时用名称）
        if (StringUtils.isNotEmpty(name)) {
            // 检查名称是否在匹配的单例Bean中
            if (singletonBeanNames.contains(name)) {
                try {
                    T bean = context.getBean(name, type);
                    return bean;
                } catch (Exception e) {
                    log.error("按名称获取Bean失败（类型：{}，名称：{}）", type.getName(), name, e);
                    return null;
                }
            } else {
                return null;
            }
        }

        // 5. 类型多实现且未指定名称：提示需指定名称
        String errorMsg = String.format("【%s】类型存在多个单例Bean，需指定名称（可选：%s）",
                type.getName(), singletonBeanNames);
        log.error(errorMsg);
        throw new IllegalStateException(errorMsg);
    }
}
