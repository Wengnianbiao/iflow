package com.helianhealth.agent.utils;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Component
public class ApplicationContextUtil implements ApplicationContextAware{

    public static ApplicationContext localApplicationContext;
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        localApplicationContext = applicationContext;
    }

    public static ApplicationContext getContext(){
        return localApplicationContext;
    }
}
