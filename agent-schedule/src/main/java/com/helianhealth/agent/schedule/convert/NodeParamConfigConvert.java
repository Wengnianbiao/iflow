package com.helianhealth.agent.schedule.convert;

import com.helianhealth.agent.invocation.remote.model.NodeParamConfig;
import com.helianhealth.agent.console.model.domain.NodeParamConfigDO;

public class NodeParamConfigConvert {

    public static NodeParamConfig convertToDomain(NodeParamConfigDO configDO) {
        return NodeParamConfig.builder()
                .configId(configDO.getConfigId())
                .processType(configDO.getProcessType())
                .nodeId(configDO.getNodeId())
                .parentId(configDO.getParentId())
                .sourceParamKey(configDO.getSourceParamKey())
                .sourceParamType(configDO.getSourceParamType())
                .paramDesc(configDO.getParamDesc())
                .targetParamKey(configDO.getTargetParamKey())
                .targetParamType(configDO.getTargetParamType())
                .keepSingleElementKey(configDO.getKeepSingleElementKey())
                .sort(configDO.getSort())
                .mappingType(configDO.getMappingType())
                .mappingSource(configDO.getMappingSource())
                .mappingRule(configDO.getMappingRule())
                .build();
    }
}
