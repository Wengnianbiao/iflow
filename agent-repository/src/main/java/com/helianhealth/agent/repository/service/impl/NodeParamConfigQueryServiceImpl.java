package com.helianhealth.agent.repository.service.impl;

import com.helianhealth.agent.repository.mapper.NodeParamConfigMapper;
import com.helianhealth.agent.repository.model.domain.NodeParamConfig;
import com.helianhealth.agent.repository.model.po.NodeParamConfigDO;
import com.helianhealth.agent.repository.service.NodeParamConfigCommandService;
import com.helianhealth.agent.repository.service.NodeParamConfigQueryService;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 节点参数配置数据访问基础实现（查询 + 写操作）
 */
public class NodeParamConfigQueryServiceImpl implements NodeParamConfigQueryService, NodeParamConfigCommandService {

    protected final NodeParamConfigMapper nodeParamConfigMapper;

    public NodeParamConfigQueryServiceImpl(NodeParamConfigMapper nodeParamConfigMapper) {
        this.nodeParamConfigMapper = nodeParamConfigMapper;
    }

    // ========== Query ==========

    @Override
    public List<NodeParamConfig> selectPreProcessConfigByNodeId(Integer nodeId) {
        return nodeParamConfigMapper.selectPreProcessConfigByNodeId(nodeId).stream()
                .map(this::toNodeParamConfig)
                .collect(Collectors.toList());
    }

    @Override
    public List<NodeParamConfig> selectPostProcessConfigByNodeId(Integer nodeId) {
        return nodeParamConfigMapper.selectPostProcessConfigByNodeId(nodeId).stream()
                .map(this::toNodeParamConfig)
                .collect(Collectors.toList());
    }

    // ========== Command ==========

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

    // ========== Convert ==========

    protected NodeParamConfig toNodeParamConfig(NodeParamConfigDO entity) {
        return NodeParamConfig.builder()
                .configId(entity.getConfigId())
                .processType(entity.getProcessType())
                .nodeId(entity.getNodeId())
                .parentId(entity.getParentId())
                .sourceParamKey(entity.getSourceParamKey())
                .sourceParamType(entity.getSourceParamType())
                .paramDesc(entity.getParamDesc())
                .targetParamKey(entity.getTargetParamKey())
                .targetParamType(entity.getTargetParamType())
                .keepSingleElementKey(entity.getKeepSingleElementKey())
                .sort(entity.getSort())
                .mappingType(entity.getMappingType())
                .mappingSource(entity.getMappingSource())
                .mappingRule(entity.getMappingRule())
                .build();
    }
}
