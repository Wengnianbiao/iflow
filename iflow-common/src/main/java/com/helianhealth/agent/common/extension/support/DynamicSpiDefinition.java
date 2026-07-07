package com.helianhealth.agent.common.extension.support;

import lombok.Data;

/**
 * 动态 SPI 定义模型
 * extension模块只关注SPI扩展类的注册与卸载
 */
@Data
public class DynamicSpiDefinition {

    /**
     * SPI 接口（全限定名）
     */
    private String spiInterface;

    /**
     * 扩展名称
     */
    private String extensionName;

    /**
     * 实现类全限定名
     */
    private String className;

    /**
     * Java 源代码
     */
    private String sourceCode;

    /**
     * 是否需要动态编译
     * true: 在线源码，动态编译加载（独立ClassLoader，支持卸载）
     * false: 项目内实现类，从类路径加载（AppClassLoader，生命周期跟随JVM）
     */
    private boolean dynamicCompile;
}
