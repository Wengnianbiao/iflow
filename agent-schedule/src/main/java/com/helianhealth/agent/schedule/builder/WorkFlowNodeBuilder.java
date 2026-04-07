package com.helianhealth.agent.schedule.builder;

import com.helianhealth.agent.common.base.ExtensionFilterConstants;
import com.helianhealth.agent.repository.model.domain.MockConfig;
import com.helianhealth.agent.repository.model.domain.WorkflowNode;
import com.helianhealth.agent.repository.service.MockConfigQueryService;
import com.helianhealth.agent.repository.service.WorkflowNodeQueryService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@AllArgsConstructor
public class WorkFlowNodeBuilder {

    private final WorkflowNodeQueryService flowNodeService;
    private final MockConfigQueryService mockConfigQueryService;

    public List<WorkflowNode> build(List<Integer> workFlowNodeIds) {
        List<WorkflowNode> nodes = new ArrayList<>();
        workFlowNodeIds.forEach(nodeId -> {
            WorkflowNode build = doBuild(nodeId);
            nodes.add(build);
        });

        return nodes;
    }

    private WorkflowNode doBuild(Integer workFlowNodeId) {
        return flowNodeService.selectByNodeId(workFlowNodeId);
    }

    /**
     * 构建带Mock配置的工作流节点
     * @param workFlowNodeId 节点ID
     * @param mockId Mock配置ID
     * @return 带Mock配置的工作流节点
     */
    public WorkflowNode buildWithMock(Integer workFlowNodeId, Integer mockId) {
        // 1. 验证Mock配置是否存在
        MockConfig mockConfig = mockConfigQueryService.findById(mockId);
        if (mockConfig == null) {
            throw new IllegalArgumentException("Mock配置不存在，mockId=" + mockId);
        }

        // 2. 验证Mock配置是否属于该节点
        if (!mockConfig.getNodeId().equals(workFlowNodeId)) {
            throw new IllegalArgumentException("Mock配置不属于该节点，mockId=" + mockId + ", nodeId=" + workFlowNodeId);
        }

        WorkflowNode node = flowNodeService.selectByNodeId(workFlowNodeId);
        Map<String, String> extensionParam = new HashMap<>();
        if (node.getExtensionParam() != null) {
            extensionParam.putAll(node.getExtensionParam());
        }
        extensionParam.put(ExtensionFilterConstants.EXTENSION_PARAM_KEY_MOCK, mockId.toString());

        return WorkflowNode.builder()
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
                .extensionParam(extensionParam)
                .build();
    }
}
