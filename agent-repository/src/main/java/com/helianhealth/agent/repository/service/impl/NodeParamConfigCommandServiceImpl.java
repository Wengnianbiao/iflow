package com.helianhealth.agent.repository.service.impl;

import com.helianhealth.agent.repository.mapper.NodeParamConfigMapper;
import com.helianhealth.agent.repository.model.po.NodeParamConfigDO;
import com.helianhealth.agent.repository.service.NodeParamConfigCommandService;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 节点参数配置写操作服务实现
 */
@Component
public class NodeParamConfigCommandServiceImpl implements NodeParamConfigCommandService {

    private final NodeParamConfigMapper nodeParamConfigMapper;

    public NodeParamConfigCommandServiceImpl(NodeParamConfigMapper nodeParamConfigMapper) {
        this.nodeParamConfigMapper = nodeParamConfigMapper;
    }

    @Override
    public int insertConfig(NodeParamConfigDO config) {
        return nodeParamConfigMapper.insertNodeParamConfig(config);
    }

    @Override
    public int updateConfig(NodeParamConfigDO config) {
        return nodeParamConfigMapper.updateNodeParamConfig(config);
    }

    @Override
    public NodeParamConfigDO selectConfigById(Integer configId) {
        return nodeParamConfigMapper.selectByPrimaryKey(configId);
    }

    @Override
    public void deleteConfig(Integer configId) {
        nodeParamConfigMapper.deleteByPrimaryKey(configId);
    }

    @Override
    public List<NodeParamConfigDO> selectAllConfigs(Integer nodeId, String processType, String targetParam) {
        return nodeParamConfigMapper.selectAllParamConfig(nodeId, processType, targetParam);
    }
}
