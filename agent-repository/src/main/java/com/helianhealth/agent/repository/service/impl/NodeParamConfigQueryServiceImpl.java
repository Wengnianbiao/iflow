package com.helianhealth.agent.repository.service.impl;

import com.helianhealth.agent.repository.mapper.NodeParamConfigMapper;
import com.helianhealth.agent.repository.model.domain.NodeParamConfig;
import com.helianhealth.agent.repository.model.po.NodeParamConfigDO;
import com.helianhealth.agent.repository.service.NodeParamConfigQueryService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 节点参数配置查询服务基础实现
 */
@Service
public class NodeParamConfigQueryServiceImpl implements NodeParamConfigQueryService {

    protected final NodeParamConfigMapper nodeParamConfigMapper;

    public NodeParamConfigQueryServiceImpl(NodeParamConfigMapper nodeParamConfigMapper) {
        this.nodeParamConfigMapper = nodeParamConfigMapper;
    }

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
