package com.helianhealth.agent.repository.model.assembler;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.helianhealth.agent.common.utils.JsonUtils;
import com.helianhealth.agent.repository.model.domain.WorkflowNode;
import com.helianhealth.agent.repository.model.po.InterfaceWorkflowNodePO;

import java.util.Map;

/**
 * 工作流节点 PO/Domain 转换器
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
            extensionParam = JSON.parseObject(po.getExtensionParam(), new TypeReference<Map<String, String>>() {});
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
}
