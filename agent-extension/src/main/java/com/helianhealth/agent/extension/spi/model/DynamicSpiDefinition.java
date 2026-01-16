package com.helianhealth.agent.extension.spi.model;

import lombok.Data;

/**
 * The spi module only focuses on the registration and unregistration of spi extension classes
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
