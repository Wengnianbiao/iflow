package com.helianhealth.agent.repository.model.assembler;

import com.helianhealth.agent.common.utils.JsonUtils;
import com.helianhealth.agent.repository.model.domain.WorkflowNode;
import com.helianhealth.agent.repository.model.dto.InterfaceWorkflowNodeView;
import com.helianhealth.agent.repository.model.po.InterfaceWorkflowNodePO;

import java.util.Map;

/**
 * 工作流节点 PO/Domain/View 转换器
 */
public final class WorkflowNodeAssembler {

    private WorkflowNodeAssembler() {
    }

    public static WorkflowNode toDomain(InterfaceWorkflowNodePO po) {
        if (po == null) {
            return null;
        }
        Map<String, String> extensionParam = null;
        if (po.getExtensionParam() != null && !po.getExtensionParam().isEmpty()) {
            extensionParam = JsonUtils.fromJsonStringToMap(po.getExtensionParam());
        }
        return WorkflowNode.builder()
                .nodeId(po.getNodeId())
                .nodeName(po.getNodeName())
                .flowId(po.getFlowId())
                .paramFilterExpr(po.getParamFilterExpr())
                .nodeType(po.getNodeType())
                .metaInfo(po.getMetaInfo())
                .splitField(po.getSplitField())
                .scheduleExpr(po.getScheduleExpr())
                .scheduleParamSourceType(po.getScheduleParamSourceType())
                .keywordField(po.getKeywordField())
                .remoteExtension(po.getRemoteExtension())
                .extensionParam(extensionParam)
                .build();
    }

    public static InterfaceWorkflowNodePO toPO(WorkflowNode node) {
        if (node == null) {
            return null;
        }
        String extensionParamStr = null;
        Map<String, String> extensionParam = node.getExtensionParam();
        if (extensionParam != null && !extensionParam.isEmpty()) {
            extensionParamStr = JsonUtils.toJsonString(extensionParam);
        }
        return InterfaceWorkflowNodePO.builder()
                .nodeId(node.getNodeId())
                .nodeName(node.getNodeName())
                .flowId(node.getFlowId())
                .paramFilterExpr(node.getParamFilterExpr())
                .nodeType(node.getNodeType())
                .metaInfo(node.getMetaInfo())
                .splitField(node.getSplitField())
                .scheduleExpr(node.getScheduleExpr())
                .scheduleParamSourceType(node.getScheduleParamSourceType())
                .keywordField(node.getKeywordField())
                .remoteExtension(node.getRemoteExtension())
                .extensionParam(extensionParamStr)
                .build();
    }

    public static InterfaceWorkflowNodeView toView(InterfaceWorkflowNodePO po) {
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
        view.setExtensionParam(po.getExtensionParam());
        return view;
    }

    public static InterfaceWorkflowNodeView toView(WorkflowNode node) {
        if (node == null) {
            return null;
        }
        InterfaceWorkflowNodeView view = new InterfaceWorkflowNodeView();
        view.setNodeId(node.getNodeId());
        view.setNodeName(node.getNodeName());
        view.setFlowId(node.getFlowId());
        view.setParamFilterExpr(node.getParamFilterExpr());
        view.setNodeType(node.getNodeType());
        view.setMetaInfo(node.getMetaInfo());
        view.setSplitField(node.getSplitField());
        view.setScheduleExpr(node.getScheduleExpr());
        view.setScheduleParamSourceType(node.getScheduleParamSourceType());
        view.setKeywordField(node.getKeywordField());
        view.setRemoteExtension(node.getRemoteExtension());
        return view;
    }
}
