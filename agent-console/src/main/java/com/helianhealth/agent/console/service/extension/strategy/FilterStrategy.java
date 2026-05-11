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
 * 过滤器扩展策略：管理节点 {@code extension_param} 中 {@code filter.{name}=paramValue} 键值对。
 */
@Component
@Slf4j
public class FilterStrategy implements ExtensionTypeStrategy {

    private static final String FILTER_KEY_PREFIX = "filter.";
    private static final String DEFAULT_PARAM_VALUE = "true";

    private final WorkflowNodeQueryService workflowNodeQueryService;
    private final WorkflowNodeCommandService workflowNodeCommandService;

    public FilterStrategy(WorkflowNodeQueryService workflowNodeQueryService,
                          WorkflowNodeCommandService workflowNodeCommandService) {
        this.workflowNodeQueryService = workflowNodeQueryService;
        this.workflowNodeCommandService = workflowNodeCommandService;
    }

    @Override
    public ExtensionType supportType() {
        return ExtensionType.FILTER;
    }

    @Override
    public void onRegister(NodeExtensionConfig config) {
        writeFilterParam(config.getExtensionName(), config.getBindingIds(), config.getExtensionParamValue());
    }

    @Override
    public void onUpdate(NodeExtensionConfig newConfig, NodeExtensionConfig oldConfig) {
        if (oldConfig != null) {
            clearFilterParam(oldConfig.getExtensionName(), oldConfig.getBindingIds());
        }
        writeFilterParam(newConfig.getExtensionName(), newConfig.getBindingIds(), newConfig.getExtensionParamValue());
    }

    @Override
    public void onUnregister(NodeExtensionConfig config) {
        clearFilterParam(config.getExtensionName(), config.getBindingIds());
    }

    @Override
    public void enrichDetail(NodeExtensionConfig config) {
        List<Integer> nodeIds = config.getBindingIds();
        if (nodeIds == null || nodeIds.isEmpty()) {
            return;
        }
        // 从第一个节点回捞参数值用于编辑回显
        WorkflowNode node = workflowNodeQueryService.selectByNodeId(nodeIds.get(0));
        if (node == null || node.getExtensionParam() == null) {
            return;
        }
        String paramValue = node.getExtensionParam().get(FILTER_KEY_PREFIX + config.getExtensionName());
        config.setExtensionParamValue(paramValue);
    }

    private void writeFilterParam(String extensionName, List<Integer> nodeIds, String rawParamValue) {
        if (nodeIds == null || nodeIds.isEmpty()) {
            return;
        }
        String paramValue = (rawParamValue != null && !rawParamValue.trim().isEmpty())
                ? rawParamValue : DEFAULT_PARAM_VALUE;
        String filterKey = FILTER_KEY_PREFIX + extensionName;

        for (Integer nodeId : nodeIds) {
            WorkflowNode node = workflowNodeQueryService.selectByNodeId(nodeId);
            if (node == null) {
                throw new AgentException(AgentErrorCode.NODE_NOT_FOUND,
                        "注册过滤器拓展类节点不存在: " + nodeId);
            }
            node.putExtensionParam(filterKey, paramValue);
            workflowNodeCommandService.saveExtensionParam(node.getNodeId(), node.getExtensionParam());
            log.info("Updated node {} extension_param: {}={}", nodeId, filterKey, paramValue);
        }
    }

    private void clearFilterParam(String extensionName, List<Integer> nodeIds) {
        if (nodeIds == null || nodeIds.isEmpty()) {
            return;
        }
        String filterKey = FILTER_KEY_PREFIX + extensionName;
        for (Integer nodeId : nodeIds) {
            WorkflowNode node = workflowNodeQueryService.selectByNodeId(nodeId);
            if (node != null && node.removeExtensionParam(filterKey)) {
                workflowNodeCommandService.saveExtensionParam(node.getNodeId(), node.getExtensionParam());
            }
        }
    }
}
