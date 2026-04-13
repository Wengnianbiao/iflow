package com.helianhealth.agent.repository.service;

import com.helianhealth.agent.repository.model.domain.NodeExtensionConfig;

/**
 * 节点扩展配置写操作服务
 */
public interface NodeExtensionConfigCommandService {

    void insert(NodeExtensionConfig config);

    void update(NodeExtensionConfig config);

    void deleteById(Integer codeId);

    void deleteBySpiInterfaceAndExtension(String spiInterface, String extensionName);
}
