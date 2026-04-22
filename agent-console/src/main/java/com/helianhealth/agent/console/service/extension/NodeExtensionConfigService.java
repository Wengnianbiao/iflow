package com.helianhealth.agent.console.service.extension;

import com.helianhealth.agent.common.exception.AgentErrorCode;
import com.helianhealth.agent.common.exception.AgentException;
import com.helianhealth.agent.common.utils.JsonUtils;
import com.helianhealth.agent.console.enums.ExtensionType;
import com.helianhealth.agent.repository.model.domain.WorkflowNode;
import com.helianhealth.agent.repository.service.WorkflowNodeCommandService;
import com.helianhealth.agent.repository.service.WorkflowNodeQueryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 扩展节点字段更新服务
 * 职责：
 * - 根据扩展类型更新节点的不同字段
 * - REMOTE类型：更新节点的 remote_extension 字段（单节点绑定）
 * - FILTER类型：更新节点的 extension_param 字段（多节点绑定）
 * - 与SPI注册逻辑完全解耦
 */
@Service
@Slf4j
public class NodeExtensionConfigService {

    private final WorkflowNodeQueryService workflowNodeQueryService;

    private final WorkflowNodeCommandService workflowNodeCommandService;

    public NodeExtensionConfigService(WorkflowNodeQueryService workflowNodeQueryService, WorkflowNodeCommandService workflowNodeCommandService) {
        this.workflowNodeQueryService = workflowNodeQueryService;
        this.workflowNodeCommandService = workflowNodeCommandService;
    }

    /**
     * 根据扩展类型更新节点字段
     *
     * @param extensionType 扩展类型
     * @param extensionName 扩展名称
     * @param nodeIds 节点ID列表
     */
    @Transactional(value = "repositoryAgentTransactionManager", rollbackFor = Exception.class)
    public void updateNodeExtensionField(ExtensionType extensionType, String extensionName, List<Integer> nodeIds) {
        updateNodeExtensionField(extensionType, extensionName, nodeIds, null);
    }

    /**
     * 根据扩展类型更新节点字段（支持自定义参数值）
     *
     * @param extensionType 扩展类型
     * @param extensionName 扩展名称
     * @param nodeIds 节点ID列表
     * @param extensionParamValue 扩展参数值（可选，Filter类型使用，null则默认为"true"）
     */
    @Transactional(value = "repositoryAgentTransactionManager", rollbackFor = Exception.class)
    public void updateNodeExtensionField(ExtensionType extensionType, String extensionName, List<Integer> nodeIds, String extensionParamValue) {
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
                throw new AgentException(AgentErrorCode.UNSUPPORTED_EXTENSION_TYPE, "不支持的SPI扩展类型: " + extensionType);
        }
    }

    /**
     * 清理节点上的扩展配置
     *
     * @param extensionType 扩展类型
     * @param extensionName 扩展名称
     * @param nodeIds 节点ID列表
     */
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
                throw new IllegalArgumentException("不支持的扩展类型: " + extensionType);
        }
    }

    /**
     * 更新节点的 remote_extension 字段（用于 REMOTE 类型）
     * 单节点绑定：只取第一个节点
     */
    private void updateRemoteExtensionField(String extensionName, List<Integer> nodeIds) {
        if (nodeIds == null || nodeIds.size() != 1) {
            log.error("Node ID list is null or size is not 1");
            throw new AgentException(AgentErrorCode.INVALID_NODE_ID_LIST, "Invoker接口拓展节点ID列表为空或大小不是1");
        }
        // remote_extension单节点绑定：只关联单个节点
        Integer nodeId = nodeIds.get(0);
        WorkflowNode node = workflowNodeQueryService.selectByNodeId(nodeId);
        if (node != null) {
            workflowNodeCommandService.updateRemoteExtension(nodeId, extensionName);
        }  else {
            log.error("Node not found for update: {}", nodeId);
            throw new AgentException(AgentErrorCode.NODE_NOT_FOUND, "注册接口拓展类节点不存在: " + nodeId);
        }
    }

    /**
     * 更新节点的 extension_param 字段（用于 FILTER 类型）
     * 多节点绑定：遍历所有节点
     * 将 filter 的 extensionName 作为 key，值为自定义参数的 JSON 字符串或默认 "true"
     *
     * @param extensionName 扩展名称
     * @param nodeIds 节点ID列表
     * @param extensionParamValue 扩展参数值（JSON格式的Map字符串，如 "{\"user\":\"123\"}"，可选，null则默认为"true"）
     */
    private void updateExtensionParamField(String extensionName, List<Integer> nodeIds, String extensionParamValue) {
        // 确定参数值：如果没有传入自定义值，默认为 "true"
        String paramValue = (extensionParamValue != null && !extensionParamValue.trim().isEmpty())
                ? extensionParamValue
                : "true";

        for (Integer nodeId : nodeIds) {
            WorkflowNode node = workflowNodeQueryService.selectByNodeId(nodeId);
            if (node != null) {
                // 获取现有的 extension_param（已是 Map<String,String>）
                Map<String, String> extensionParam = new HashMap<>();
                Map<String, String> existingParam = node.getExtensionParam();
                if (existingParam != null && !existingParam.isEmpty()) {
                    extensionParam.putAll(existingParam);
                }

                // 添加/更新 filter 配置（key: "filter.{extensionName}", value: JSON字符串或"true"）
                String filterKey = "filter." + extensionName;
                extensionParam.put(filterKey, paramValue);

                String newExtensionParam = JsonUtils.toJsonString(extensionParam);
                workflowNodeCommandService.updateExtensionParam(nodeId, newExtensionParam);
                log.info("Updated node {} extension_param: {}={}", nodeId, filterKey, paramValue);
            } else {
                log.error("Node not found for update: {}", nodeId);
                throw new AgentException(AgentErrorCode.NODE_NOT_FOUND, "注册过滤器拓展类节点不存在: " + nodeId);
            }
        }
    }

    /**
     * 清理节点的 remote_extension 字段
     */
    private void clearRemoteExtensionField(List<Integer> nodeIds) {
        for (Integer nodeId : nodeIds) {
            WorkflowNode node = workflowNodeQueryService.selectByNodeId(nodeId);
            if (node != null) {
                workflowNodeCommandService.updateRemoteExtension(nodeId, null);
            }
        }
    }

    /**
     * 清理节点的 extension_param 字段中的特定 filter
     */
    private void clearExtensionParamField(String extensionName, List<Integer> nodeIds) {
        for (Integer nodeId : nodeIds) {
            WorkflowNode node = workflowNodeQueryService.selectByNodeId(nodeId);
            if (node != null) {
                Map<String, String> extensionParam = node.getExtensionParam();
                if (extensionParam != null && !extensionParam.isEmpty()) {
                    String filterKey = "filter." + extensionName;

                    if (extensionParam.remove(filterKey) != null) {
                        // 如果成功移除了该 filter，回写到节点
                        String newExtensionParam = JsonUtils.toJsonString(extensionParam);
                        workflowNodeCommandService.updateExtensionParam(nodeId, newExtensionParam);
                    }
                }
            }
        }
    }
}
