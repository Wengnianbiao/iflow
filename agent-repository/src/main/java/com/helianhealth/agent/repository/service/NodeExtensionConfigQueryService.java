package com.helianhealth.agent.repository.service;

import com.helianhealth.agent.repository.model.domain.NodeExtensionConfig;

import java.util.List;

/**
 * 节点扩展配置查询服务
 */
public interface NodeExtensionConfigQueryService {

    NodeExtensionConfig selectById(Integer configId);

    NodeExtensionConfig selectBySpiInterfaceAndExtension(String spiInterface, String extensionName);

    List<NodeExtensionConfig> selectByEnabled(Integer enabled);

    List<NodeExtensionConfig> selectBySpiInterface(String spiInterface);
}
