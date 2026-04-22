package com.helianhealth.agent.repository.service.impl;

import com.helianhealth.agent.repository.mapper.agent.NodeExtensionConfigMapper;
import com.helianhealth.agent.repository.model.assembler.NodeExtensionConfigAssembler;
import com.helianhealth.agent.repository.model.domain.NodeExtensionConfig;
import com.helianhealth.agent.repository.service.NodeExtensionConfigCommandService;
import org.springframework.stereotype.Service;

/**
 * 节点扩展配置写操作服务实现
 */
@Service
public class NodeExtensionConfigCommandServiceImpl implements NodeExtensionConfigCommandService {
    private final NodeExtensionConfigMapper nodeExtensionConfigMapper;

    public NodeExtensionConfigCommandServiceImpl(NodeExtensionConfigMapper nodeExtensionConfigMapper) {
        this.nodeExtensionConfigMapper = nodeExtensionConfigMapper;
    }

    @Override
    public void insert(NodeExtensionConfig config) {
        nodeExtensionConfigMapper.insert(NodeExtensionConfigAssembler.toPO(config));
    }

    @Override
    public void update(NodeExtensionConfig config) {
        nodeExtensionConfigMapper.update(NodeExtensionConfigAssembler.toPO(config));
    }

    @Override
    public void deleteById(Integer codeId) {
        nodeExtensionConfigMapper.deleteById(codeId);
    }
}
