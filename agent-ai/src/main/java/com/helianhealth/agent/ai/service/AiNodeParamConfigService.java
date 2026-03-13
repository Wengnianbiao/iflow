package com.helianhealth.agent.ai.service;

import com.helianhealth.agent.ai.convert.NodeParamConfigMcpConverter;
import com.helianhealth.agent.ai.model.NodeParamConfigMcpResponse;
import com.helianhealth.agent.ai.model.NodeParamConfigMcpView;
import com.helianhealth.agent.common.enums.ParamType;
import com.helianhealth.agent.common.enums.ProcessType;
import com.helianhealth.agent.repository.mapper.agent.InterfaceFlowNodeMapper;
import com.helianhealth.agent.repository.mapper.agent.NodeParamConfigMapper;
import com.helianhealth.agent.repository.model.domain.NodeParamConfig;
import com.helianhealth.agent.repository.model.po.InterfaceWorkflowNodePO;
import com.helianhealth.agent.repository.model.po.NodeParamConfigDO;
import com.helianhealth.agent.repository.service.NodeParamConfigCommandService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class AiNodeParamConfigService {

    private final NodeParamConfigCommandService nodeParamConfigCommandService;
    private final NodeParamConfigMapper nodeParamConfigMapper;
    private final InterfaceFlowNodeMapper flowNodeMapper;

    public boolean nodeExists(Integer nodeId) {
        return flowNodeMapper.selectByNodeId(nodeId) != null;
    }

    public String getNodeName(Integer nodeId) {
        InterfaceWorkflowNodePO node = flowNodeMapper.selectByNodeId(nodeId);
        return node == null ? null : node.getNodeName();
    }

    public List<NodeParamConfigDO> findAllNodeParamConfigsUnpaged(Integer nodeId, ProcessType processType) {
        String type = processType == null ? null : processType.name();
        return nodeParamConfigCommandService.selectAllConfigs(nodeId, type, null);
    }

    public NodeParamConfigMcpResponse exportNodeParamConfigs(Integer nodeId) {
        List<NodeParamConfigDO> configs = nodeParamConfigCommandService.selectAllConfigs(nodeId, null, null);
        List<NodeParamConfigMcpView> preParams = buildTree(configs, null, ProcessType.PRE_PROCESS);
        List<NodeParamConfigMcpView> postParams = buildTree(configs, null, ProcessType.POST_PROCESS);
        String nodeName = getNodeName(nodeId);
        return NodeParamConfigMcpConverter.toMcpResponse(nodeId, nodeName, preParams, postParams);
    }

    public List<NodeParamConfig> findPreProcessDomainConfigs(Integer nodeId) {
        List<NodeParamConfigDO> configs = nodeParamConfigMapper.selectPreProcessConfigByNodeId(nodeId);
        return configs.stream().map(this::toDomain).collect(Collectors.toList());
    }

    public List<NodeParamConfig> findPostProcessDomainConfigs(Integer nodeId) {
        List<NodeParamConfigDO> configs = nodeParamConfigMapper.selectPostProcessConfigByNodeId(nodeId);
        return configs.stream().map(this::toDomain).collect(Collectors.toList());
    }

    public void deleteAllNodeParamConfigs(Integer nodeId) {
        List<NodeParamConfigDO> configs = nodeParamConfigCommandService.selectAllConfigs(nodeId, null, null);
        for (NodeParamConfigDO config : configs) {
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

    public NodeParamConfigDO updateNodeParamConfig(NodeParamConfigMcpView configView) {
        NodeParamConfigDO config = NodeParamConfigMcpConverter.toDO(configView);
        normalizeConfig(config);
        nodeParamConfigCommandService.updateConfig(config);
        return nodeParamConfigCommandService.selectConfigById(configView.getConfigId());
    }

    public void deleteNodeParamConfig(Integer configId) {
        nodeParamConfigCommandService.deleteConfig(configId);
    }

    private int processConfigRecursive(NodeParamConfigMcpView paramConfigView, Integer nodeId, Integer parentId) {
        NodeParamConfigDO configDO = NodeParamConfigMcpConverter.toDO(paramConfigView, nodeId, parentId);
        NodeParamConfigDO savedConfig = save(configDO);
        int count = 1;
        if (paramConfigView.getChildParams() != null && !paramConfigView.getChildParams().isEmpty()) {
            for (NodeParamConfigMcpView child : paramConfigView.getChildParams()) {
                count += processConfigRecursive(child, nodeId, savedConfig.getConfigId());
            }
        }
        return count;
    }

    private NodeParamConfigDO save(NodeParamConfigDO config) {
        normalizeConfig(config);
        nodeParamConfigCommandService.insertConfig(config);
        return config;
    }

    private void normalizeConfig(NodeParamConfigDO config) {
        if (config.getSort() == null) {
            config.setSort(1);
        }
        if (config.getSourceParamType() == ParamType.NONE) {
            config.setSourceParamKey(null);
        }
    }

    private List<NodeParamConfigMcpView> buildTree(List<NodeParamConfigDO> configs, Integer parentId, ProcessType processType) {
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

    private NodeParamConfig toDomain(NodeParamConfigDO config) {
        return NodeParamConfig.builder()
                .configId(config.getConfigId())
                .nodeId(config.getNodeId())
                .parentId(config.getParentId())
                .processType(config.getProcessType())
                .sourceParamKey(config.getSourceParamKey())
                .sourceParamType(config.getSourceParamType())
                .paramDesc(config.getParamDesc())
                .targetParamKey(config.getTargetParamKey())
                .targetParamType(config.getTargetParamType())
                .keepSingleElementKey(config.getKeepSingleElementKey())
                .sort(config.getSort())
                .mappingType(config.getMappingType())
                .mappingSource(config.getMappingSource())
                .mappingRule(config.getMappingRule())
                .build();
    }
}
