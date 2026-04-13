package com.helianhealth.agent.repository.service.impl;

import com.helianhealth.agent.repository.mapper.agent.NodeParamConfigMapper;
import com.helianhealth.agent.repository.model.assembler.NodeParamConfigAssembler;
import com.helianhealth.agent.repository.model.domain.NodeParamConfig;
import com.helianhealth.agent.repository.model.po.NodeParamConfigPO;
import com.helianhealth.agent.repository.service.NodeParamConfigCommandService;
import org.springframework.stereotype.Service;

/**
 * 节点参数配置写操作服务实现
 */
@Service
public class NodeParamConfigCommandServiceImpl implements NodeParamConfigCommandService {

    private final NodeParamConfigMapper nodeParamConfigMapper;

    public NodeParamConfigCommandServiceImpl(NodeParamConfigMapper nodeParamConfigMapper) {
        this.nodeParamConfigMapper = nodeParamConfigMapper;
    }

    @Override
    public int insertConfig(NodeParamConfig config) {
        NodeParamConfigPO po = NodeParamConfigAssembler.toPO(config);
        int result = nodeParamConfigMapper.insertNodeParamConfig(po);
        config.setConfigId(po.getConfigId());
        return result;
    }

    @Override
    public int updateConfig(NodeParamConfig config) {
        NodeParamConfigPO po = NodeParamConfigAssembler.toPO(config);
        return nodeParamConfigMapper.updateNodeParamConfig(po);
    }

    @Override
    public void deleteConfig(Integer configId) {
        nodeParamConfigMapper.deleteByPrimaryKey(configId);
    }

}
