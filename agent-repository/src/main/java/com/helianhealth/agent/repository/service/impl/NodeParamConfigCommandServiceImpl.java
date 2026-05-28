package com.helianhealth.agent.repository.service.impl;

import com.helianhealth.agent.repository.mapper.agent.NodeParamConfigMapper;
import com.helianhealth.agent.repository.model.assembler.NodeParamConfigAssembler;
import com.helianhealth.agent.repository.model.domain.NodeParamConfig;
import com.helianhealth.agent.repository.model.po.NodeParamConfigPO;
import com.helianhealth.agent.repository.service.NodeParamConfigCommandService;
import com.helianhealth.agent.repository.support.NodeParamConfigMergeSupport;
import com.helianhealth.agent.repository.support.NodeParamConfigNormalizer;
import org.springframework.stereotype.Service;

/**
 * 节点参数配置写操作服务基础实现
 */
@Service
public class NodeParamConfigCommandServiceImpl implements NodeParamConfigCommandService {

    private final NodeParamConfigMapper nodeParamConfigMapper;

    public NodeParamConfigCommandServiceImpl(NodeParamConfigMapper nodeParamConfigMapper) {
        this.nodeParamConfigMapper = nodeParamConfigMapper;
    }

    @Override
    public int insertConfig(NodeParamConfig config) {
        NodeParamConfigNormalizer.normalize(config);
        NodeParamConfigPO po = NodeParamConfigAssembler.toPO(config);
        int result = nodeParamConfigMapper.insertNodeParamConfig(po);
        config.setConfigId(po.getConfigId());
        return result;
    }

    @Override
    public int updateConfig(NodeParamConfig config) {
        if (config == null || config.getConfigId() == null) {
            return 0;
        }
        NodeParamConfigPO existingPo = nodeParamConfigMapper.selectByPrimaryKey(config.getConfigId());
        if (existingPo == null) {
            return 0;
        }
        NodeParamConfig existing = NodeParamConfigAssembler.toDomain(existingPo);
        NodeParamConfig merged = NodeParamConfigMergeSupport.mergeForUpdate(existing, config);
        NodeParamConfigNormalizer.normalize(merged);
        return nodeParamConfigMapper.updateNodeParamConfig(NodeParamConfigAssembler.toPO(merged));
    }

    @Override
    public void deleteConfig(Integer configId) {
        nodeParamConfigMapper.deleteByPrimaryKey(configId);
    }
}
