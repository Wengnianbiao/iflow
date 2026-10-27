package com.helianhealth.iflow.common.extension.support;

import java.util.Objects;

/**
 * 动态 SPI 定义模型
 * extension模块只关注SPI扩展类的注册与卸载
 */
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

    public DynamicSpiDefinition() {
    }

    public String getSpiInterface() {
        return this.spiInterface;
    }

    public String getExtensionName() {
        return this.extensionName;
    }

    public String getClassName() {
        return this.className;
    }

    public String getSourceCode() {
        return this.sourceCode;
    }

    public boolean isDynamicCompile() {
        return this.dynamicCompile;
    }

    public void setSpiInterface(String spiInterface) {
        this.spiInterface = spiInterface;
    }

    public void setExtensionName(String extensionName) {
        this.extensionName = extensionName;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public void setSourceCode(String sourceCode) {
        this.sourceCode = sourceCode;
    }

    public void setDynamicCompile(boolean dynamicCompile) {
        this.dynamicCompile = dynamicCompile;
    }

    protected boolean canEqual(Object other) {
        return other instanceof DynamicSpiDefinition;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof DynamicSpiDefinition other)) {
            return false;
        }
        if (!other.canEqual(this)) {
            return false;
        }
        return Objects.equals(getSpiInterface(), other.getSpiInterface())
                && Objects.equals(getExtensionName(), other.getExtensionName())
                && Objects.equals(getClassName(), other.getClassName())
                && Objects.equals(getSourceCode(), other.getSourceCode())
                && isDynamicCompile() == other.isDynamicCompile();
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                getSpiInterface(),
                getExtensionName(),
                getClassName(),
                getSourceCode(),
                isDynamicCompile());
    }

    @Override
    public String toString() {
        return "DynamicSpiDefinition(spiInterface=" + getSpiInterface()
                + ", extensionName=" + getExtensionName()
                + ", className=" + getClassName()
                + ", sourceCode=" + getSourceCode()
                + ", dynamicCompile=" + isDynamicCompile() + ")";
    }
}
