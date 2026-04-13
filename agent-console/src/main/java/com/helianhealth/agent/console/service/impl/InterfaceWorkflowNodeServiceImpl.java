package com.helianhealth.agent.console.service.impl;

import com.alibaba.fastjson2.JSON;
import com.helianhealth.agent.common.base.PageList;
import com.helianhealth.agent.common.exception.AgentErrorCode;
import com.helianhealth.agent.common.exception.AgentException;
import com.helianhealth.agent.common.enums.NodeType;
import com.helianhealth.agent.common.enums.ScheduleParamSourceType;
import com.helianhealth.agent.common.utils.SpelUtils;
import com.helianhealth.agent.console.controller.reponse.NodeConfigExportRsp;
import com.helianhealth.agent.console.controller.reponse.NodeParamConfigExportRsp;
import com.helianhealth.agent.console.controller.request.WorkflowNodeCreateReq;
import com.helianhealth.agent.console.controller.request.WorkflowNodeScheduleUpdateReq;
import com.helianhealth.agent.console.controller.request.WorkflowNodeUpdateReq;
import com.helianhealth.agent.console.model.convert.WorkflowNodeConvert;
import com.helianhealth.agent.console.model.dto.NodeConfigImportDTO;
import com.helianhealth.agent.console.model.dto.NodeParamConfigImportDTO;
import com.helianhealth.agent.console.service.InterfaceWorkflowNodeService;
import com.helianhealth.agent.console.service.NodeParamConfigService;
import com.helianhealth.agent.repository.model.domain.NodeParamConfig;
import com.helianhealth.agent.repository.model.assembler.WorkflowNodeAssembler;
import com.helianhealth.agent.repository.model.domain.WorkflowNode;
import com.helianhealth.agent.repository.model.dto.InterfaceWorkflowNodeView;
import com.helianhealth.agent.repository.service.WorkflowNodeCommandService;
import com.helianhealth.agent.repository.service.WorkflowNodeQueryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
public class InterfaceWorkflowNodeServiceImpl implements InterfaceWorkflowNodeService {

    private final WorkflowNodeQueryService workflowNodeQueryService;
    private final WorkflowNodeCommandService workflowNodeCommandService;
    private final WorkflowNodeConvert nodeConvert;
    private final NodeParamConfigService nodeParamConfigService;

    @Autowired
    public InterfaceWorkflowNodeServiceImpl(
            WorkflowNodeQueryService workflowNodeQueryService,
            WorkflowNodeCommandService workflowNodeCommandService,
            WorkflowNodeConvert nodeConvert,
            NodeParamConfigService nodeParamConfigService) {
        this.workflowNodeQueryService = workflowNodeQueryService;
        this.workflowNodeCommandService = workflowNodeCommandService;
        this.nodeConvert = nodeConvert;
        this.nodeParamConfigService = nodeParamConfigService;
    }

    @Override
    public WorkflowNode selectByNodeId(Integer nodeId) {
        return workflowNodeQueryService.selectByNodeId(nodeId);
    }

    @Override
    public PageList<InterfaceWorkflowNodeView> getAllNodes(Integer flowId, int pageNum, int pageSize) {
        try {
            return workflowNodeQueryService.selectAllNodesPaged(flowId, pageNum, pageSize);
        } catch (Exception e) {
            log.error("获取工作流节点列表失败", e);
            throw new AgentException(AgentErrorCode.CONSOLE_OPERATION_FAILED, "获取工作流节点列表失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<InterfaceWorkflowNodeView> getAllNodesWithoutPaged() {
        return workflowNodeQueryService.selectAllNodes(null);
    }


    @Override
    public int save(WorkflowNodeCreateReq nodeCreateReq) {
        String scheduleParamSourceType = nodeCreateReq.getScheduleParamSourceType();
        WorkflowNode workflowNode = WorkflowNode.builder()
                .nodeName(nodeCreateReq.getNodeName())
                .flowId(nodeCreateReq.getFlowId())
                .paramFilterExpr(nodeCreateReq.getParamFilterExpr())
                .nodeType(NodeType.valueOf(nodeCreateReq.getNodeType()))
                .metaInfo(nodeCreateReq.getMetaInfo())
                .keywordField(nodeCreateReq.getKeywordField())
                .scheduleExpr(nodeCreateReq.getScheduleExpr())
                .splitField(nodeCreateReq.getSplitField())
                .remoteExtension(nodeCreateReq.getRemoteExtension())
                .scheduleParamSourceType(scheduleParamSourceType == null || scheduleParamSourceType.isEmpty() ?
                        null : ScheduleParamSourceType.valueOf(scheduleParamSourceType)).build();

        workflowNodeCommandService.createNode(workflowNode);
        return workflowNode.getNodeId();
    }

    @Override
    public int update(WorkflowNodeUpdateReq nodeUpdateReq) {
        NodeType nodeType = StringUtils.hasText(nodeUpdateReq.getNodeType())
                ? NodeType.valueOf(nodeUpdateReq.getNodeType())
                : null;
        WorkflowNode workflowNode = WorkflowNode.builder()
                .nodeId(nodeUpdateReq.getNodeId())
                .nodeName(nodeUpdateReq.getNodeName())
                .flowId(nodeUpdateReq.getFlowId())
                .paramFilterExpr(nodeUpdateReq.getParamFilterExpr())
                .nodeType(nodeType)
                .metaInfo(nodeUpdateReq.getMetaInfo())
                .keywordField(nodeUpdateReq.getKeywordField())
                .splitField(nodeUpdateReq.getSplitField())
                .build();
        return workflowNodeCommandService.updateNodeSelective(workflowNode);
    }

    @Override
    public int updateSchedule(WorkflowNodeScheduleUpdateReq scheduleUpdateReq) {
        String scheduleParamSourceType = StringUtils.hasText(scheduleUpdateReq.getScheduleParamSourceType())
                ? scheduleUpdateReq.getScheduleParamSourceType()
                : null;
        String scheduleExpr = StringUtils.hasText(scheduleUpdateReq.getScheduleExpr())
                ? scheduleUpdateReq.getScheduleExpr()
                : null;
        return workflowNodeCommandService.updateScheduleFields(
                scheduleUpdateReq.getNodeId(),
                scheduleParamSourceType,
                scheduleExpr
        );
    }

    @Override
    @Transactional("repositoryAgentTransactionManager")
    public void deleteByNodeId(Integer nodeId) {
        try {
            if (nodeId == null) {
                throw new IllegalArgumentException("参数不能为空，nodeId 必须存在");
            }
            workflowNodeCommandService.deleteNode(nodeId);
            List<NodeParamConfig> nodeParamConfigs = nodeParamConfigService.findAllNodeParamConfigsUnpaged(nodeId, null);
            nodeParamConfigs.forEach(config -> nodeParamConfigService.deleteById(config.getConfigId()));
        } catch (Exception e) {
            log.error("删除节点失败", e);
            throw new AgentException(AgentErrorCode.CONSOLE_OPERATION_FAILED, "删除节点失败: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional("repositoryAgentTransactionManager")
    public void deleteAllConfigByNodeId(Integer nodeId) {
        WorkflowNode node = workflowNodeQueryService.selectByNodeId(nodeId);
        List<NodeParamConfig> allNodeParamConfigsUnpaged = nodeParamConfigService.findAllNodeParamConfigsUnpaged(nodeId, null);
        allNodeParamConfigsUnpaged.forEach(config -> nodeParamConfigService.deleteById(config.getConfigId()));
        log.info("删除节点参数配置成功，节点名称：{}，共删除{}条配置项.", node.getNodeName(), allNodeParamConfigsUnpaged.size());
    }

    @Override
    @Transactional("repositoryAgentTransactionManager")
    public int batchDeleteByNodeIds(List<Integer> nodeIds) {
        if (nodeIds == null || nodeIds.isEmpty()) {
            throw new IllegalArgumentException("节点ID列表不能为空");
        }

        int deletedCount = 0;
        for (Integer nodeId : nodeIds) {
            if (nodeId != null) {
                try {
                    deleteByNodeId(nodeId);
                    deletedCount++;
                } catch (Exception e) {
                    log.error("删除节点失败，nodeId: {}", nodeId, e);
                }
            }
        }
        return deletedCount;
    }

    @Override
    public NodeParamConfigExportRsp exportNodeParamConfig(Integer nodeId) {
        try {
            // 1. 查询节点的所有参数配置（包括所有处理类型）
            List<NodeParamConfig> paramConfigs = nodeParamConfigService.findAllNodeParamConfigsUnpaged(nodeId, null);

            // 2. 构建导出响应对象
            NodeParamConfigExportRsp response = new NodeParamConfigExportRsp();

            // 3. 设置节点信息
            NodeParamConfigExportRsp.NodeInfo nodeInfo = new NodeParamConfigExportRsp.NodeInfo();
            WorkflowNode workflowNode = workflowNodeQueryService.selectByNodeId(nodeId);
            nodeInfo.setNodeName(workflowNode.getNodeName());
            nodeInfo.setNodeType(workflowNode.getNodeType().name());
            response.setNodeInfo(nodeInfo);

            // 4. 构建树形结构
            List<NodeParamConfigExportRsp.NodeParamConfigExportDTO> rootParams =
                    buildParamTree(paramConfigs, null);

            response.setParamsConfig(rootParams);

            return response;
        } catch (Exception e) {
            log.error("导出节点参数配置失败，nodeId={}", nodeId, e);
            throw new AgentException(AgentErrorCode.CONSOLE_OPERATION_FAILED, "导出节点参数配置失败: " + e.getMessage(), e);
        }
    }

    @Override
    public NodeConfigExportRsp exportNodeConfig(Integer nodeId) {
        try {
            // 1. 查询节点的所有参数配置（包括所有处理类型）
            List<NodeParamConfig> paramConfigs = nodeParamConfigService.findAllNodeParamConfigsUnpaged(nodeId, null);

            // 2. 构建导出响应对象
            NodeConfigExportRsp response = new NodeConfigExportRsp();

            NodeConfigExportRsp.NodeInfo nodeInfo = new NodeConfigExportRsp.NodeInfo();
            WorkflowNode workflowNode = workflowNodeQueryService.selectByNodeId(nodeId);
            nodeInfo.setNodeName(workflowNode.getNodeName());
            nodeInfo.setNodeType(workflowNode.getNodeType().name());
            nodeInfo.setMetaInfo(workflowNode.getMetaInfo());
            nodeInfo.setParamFilterExpr(workflowNode.getParamFilterExpr());
            nodeInfo.setKeywordField(workflowNode.getKeywordField());
            nodeInfo.setSplitField(workflowNode.getSplitField());
            response.setNodeInfo(nodeInfo);

            List<NodeConfigExportRsp.NodeParamConfigDTO> rootParams =
                    buildNodeParamTree(paramConfigs, null);

            response.setParamsConfig(rootParams);

            return response;
        } catch (Exception e) {
            log.error("导出节点参数配置失败，nodeId={}", nodeId, e);
            throw new AgentException(AgentErrorCode.CONSOLE_OPERATION_FAILED, "导出节点参数配置失败: " + e.getMessage(), e);
        }
    }

    /**
     * 构建参数配置的树形结构
     */
    private List<NodeConfigExportRsp.NodeParamConfigDTO> buildNodeParamTree(
            List<NodeParamConfig> paramConfigs, Integer parentId) {

        return paramConfigs.stream()
                .filter(config -> (parentId == null && config.getParentId() == null) ||
                        (parentId != null && parentId.equals(config.getParentId())))
                .map(config -> {
                    NodeConfigExportRsp.NodeParamConfigDTO dto = convertToNodeExportDTO(config);
                    List<NodeConfigExportRsp.NodeParamConfigDTO> childParams =
                            buildNodeParamTree(paramConfigs, config.getConfigId());
                    dto.setChildParam(childParams.isEmpty() ? null : childParams);
                    return dto;
                })
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public void importNodeConfig(MultipartFile file) {
        try {
            if (file == null || file.isEmpty()) {
                throw new IllegalArgumentException("文件不能为空");
            }

            String fileName = file.getOriginalFilename();
            if (!StringUtils.hasText(fileName) || !fileName.toLowerCase().endsWith(".json")) {
                throw new IllegalArgumentException("文件格式不正确，仅支持JSON格式文件");
            }

            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            NodeConfigImportDTO importNodeDTO = JSON.parseObject(content, NodeConfigImportDTO.class);

            // 先创建节点
            NodeConfigImportDTO.NodeInfo nodeInfo = importNodeDTO.getNodeInfo();
            WorkflowNode workflowNode = nodeConvert.importNodeToDomain(nodeInfo);
            workflowNodeCommandService.createNode(workflowNode);
            Integer nodeId = workflowNode.getNodeId();

            List<NodeConfigImportDTO.NodeParamConfigDTO> sourceParams = importNodeDTO.getParamsConfig();
            List<NodeParamConfigImportDTO.NodeParamConfigDTO> targetParams = sourceParams.stream()
                    .map(nodeConvert::convertNodeParamConfig)
                    .collect(Collectors.toList());
            nodeParamConfigService.importNodeParamConfig(nodeId, targetParams);
        } catch (IOException e) {
            log.error("导入节点配置失败：", e);
        }
    }

    @Override
    public void batchImportNodeConfig(MultipartFile file) {
        try {
            if (file == null || file.isEmpty()) {
                throw new IllegalArgumentException("文件不能为空");
            }

            String fileName = file.getOriginalFilename();
            if (!StringUtils.hasText(fileName) || !fileName.toLowerCase().endsWith(".json")) {
                throw new IllegalArgumentException("文件格式不正确，仅支持JSON格式文件");
            }

            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            NodeConfigImportDTO singleImport = null;
            List<NodeConfigImportDTO> importList = null;

            try {
                singleImport = JSON.parseObject(content, NodeConfigImportDTO.class);
            } catch (Exception e) {
                try {
                    importList = JSON.parseArray(content, NodeConfigImportDTO.class);
                } catch (Exception e2) {
                    throw new IllegalArgumentException("JSON文件格式不正确，既不是单个节点配置也不是节点配置数组");
                }
            }

            if (singleImport != null) {
                processSingleNodeImport(singleImport);
                log.info("批量导入节点配置成功，共处理1个节点");
            } else if (importList != null && !importList.isEmpty()) {
                for (NodeConfigImportDTO importNodeDTO : importList) {
                    processSingleNodeImport(importNodeDTO);
                }
                log.info("批量导入节点配置成功，共处理{}个节点", importList.size());
            } else {
                throw new IllegalArgumentException("JSON文件内容为空或格式不正确");
            }
        } catch (IOException e) {
            log.error("批量导入节点配置失败：", e);
            throw new AgentException(AgentErrorCode.CONSOLE_OPERATION_FAILED, "批量导入节点配置失败: " + e.getMessage(), e);
        }
    }

    /**
     * 处理单个节点的导入
     */
    private void processSingleNodeImport(NodeConfigImportDTO importNodeDTO) {
        NodeConfigImportDTO.NodeInfo nodeInfo = importNodeDTO.getNodeInfo();
        WorkflowNode workflowNode = nodeConvert.importNodeToDomain(nodeInfo);
        workflowNodeCommandService.createNode(workflowNode);
        Integer nodeId = workflowNode.getNodeId();

        List<NodeConfigImportDTO.NodeParamConfigDTO> sourceParams = importNodeDTO.getParamsConfig();
        List<NodeParamConfigImportDTO.NodeParamConfigDTO> targetParams = sourceParams.stream()
                .map(nodeConvert::convertNodeParamConfig)
                .collect(Collectors.toList());
        nodeParamConfigService.importNodeParamConfig(nodeId, targetParams);
    }

    /**
     * 构建参数配置的树形结构
     */
    private List<NodeParamConfigExportRsp.NodeParamConfigExportDTO> buildParamTree(
            List<NodeParamConfig> paramConfigs, Integer parentId) {

        return paramConfigs.stream()
                .filter(config -> (parentId == null && config.getParentId() == null) ||
                        (parentId != null && parentId.equals(config.getParentId())))
                .map(config -> {
                    NodeParamConfigExportRsp.NodeParamConfigExportDTO dto = convertToExportDTO(config);
                    List<NodeParamConfigExportRsp.NodeParamConfigExportDTO> childParams =
                            buildParamTree(paramConfigs, config.getConfigId());
                    dto.setChildParam(childParams.isEmpty() ? null : childParams);
                    return dto;
                })
                .collect(java.util.stream.Collectors.toList());
    }

    private NodeParamConfigExportRsp.NodeParamConfigExportDTO convertToExportDTO(NodeParamConfig config) {
        NodeParamConfigExportRsp.NodeParamConfigExportDTO dto =
                new NodeParamConfigExportRsp.NodeParamConfigExportDTO();

        dto.setProcessType(config.getProcessType());
        dto.setSourceParamKey(config.getSourceParamKey());
        dto.setSourceParamType(config.getSourceParamType());
        dto.setParamDesc(config.getParamDesc());
        dto.setTargetParamKey(config.getTargetParamKey());
        dto.setTargetParamType(config.getTargetParamType());
        dto.setKeepSingleElementKey(config.getKeepSingleElementKey());
        dto.setSort(config.getSort());
        dto.setMappingType(config.getMappingType());
        dto.setMappingSource(config.getMappingSource());
        dto.setMappingRule(config.getMappingRule());

        return dto;
    }

    private NodeConfigExportRsp.NodeParamConfigDTO convertToNodeExportDTO(NodeParamConfig config) {
        NodeConfigExportRsp.NodeParamConfigDTO dto =
                new NodeConfigExportRsp.NodeParamConfigDTO();

        dto.setProcessType(config.getProcessType());
        dto.setSourceParamKey(config.getSourceParamKey());
        dto.setSourceParamType(config.getSourceParamType());
        dto.setParamDesc(config.getParamDesc());
        dto.setTargetParamKey(config.getTargetParamKey());
        dto.setTargetParamType(config.getTargetParamType());
        dto.setKeepSingleElementKey(config.getKeepSingleElementKey());
        dto.setSort(config.getSort());
        dto.setMappingType(config.getMappingType());
        dto.setMappingSource(config.getMappingSource());
        dto.setMappingRule(config.getMappingRule());

        return dto;
    }

    @Override
    public List<InterfaceWorkflowNodeView> getDownstreamNodes(Integer nodeId) {
        WorkflowNode currentNode = workflowNodeQueryService.selectByNodeId(nodeId);
        if (currentNode == null || currentNode.getScheduleExpr() == null || currentNode.getScheduleExpr().trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<Integer> downstreamNodeIds = SpelUtils.extractNodeIdsFromScheduleExpr(currentNode.getScheduleExpr());
        if (downstreamNodeIds.isEmpty()) {
            return Collections.emptyList();
        }

        return downstreamNodeIds.stream()
                .map(id -> {
                    WorkflowNode node = workflowNodeQueryService.selectByNodeId(id);
                    return node != null ? WorkflowNodeAssembler.toView(node) : null;
                })
                .filter(view -> view != null)
                .collect(Collectors.toList());
    }

}
