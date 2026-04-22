package com.helianhealth.agent.common.extension.support;

import java.util.List;

/**
 * 动态 SPI 配置来源接口
 * 由 workflow 模块实现，提供启用的 SPI 配置列表
 */
public interface DynamicSpiConfigSource {

    /**
     * 查询所有启用的动态 SPI 配置
     */
    List<DynamicSpiDefinition> listEnabled();

    /**
     * 根据 SPI 接口与扩展名查询
     */
    DynamicSpiDefinition findBySpiInterfaceAndExtension(String spiInterface, String extensionName);
}
