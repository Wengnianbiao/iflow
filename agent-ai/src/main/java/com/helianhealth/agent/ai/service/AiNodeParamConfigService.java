package com.helianhealth.agent.ai.service;

import com.helianhealth.agent.ai.convert.NodeParamConfigMcpConverter;
import com.helianhealth.agent.ai.model.NodeParamConfigMcpResponse;
import com.helianhealth.agent.ai.model.NodeParamConfigMcpView;
import com.helianhealth.agent.common.enums.ProcessType;
import com.helianhealth.agent.repository.model.domain.NodeParamConfig;
import com.helianhealth.agent.repository.model.domain.WorkflowNode;
import com.helianhealth.agent.repository.service.NodeParamConfigCommandService;
import com.helianhealth.agent.repository.service.NodeParamConfigQueryService;
import com.helianhealth.agent.repository.service.WorkflowNodeQueryService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class AiNodeParamConfigService {

    private final NodeParamConfigCommandService nodeParamConfigCommandService;
    private final NodeParamConfigQueryService nodeParamConfigQueryService;
    private final WorkflowNodeQueryService workflowNodeQueryService;

    public boolean nodeExists(Integer nodeId) {
        return workflowNodeQueryService.selectByNodeId(nodeId) != null;
    }

    public String getNodeName(Integer nodeId) {
        WorkflowNode node = workflowNodeQueryService.selectByNodeId(nodeId);
        return node == null ? null : node.getNodeName();
    }

    public List<NodeParamConfig> findAllNodeParamConfigsUnpaged(Integer nodeId, ProcessType processType) {
        String type = processType == null ? null : processType.name();
        return nodeParamConfigQueryService.selectAllConfigs(nodeId, null, type, null);
    }

    public NodeParamConfigMcpResponse exportNodeParamConfigs(Integer nodeId) {
        List<NodeParamConfig> configs = nodeParamConfigQueryService.selectAllConfigs(nodeId, null, null, null);
        List<NodeParamConfigMcpView> preParams = buildTree(configs, null, ProcessType.PRE_PROCESS);
        List<NodeParamConfigMcpView> postParams = buildTree(configs, null, ProcessType.POST_PROCESS);
        String nodeName = getNodeName(nodeId);
        return NodeParamConfigMcpConverter.toMcpResponse(nodeId, nodeName, preParams, postParams);
    }

    public List<NodeParamConfig> findPreProcessDomainConfigs(Integer nodeId) {
        return nodeParamConfigQueryService.selectPreProcessConfigByNodeId(nodeId);
    }

    public List<NodeParamConfig> findPostProcessDomainConfigs(Integer nodeId) {
        return nodeParamConfigQueryService.selectPostProcessConfigByNodeId(nodeId);
    }

    public void deleteAllNodeParamConfigs(Integer nodeId) {
        List<NodeParamConfig> configs = nodeParamConfigQueryService.selectAllConfigs(nodeId, null, null, null);
        for (NodeParamConfig config : configs) {
            nodeParamConfigCommandService.deleteConfig(config.getConfigId());
        }
    }

    public int importNodeParamConfigs(Integer nodeId, List<NodeParamConfigMcpView> params, boolean clearBeforeImport) {
        if (clearBeforeImport) {
            deleteAllNodeParamConfigs(nodeId);
        }
        if (params == null || params.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (NodeParamConfigMcpView param : params) {
            count += processConfigRecursive(param, nodeId, null);
        }
        return count;
    }

    public NodeParamConfig updateNodeParamConfig(NodeParamConfigMcpView configView) {
        NodeParamConfig config = NodeParamConfigMcpConverter.toDomain(configView);
        nodeParamConfigCommandService.updateConfig(config);
        return nodeParamConfigQueryService.selectConfigById(configView.getConfigId());
    }

    public void deleteNodeParamConfig(Integer configId) {
        nodeParamConfigCommandService.deleteConfig(configId);
    }

    private int processConfigRecursive(NodeParamConfigMcpView paramConfigView, Integer nodeId, Integer parentId) {
        NodeParamConfig config = NodeParamConfigMcpConverter.toDomain(paramConfigView, nodeId, parentId);
        NodeParamConfig savedConfig = save(config);
        int count = 1;
        if (paramConfigView.getChildParams() != null && !paramConfigView.getChildParams().isEmpty()) {
            for (NodeParamConfigMcpView child : paramConfigView.getChildParams()) {
                count += processConfigRecursive(child, nodeId, savedConfig.getConfigId());
            }
        }
        return count;
    }

    private NodeParamConfig save(NodeParamConfig config) {
        nodeParamConfigCommandService.insertConfig(config);
        return config;
    }

    private List<NodeParamConfigMcpView> buildTree(List<NodeParamConfig> configs, Integer parentId, ProcessType processType) {
        if (configs == null || configs.isEmpty()) {
            return Collections.emptyList();
        }
        return configs.stream()
                .filter(config -> processType == config.getProcessType())
                .filter(config -> {
                    if (parentId == null) {
                        return config.getParentId() == null;
                    }
                    return parentId.equals(config.getParentId());
                })
                .map(config -> {
                    NodeParamConfigMcpView view = NodeParamConfigMcpConverter.toMcpView(config);
                    List<NodeParamConfigMcpView> children = buildTree(configs, config.getConfigId(), processType);
                    view.setChildParams(children.isEmpty() ? null : children);
                    return view;
                })
                .collect(Collectors.toList());
    }
}
