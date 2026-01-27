package com.helianhealth.agent.schedule.builder;

import com.helianhealth.agent.workflow.controller.reponse.InterfaceWorkflowNodeView;
import com.helianhealth.agent.invocation.remote.model.InterfaceWorkflowNode;
import com.helianhealth.agent.mock.model.MockConfigDO;
import com.helianhealth.agent.workflow.service.InterfaceWorkflowNodeService;
import com.helianhealth.agent.mock.service.MockConfigService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@AllArgsConstructor
public class WorkFlowNodeBuilder {

    private final InterfaceWorkflowNodeService flowNodeService;
    private final MockConfigService mockConfigService;

    public List<InterfaceWorkflowNode> build(List<Integer> workFlowNodeIds) {
        List<InterfaceWorkflowNode> nodes = new ArrayList<>();
        workFlowNodeIds.forEach(nodeId -> {
            InterfaceWorkflowNode build = doBuild(nodeId);
            nodes.add(build);
        });

        return nodes;
    }

    private InterfaceWorkflowNode doBuild(Integer workFlowNodeId) {
        InterfaceWorkflowNodeView view = flowNodeService.selectByNodeId(workFlowNodeId);

        return InterfaceWorkflowNode.builder()
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

        Map<String, String> filterParams = new HashMap<>();
        filterParams.put("mock", mockId.toString());
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
                .filterParams(filterParams)
                .build();

        return workflowNode;
    }
}
