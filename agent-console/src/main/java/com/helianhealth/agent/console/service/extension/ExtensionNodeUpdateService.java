package com.helianhealth.agent.console.service.extension;

import com.helianhealth.agent.common.utils.JsonUtils;
import com.helianhealth.agent.console.enums.ExtensionType;
import com.helianhealth.agent.console.mapper.agent.InterfaceFlowNodeMapper;
import com.helianhealth.agent.console.model.po.InterfaceWorkflowNodePO;
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
public class ExtensionNodeUpdateService {

    @Autowired
    private InterfaceFlowNodeMapper nodeMapper;

    /**
     * 根据扩展类型更新节点字段
     *
     * @param extensionType 扩展类型
     * @param extensionName 扩展名称
     * @param nodeIds 节点ID列表
     */
    @Transactional(value = "workflowAgentTransactionManager", rollbackFor = Exception.class)
    public void updateNodeFields(ExtensionType extensionType, String extensionName, List<Integer> nodeIds) {
        if (nodeIds == null || nodeIds.isEmpty()) {
            log.debug("节点ID列表为空，跳过节点字段更新");
            return;
        }

        switch (extensionType) {
            case REMOTE:
                updateRemoteExtensionField(extensionName, nodeIds);
                break;
            case FILTER:
                updateExtensionParamField(extensionName, nodeIds);
                break;
            default:
                throw new IllegalArgumentException("不支持的扩展类型: " + extensionType);
        }
    }

    /**
     * 清理节点上的扩展配置
     *
     * @param extensionType 扩展类型
     * @param extensionName 扩展名称
     * @param nodeIds 节点ID列表
     */
    @Transactional(value = "workflowAgentTransactionManager", rollbackFor = Exception.class)
    public void clearNodeFields(ExtensionType extensionType, String extensionName, List<Integer> nodeIds) {
        if (nodeIds == null || nodeIds.isEmpty()) {
            log.debug("节点ID列表为空，跳过节点字段清理");
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
        Integer nodeId = nodeIds.get(0); // 单节点绑定：只取第一个
        InterfaceWorkflowNodePO node = nodeMapper.selectByPrimaryKey(nodeId);
        if (node != null) {
            node.setRemoteExtension(extensionName);
            nodeMapper.updateByPrimaryKey(node);
            log.info("Updated node {} remote_extension to: {}", nodeId, extensionName);
        } else {
            log.warn("Node not found for update: {}", nodeId);
        }
    }

    /**
     * 更新节点的 extension_param 字段（用于 FILTER 类型）
     * 多节点绑定：遍历所有节点
     * 将 filter 的 extensionName 作为 key，值为 "true"
     */
    private void updateExtensionParamField(String extensionName, List<Integer> nodeIds) {
        for (Integer nodeId : nodeIds) {
            InterfaceWorkflowNodePO node = nodeMapper.selectByPrimaryKey(nodeId);
            if (node != null) {
                // 解析现有的 extension_param
                Map<String, String> extensionParam = new HashMap<>();
                String existingParam = node.getExtensionParam();
                if (existingParam != null && !existingParam.trim().isEmpty()) {
                    try {
                        extensionParam = JsonUtils.fromJsonStringToMap(existingParam);
                    } catch (Exception e) {
                        log.warn("Failed to parse existing extension_param for node {}: {}", nodeId, existingParam, e);
                    }
                }

                // 添加新的 filter 配置（key: "filter.{extensionName}", value: "true"）
                String filterKey = "filter." + extensionName;
                extensionParam.put(filterKey, "true");

                // 回写到节点
                node.setExtensionParam(JsonUtils.toJsonString(extensionParam));
                nodeMapper.updateByPrimaryKey(node);
                log.info("Updated node {} extension_param, added filter: {}", nodeId, filterKey);
            } else {
                log.warn("Node not found for update: {}", nodeId);
            }
        }
    }

    /**
     * 清理节点的 remote_extension 字段
     */
    private void clearRemoteExtensionField(List<Integer> nodeIds) {
        for (Integer nodeId : nodeIds) {
            InterfaceWorkflowNodePO node = nodeMapper.selectByPrimaryKey(nodeId);
            if (node != null) {
                node.setRemoteExtension(null);
                nodeMapper.updateByPrimaryKey(node);
                log.info("Cleared node {} remote_extension", nodeId);
            }
        }
    }

    /**
     * 清理节点的 extension_param 字段中的特定 filter
     */
    private void clearExtensionParamField(String extensionName, List<Integer> nodeIds) {
        for (Integer nodeId : nodeIds) {
            InterfaceWorkflowNodePO node = nodeMapper.selectByPrimaryKey(nodeId);
            if (node != null) {
                String existingParam = node.getExtensionParam();
                if (existingParam != null && !existingParam.trim().isEmpty()) {
                    try {
                        Map<String, String> extensionParam = JsonUtils.fromJsonStringToMap(existingParam);
                        String filterKey = "filter." + extensionName;

                        if (extensionParam.remove(filterKey) != null) {
                            // 如果成功移除了该 filter，回写到节点
                            node.setExtensionParam(JsonUtils.toJsonString(extensionParam));
                            nodeMapper.updateByPrimaryKey(node);
                            log.info("Cleared node {} extension_param filter: {}", nodeId, filterKey);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to clear extension_param for node {}: {}", nodeId, existingParam, e);
                    }
                }
            }
        }
    }
}
