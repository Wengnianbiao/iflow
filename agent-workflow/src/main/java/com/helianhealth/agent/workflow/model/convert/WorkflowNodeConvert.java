package com.helianhealth.agent.workflow.model.convert;

import com.helianhealth.agent.workflow.controller.reponse.InterfaceWorkflowNodeView;
import com.helianhealth.agent.common.enums.NodeType;
import com.helianhealth.agent.workflow.model.dto.NodeConfigImportDTO;
import com.helianhealth.agent.workflow.model.dto.NodeParamConfigImportDTO;
import com.helianhealth.agent.workflow.model.po.InterfaceWorkflowNodePO;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

@Repository
public class WorkflowNodeConvert {

    public InterfaceWorkflowNodeView poConvertToView(InterfaceWorkflowNodePO po) {
        if (po == null) {
            return null;
        }

        InterfaceWorkflowNodeView view = new InterfaceWorkflowNodeView();
        view.setNodeId(po.getNodeId());
        view.setNodeName(po.getNodeName());
        view.setFlowId(po.getFlowId());
        view.setParamFilterExpr(po.getParamFilterExpr());
        view.setNodeType(po.getNodeType());
        view.setMetaInfo(po.getMetaInfo());
        view.setSplitField(po.getSplitField());
        view.setScheduleExpr(po.getScheduleExpr());
        view.setScheduleParamSourceType(po.getScheduleParamSourceType());
        view.setKeywordField(po.getKeywordField());
        view.setRemoteExtension(po.getRemoteExtension());

        return view;
    }

    public InterfaceWorkflowNodePO importNodeToPo(NodeConfigImportDTO.NodeInfo nodeInfo) {
        InterfaceWorkflowNodePO po = new InterfaceWorkflowNodePO();
        po.setNodeName(nodeInfo.getNodeName());
        po.setNodeType(NodeType.valueOf(nodeInfo.getNodeType()));
        po.setMetaInfo(nodeInfo.getMetaInfo());
        po.setParamFilterExpr(nodeInfo.getParamFilterExpr());
        po.setKeywordField(nodeInfo.getKeywordField());
        return po;
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
