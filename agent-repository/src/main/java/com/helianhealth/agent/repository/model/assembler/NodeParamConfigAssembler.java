package com.helianhealth.agent.repository.model.assembler;

import com.helianhealth.agent.repository.model.domain.NodeParamConfig;
import com.helianhealth.agent.repository.model.po.NodeParamConfigPO;

/**
 * 节点参数配置 PO/Domain 双向转换器
 */
public final class NodeParamConfigAssembler {

    private NodeParamConfigAssembler() {
    }

    public static NodeParamConfig toDomain(NodeParamConfigPO entity) {
        if (entity == null) {
            return null;
        }
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

    public static NodeParamConfigPO toPO(NodeParamConfig config) {
        if (config == null) {
            return null;
        }
        return NodeParamConfigPO.builder()
                .configId(config.getConfigId())
                .processType(config.getProcessType())
                .nodeId(config.getNodeId())
                .parentId(config.getParentId())
                .sourceParamKey(config.getSourceParamKey())
                .sourceParamType(config.getSourceParamType())
                .paramDesc(config.getParamDesc())
                .targetParamKey(config.getTargetParamKey())
                .targetParamType(config.getTargetParamType())
                .keepSingleElementKey(config.getKeepSingleElementKey())
                .sort(config.getSort())
                .mappingType(config.getMappingType())
                .mappingSource(config.getMappingSource())
                .mappingRule(config.getMappingRule())
                .build();
    }
}
