package com.helianhealth.agent.schedule.build;

import com.helianhealth.agent.controller.workflow.reponse.InterfaceWorkflowNodeView;
import com.helianhealth.agent.model.domain.InterfaceWorkflowNode;
import com.helianhealth.agent.service.InterfaceWorkflowNodeService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@AllArgsConstructor
public class WorkFlowNodeBuilder {

    private final InterfaceWorkflowNodeService flowNodeService;

    public InterfaceWorkflowNode build(Integer workFlowNodeId) {
        InterfaceWorkflowNodeView view = flowNodeService.selectByNodeId(workFlowNodeId);

        // 工作流节点baseInfo
        InterfaceWorkflowNode workflowNode = InterfaceWorkflowNode.builder()
                .nodeId(view.getNodeId())
                .nodeName(view.getNodeName())
                .flowId(view.getFlowId())
                .paramFilterExpr(view.getParamFilterExpr())
                .nodeType(view.getNodeType())
                .metaInfo(view.getMetaInfo())
                .splitRule(view.getSplitRule())
                .scheduleExpr(view.getScheduleExpr())
                .scheduleParamSourceType(view.getScheduleParamSourceType())
                .keywordField(view.getKeywordField())
                .remoteExtension(view.getRemoteExtension())
                .build();

        // URL初始化参数配置
        workflowNode.initUrl()
                .node(view.getNodeType().name().toLowerCase())
                .proxy(view.getNodeType().name().toLowerCase())
                .remote(view.getRemoteExtension());

        return workflowNode;
    }

    public List<InterfaceWorkflowNode> build(List<Integer> workFlowNodeIds) {
        List<InterfaceWorkflowNode> nodes = new ArrayList<>();
        workFlowNodeIds.forEach(nodeId -> {
            InterfaceWorkflowNode build = build(nodeId);
            nodes.add(build);
        });

        return nodes;
    }
}
