package com.helianhealth.agent.console.service.extension.strategy;

import com.helianhealth.agent.common.exception.AgentErrorCode;
import com.helianhealth.agent.common.exception.AgentException;
import com.helianhealth.agent.console.enums.ExtensionType;
import com.helianhealth.agent.repository.model.domain.NodeExtensionConfig;
import com.helianhealth.agent.repository.model.domain.WorkflowNode;
import com.helianhealth.agent.repository.service.WorkflowNodeCommandService;
import com.helianhealth.agent.repository.service.WorkflowNodeQueryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 远程调用扩展策略（Invoker）：管理节点 {@code remote_extension} 字段。
 */
@Component
@Slf4j
public class RemoteStrategy implements ExtensionTypeStrategy {

    private final WorkflowNodeQueryService workflowNodeQueryService;
    private final WorkflowNodeCommandService workflowNodeCommandService;

    public RemoteStrategy(WorkflowNodeQueryService workflowNodeQueryService,
                          WorkflowNodeCommandService workflowNodeCommandService) {
        this.workflowNodeQueryService = workflowNodeQueryService;
        this.workflowNodeCommandService = workflowNodeCommandService;
    }

    @Override
    public ExtensionType supportType() {
        return ExtensionType.REMOTE;
    }

    @Override
    public void onRegister(NodeExtensionConfig config) {
        writeRemoteExtension(config.getExtensionName(), config.getBindingIds());
    }

    @Override
    public void onUpdate(NodeExtensionConfig newConfig, NodeExtensionConfig oldConfig) {
        if (oldConfig != null) {
            clearRemoteExtension(oldConfig.getBindingIds());
        }
        writeRemoteExtension(newConfig.getExtensionName(), newConfig.getBindingIds());
    }

    @Override
    public void onUnregister(NodeExtensionConfig config) {
        clearRemoteExtension(config.getBindingIds());
    }

    private void writeRemoteExtension(String extensionName, List<Integer> nodeIds) {
        if (nodeIds == null || nodeIds.isEmpty()) {
            return;
        }
        for (Integer nodeId : nodeIds) {
            WorkflowNode node = workflowNodeQueryService.selectByNodeId(nodeId);
            if (node == null) {
                throw new AgentException(AgentErrorCode.NODE_NOT_FOUND,
                        "注册接口拓展类节点不存在: " + nodeId);
            }
            workflowNodeCommandService.updateRemoteExtension(node.getNodeId(), extensionName);
            log.info("Updated node {} remote_extension={}", nodeId, extensionName);
        }
    }

    private void clearRemoteExtension(List<Integer> nodeIds) {
        if (nodeIds == null || nodeIds.isEmpty()) {
            return;
        }
        for (Integer nodeId : nodeIds) {
            WorkflowNode node = workflowNodeQueryService.selectByNodeId(nodeId);
            if (node != null) {
                workflowNodeCommandService.updateRemoteExtension(node.getNodeId(), null);
            }
        }
    }
}
