package com.helianhealth.agent.common.extension.factory;

import com.helianhealth.agent.common.utils.SpringContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.context.ApplicationContext;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Spring Extension Factory
 * 从Spring容器中获取Bean，优先按类型查找
 *
 * @author Agent Team
 */
public class SpringAgentExtensionFactory implements AgentExtensionFactory {

    private static final Logger log = LoggerFactory.getLogger(SpringAgentExtensionFactory.class);

    @Override
    public <T> T getExtension(Class<T> type, String name) {
        try {
            ApplicationContext context = SpringContextHolder.getApplicationContext();
            if (context == null) {
                return null;
            }

            return getOptionalBean(context, name, type);

        } catch (Exception e) {
            log.warn("Failed to get bean from Spring context: {}", type.getName(), e);
            return null;
        }
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
                log.debug("Found unique bean by type: {} -> {}", type.getName(), beanName);
                return bean;
            } catch (Exception e) {
                log.error("获取Bean失败（类型：{}，名称：{}）", type.getName(), beanName, e);
                return null;
            }
        }

        // 4. 类型多实现时，按名称筛选（核心优化：仅在多实现时用名称）
        if (name != null && !name.isEmpty()) {
            // 检查名称是否在匹配的单例Bean中
            if (singletonBeanNames.contains(name)) {
                try {
                    T bean = context.getBean(name, type);
                    log.debug("Found bean by name in multiple candidates: {} -> {}", type.getName(), name);
                    return bean;
                } catch (Exception e) {
                    log.error("按名称获取Bean失败（类型：{}，名称：{}）", type.getName(), name, e);
                    return null;
                }
            } else {
                log.warn("指定的Bean名称不在候选列表中（类型：{}，名称：{}，候选：{}）", 
                        type.getName(), name, singletonBeanNames);
                return null;
            }
        }

        // 5. 类型多实现且未指定名称：返回第一个（降级处理，避免抛异常）
        String firstBeanName = singletonBeanNames.get(0);
        log.warn("类型【{}】存在多个单例Bean，未指定名称，返回第一个：{}（可选：{}）",
                type.getName(), firstBeanName, singletonBeanNames);
        try {
            return context.getBean(firstBeanName, type);
        } catch (Exception e) {
            log.error("获取Bean失败（类型：{}，名称：{}）", type.getName(), firstBeanName, e);
            return null;
        }
    }
}
