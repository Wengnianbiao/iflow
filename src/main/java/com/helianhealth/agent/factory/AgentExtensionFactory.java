package com.helianhealth.agent.factory;

import com.helianhealth.agent.utils.ApplicationContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.common.extension.ExtensionFactory;
import org.springframework.context.ApplicationContext;

/**
 * agent的spi扩展工厂，用于注入属性
 * 注入的属性必须是spring bean且成员变量名后缀带有_findBySpring
 */
@Slf4j
public class AgentExtensionFactory implements ExtensionFactory {

    public static final String FIND_BY_SPRING = "_findBySpring";
    @Override
    public <T> T getExtension(Class<T> type, String name) {
        ApplicationContext context = ApplicationContextUtil.getContext();
        if (context == null){
            return null;
        }
        if (!name.contains(FIND_BY_SPRING)){
            return null;
        }
        try{
            String[] split = name.split(FIND_BY_SPRING);
            return context.getBean(type, split[0]);
        }catch (Exception e){
            log.error("spi注入springBean异常 原因:", e);
            return null;
        }

    }
}
