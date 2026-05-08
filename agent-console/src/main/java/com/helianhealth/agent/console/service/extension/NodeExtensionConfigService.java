package com.helianhealth.agent.console.service.extension;

import com.helianhealth.agent.common.exception.AgentErrorCode;
import com.helianhealth.agent.common.exception.AgentException;
import com.helianhealth.agent.console.enums.ExtensionType;
import com.helianhealth.agent.repository.model.domain.WorkflowNode;
import com.helianhealth.agent.repository.service.WorkflowNodeCommandService;
import com.helianhealth.agent.repository.service.WorkflowNodeQueryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 扩展节点字段更新服务
 * 职责：
 * - 根据扩展类型更新节点的不同字段
 * - REMOTE类型：更新节点的 remote_extension 字段（可多节点绑定，每个节点写入同一扩展名）
 * - FILTER类型：更新节点的 extension_param 字段（多节点绑定）
 * - 与SPI注册逻辑完全解耦
 */
@Service
@Slf4j
public class NodeExtensionConfigService {

    private final WorkflowNodeQueryService workflowNodeQueryService;
    private final WorkflowNodeCommandService workflowNodeCommandService;

    public NodeExtensionConfigService(WorkflowNodeQueryService workflowNodeQueryService,
                                      WorkflowNodeCommandService workflowNodeCommandService) {
        this.workflowNodeQueryService = workflowNodeQueryService;
        this.workflowNodeCommandService = workflowNodeCommandService;
    }

    @Transactional(value = "repositoryAgentTransactionManager", rollbackFor = Exception.class)
    public void updateNodeExtensionField(ExtensionType extensionType, String extensionName, List<Integer> nodeIds) {
        updateNodeExtensionField(extensionType, extensionName, nodeIds, null);
    }

    @Transactional(value = "repositoryAgentTransactionManager", rollbackFor = Exception.class)
    public void updateNodeExtensionField(ExtensionType extensionType, String extensionName,
                                         List<Integer> nodeIds, String extensionParamValue) {
        if (nodeIds == null || nodeIds.isEmpty()) {
            return;
        }

        switch (extensionType) {
            case REMOTE:
                updateRemoteExtensionField(extensionName, nodeIds);
                break;
            case FILTER:
                updateExtensionParamField(extensionName, nodeIds, extensionParamValue);
                break;
            default:
                throw new AgentException(AgentErrorCode.UNSUPPORTED_EXTENSION_TYPE,
                        "不支持的SPI扩展类型: " + extensionType);
        }
    }

    @Transactional(value = "repositoryAgentTransactionManager", rollbackFor = Exception.class)
    public void clearNodeFields(ExtensionType extensionType, String extensionName, List<Integer> nodeIds) {
        if (nodeIds == null || nodeIds.isEmpty()) {
            return;
        }

        switch (extensionType) {
            case REMOTE:
                clearRemoteExtensionField(nodeIds);
                break;
            case FILTER:
                clearExtensionParamField(extensionName, nodeIds);
                break;
            default:
                throw new AgentException(AgentErrorCode.UNSUPPORTED_EXTENSION_TYPE,
                        "不支持的扩展类型: " + extensionType);
        }
    }

    private void updateRemoteExtensionField(String extensionName, List<Integer> nodeIds) {
        if (nodeIds == null || nodeIds.isEmpty()) {
            throw new AgentException(AgentErrorCode.INVALID_NODE_ID_LIST,
                    "Invoker接口拓展节点ID列表不能为空");
        }
        for (Integer nodeId : nodeIds) {
            WorkflowNode node = getNodeOrThrow(nodeId, "注册接口拓展类节点不存在: ");
            workflowNodeCommandService.updateRemoteExtension(node.getNodeId(), extensionName);
        }
    }

    private void updateExtensionParamField(String extensionName, List<Integer> nodeIds, String extensionParamValue) {
        String paramValue = (extensionParamValue != null && !extensionParamValue.trim().isEmpty())
                ? extensionParamValue : "true";
        String filterKey = "filter." + extensionName;

        for (Integer nodeId : nodeIds) {
            WorkflowNode node = getNodeOrThrow(nodeId, "注册过滤器拓展类节点不存在: ");
            node.putExtensionParam(filterKey, paramValue);
            workflowNodeCommandService.saveExtensionParam(node.getNodeId(), node.getExtensionParam());
            log.info("Updated node {} extension_param: {}={}", nodeId, filterKey, paramValue);
        }
    }

    private void clearRemoteExtensionField(List<Integer> nodeIds) {
        for (Integer nodeId : nodeIds) {
            WorkflowNode node = workflowNodeQueryService.selectByNodeId(nodeId);
            if (node != null) {
                workflowNodeCommandService.updateRemoteExtension(node.getNodeId(), null);
            }
        }
    }

    private void clearExtensionParamField(String extensionName, List<Integer> nodeIds) {
        String filterKey = "filter." + extensionName;

        for (Integer nodeId : nodeIds) {
            WorkflowNode node = workflowNodeQueryService.selectByNodeId(nodeId);
            if (node != null && node.removeExtensionParam(filterKey)) {
                workflowNodeCommandService.saveExtensionParam(node.getNodeId(), node.getExtensionParam());
            }
        }
    }

    private WorkflowNode getNodeOrThrow(Integer nodeId, String errorPrefix) {
        WorkflowNode node = workflowNodeQueryService.selectByNodeId(nodeId);
        if (node == null) {
            throw new AgentException(AgentErrorCode.NODE_NOT_FOUND, errorPrefix + nodeId);
        }
        return node;
    }
}
