package com.helianhealth.agent.console.enums;

import lombok.Getter;

/**
 * 扩展类型枚举
 * 统一管理不同类型的 SPI 扩展接口
 * 目前开放两种spi拓展：过滤器扩展、远程调用扩展
 */
@Getter
public enum ExtensionType {

    /**
     * 过滤器扩展（AgentFilter）
     * - 支持多节点绑定
     * - 自动更新节点的 extension_param 字段
     */
    FILTER("filter", "com.helianhealth.agent.invocation.remote.filter.AgentFilter", "过滤器扩展", true),

    /**
     * 远程调用扩展（InterfaceRemoteInvoker）
     * - 单节点绑定
     * - 自动更新节点的 remote_extension 字段
     */
    REMOTE("remote", "com.helianhealth.agent.invocation.remote.InterfaceRemoteInvoker", "远程调用扩展", false);

    /**
     * 扩展类型代码（用于前端标识）
     */
    private final String code;

    /**
     * SPI 接口全限定名
     */
    private final String spiInterface;

    /**
     * 扩展类型描述
     */
    private final String description;

    /**
     * 是否支持多节点绑定
     */
    private final boolean multiNodeSupported;

    ExtensionType(String code, String spiInterface, String description, boolean multiNodeSupported) {
        this.code = code;
        this.spiInterface = spiInterface;
        this.description = description;
        this.multiNodeSupported = multiNodeSupported;
    }

    /**
     * 根据 SPI 接口全限定名获取扩展类型
     */
    public static ExtensionType fromSpiInterface(String spiInterface) {
        for (ExtensionType type : values()) {
            if (type.spiInterface.equals(spiInterface)) {
                return type;
            }
        }
        throw new IllegalArgumentException("不支持的 SPI 接口: " + spiInterface);
    }

    /**
     * 根据代码获取扩展类型
     */
    public static ExtensionType fromCode(String code) {
        for (ExtensionType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("不支持的扩展类型代码: " + code);
    }
}
