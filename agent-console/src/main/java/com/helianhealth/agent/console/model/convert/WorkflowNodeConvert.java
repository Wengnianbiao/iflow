package com.helianhealth.agent.console.model.convert;

import com.helianhealth.agent.common.enums.NodeType;
import com.helianhealth.agent.console.model.dto.NodeConfigImportDTO;
import com.helianhealth.agent.console.model.dto.NodeParamConfigImportDTO;
import com.helianhealth.agent.repository.model.domain.WorkflowNode;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

@Repository
public class WorkflowNodeConvert {

    public WorkflowNode importNodeToDomain(NodeConfigImportDTO.NodeInfo nodeInfo) {
        return WorkflowNode.builder()
                .nodeName(nodeInfo.getNodeName())
                .nodeType(NodeType.valueOf(nodeInfo.getNodeType()))
                .metaInfo(nodeInfo.getMetaInfo())
                .paramFilterExpr(nodeInfo.getParamFilterExpr())
                .keywordField(nodeInfo.getKeywordField())
                .splitField(nodeInfo.getSplitField())
                .build();
    }

    public NodeParamConfigImportDTO.NodeParamConfigDTO convertNodeParamConfig(NodeConfigImportDTO.NodeParamConfigDTO source) {
        NodeParamConfigImportDTO.NodeParamConfigDTO target = new NodeParamConfigImportDTO.NodeParamConfigDTO();

        // 基本字段映射
        target.setProcessType(source.getProcessType());
        target.setSourceParamKey(source.getSourceParamKey());
        target.setSourceParamType(source.getSourceParamType());
        target.setParamDesc(source.getParamDesc());
        target.setTargetParamKey(source.getTargetParamKey());
        target.setTargetParamType(source.getTargetParamType());
        target.setKeepSingleElementKey(source.getKeepSingleElementKey());
        target.setSort(source.getSort());
        target.setMappingType(source.getMappingType());
        target.setMappingSource(source.getMappingSource());
        target.setMappingRule(source.getMappingRule());

        // 递归处理子参数
        if (source.getChildParam() != null) {
            List<NodeParamConfigImportDTO.NodeParamConfigDTO> childParams = source.getChildParam().stream()
                    .map(this::convertNodeParamConfig)
                    .collect(Collectors.toList());
            target.setChildParam(childParams);
        }

        return target;
    }
}
