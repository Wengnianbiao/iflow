package com.helianhealth.agent.schedule.build;

import com.helianhealth.agent.controller.workflow.reponse.InterfaceWorkflowNodeView;
import com.helianhealth.agent.model.domain.InterfaceWorkflowNode;
import com.helianhealth.agent.model.domain.MockConfigDO;
import com.helianhealth.agent.service.InterfaceWorkflowNodeService;
import com.helianhealth.agent.service.MockConfigService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@AllArgsConstructor
public class WorkFlowNodeBuilder {

    private final InterfaceWorkflowNodeService flowNodeService;
    private final MockConfigService mockConfigService;

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
                .splitField(view.getSplitField())
                .scheduleExpr(view.getScheduleExpr())
                .scheduleParamSourceType(view.getScheduleParamSourceType())
                .keywordField(view.getKeywordField())
                .remoteExtension(view.getRemoteExtension())
                .build();

        // URL初始化参数配置
        // 节点树node的构建当前默认都走一个统一处理
        workflowNode.initUrl()
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

    /**
     * 构建带Mock配置的工作流节点
     * @param workFlowNodeId 节点ID
     * @param mockId Mock配置ID
     * @return 带Mock配置的工作流节点
     */
    public InterfaceWorkflowNode buildWithMock(Integer workFlowNodeId, Integer mockId) {
        // 1. 验证Mock配置是否存在
        MockConfigDO mockConfig = mockConfigService.findById(mockId);
        if (mockConfig == null) {
            throw new IllegalArgumentException("Mock配置不存在，mockId=" + mockId);
        }

        // 2. 验证Mock配置是否属于该节点
        if (!mockConfig.getNodeId().equals(workFlowNodeId)) {
            throw new IllegalArgumentException("Mock配置不属于该节点，mockId=" + mockId + ", nodeId=" + workFlowNodeId);
        }

        // 3. 构建基础节点
        InterfaceWorkflowNodeView view = flowNodeService.selectByNodeId(workFlowNodeId);

        // 4. 构建带Mock标记的节点（通过remoteExtension传递mockId）
        InterfaceWorkflowNode workflowNode = InterfaceWorkflowNode.builder()
                .nodeId(view.getNodeId())
                .nodeName(view.getNodeName())
                .flowId(view.getFlowId())
                .paramFilterExpr(view.getParamFilterExpr())
                .nodeType(view.getNodeType())
                .metaInfo(view.getMetaInfo())
                .splitField(view.getSplitField())
                .scheduleExpr(view.getScheduleExpr())
                .scheduleParamSourceType(view.getScheduleParamSourceType())
                .keywordField(view.getKeywordField())
                .build();

        // URL初始化参数配置
        workflowNode.initUrl()
                .proxy(view.getNodeType().name().toLowerCase())
                .mock(mockId);

        return workflowNode;
    }
}
