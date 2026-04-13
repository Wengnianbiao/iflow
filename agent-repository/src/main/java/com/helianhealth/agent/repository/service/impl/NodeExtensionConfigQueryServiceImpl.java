package com.helianhealth.agent.repository.service.impl;

import com.helianhealth.agent.repository.mapper.agent.NodeExtensionConfigMapper;
import com.helianhealth.agent.repository.model.assembler.NodeExtensionConfigAssembler;
import com.helianhealth.agent.repository.model.domain.NodeExtensionConfig;
import com.helianhealth.agent.repository.service.NodeExtensionConfigQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 节点扩展配置查询服务实现
 */
@Service
public class NodeExtensionConfigQueryServiceImpl implements NodeExtensionConfigQueryService {

    @Autowired
    private NodeExtensionConfigMapper nodeExtensionConfigMapper;

    @Override
    public NodeExtensionConfig selectById(Integer codeId) {
        return NodeExtensionConfigAssembler.toDomain(nodeExtensionConfigMapper.selectById(codeId));
    }

    @Override
    public NodeExtensionConfig selectBySpiInterfaceAndExtension(String spiInterface, String extensionName) {
        return NodeExtensionConfigAssembler.toDomain(
                nodeExtensionConfigMapper.selectBySpiInterfaceAndExtension(spiInterface, extensionName));
    }

    @Override
    public List<NodeExtensionConfig> selectByEnabled(Integer enabled) {
        return nodeExtensionConfigMapper.selectByEnabled(enabled).stream()
                .map(NodeExtensionConfigAssembler::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<NodeExtensionConfig> selectBySpiInterface(String spiInterface) {
        return nodeExtensionConfigMapper.selectBySpiInterface(spiInterface).stream()
                .map(NodeExtensionConfigAssembler::toDomain)
                .collect(Collectors.toList());
    }
}
