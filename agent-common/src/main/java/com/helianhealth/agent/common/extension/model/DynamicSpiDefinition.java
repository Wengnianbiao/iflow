package com.helianhealth.agent.common.extension.model;

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
}
