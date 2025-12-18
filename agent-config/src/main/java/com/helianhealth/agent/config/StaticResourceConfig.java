package com.helianhealth.agent.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 静态资源配置
 * 配置 Vue 前端页面的静态资源访问路径
 */
@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 映射静态资源路径
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // 将根路径映射到 index.html
        registry.addViewController("/").setViewName("forward:/index.html");
    }
}
