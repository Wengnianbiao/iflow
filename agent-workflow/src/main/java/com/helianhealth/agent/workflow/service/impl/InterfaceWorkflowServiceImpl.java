package com.helianhealth.agent.workflow.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.helianhealth.agent.common.base.PageList;
import com.helianhealth.agent.workflow.controller.reponse.InterfaceWorkflowNodeView;
import com.helianhealth.agent.workflow.controller.reponse.WorkflowFullExportRsp;
import com.helianhealth.agent.workflow.controller.request.WorkflowNodeCreateReq;
import com.helianhealth.agent.workflow.mapper.agent.InterfaceWorkflowMapper;
import com.helianhealth.agent.workflow.model.domain.InterfaceWorkflowDO;
import com.helianhealth.agent.workflow.model.domain.NodeParamConfigDO;
import com.helianhealth.agent.workflow.model.dto.NodeParamConfigImportDTO;
import com.helianhealth.agent.workflow.service.InterfaceWorkflowNodeService;
import com.helianhealth.agent.workflow.service.InterfaceWorkflowService;
import com.helianhealth.agent.workflow.service.NodeParamConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class InterfaceWorkflowServiceImpl implements InterfaceWorkflowService {

    private final InterfaceWorkflowMapper workflowMapper;
    private final InterfaceWorkflowNodeService workflowNodeService;
    private final NodeParamConfigService nodeParamConfigService;

    @Override
    public Optional<InterfaceWorkflowDO> findByInterfaceUri(String apiUri) {
        return Optional.ofNullable(workflowMapper.selectByInterfaceUri(apiUri));
    }

    @Override
    public int save(InterfaceWorkflowDO workflow) {
        // 验证参数
        if (workflow == null) {
            throw new IllegalArgumentException("参数不能为空");
        }

        return workflowMapper.createWorkflow(workflow);
    }

    @Override
    public PageList<InterfaceWorkflowDO> getAllWorkflows(int pageNum, int pageSize) {
        try {
            PageHelper.startPage(pageNum, pageSize);
            // 将查询结果转换为 PageInfo 对象
            List<InterfaceWorkflowDO> workflows = workflowMapper.selectAllWorkflows();
            PageInfo<InterfaceWorkflowDO> pageInfo = new PageInfo<>(workflows);

            // 构建 PageList 对象
            PageList<InterfaceWorkflowDO> pageList = new PageList<>();
            pageList.setRows(workflows);
            pageList.setTotal(pageInfo.getTotal());
            pageList.setPageNum(pageInfo.getPageNum());
            pageList.setPageSize(pageInfo.getPageSize());
            return pageList;
        } catch (Exception e) {
            log.error("获取工作流列表失败", e);
            throw new RuntimeException("获取工作流列表失败: " + e.getMessage());
        }
    }

    @Override
    public List<InterfaceWorkflowDO> getAllWorkflowsWithoutPaged() {
        return workflowMapper.selectAllWorkflows();
    }

    @Override
    public int update(InterfaceWorkflowDO interfaceInstanceDO) {
        return workflowMapper.updateByPrimaryKeySelective(interfaceInstanceDO);
    }

    @Override
    public void deleteByFlowId(Integer flowId) {
        try {
            // 验证参数
            if (flowId == null) {
                throw new IllegalArgumentException("参数不能为空，flowId 必须存在");
            }

            // 执行删除操作
            workflowMapper.deleteByPrimaryKey(flowId);
        } catch (Exception e) {
            log.error("删除工作流失败", e);
            throw new RuntimeException("删除工作流失败: " + e.getMessage());
        }
    }

    @Override
    public InterfaceWorkflowDO selectByFlowId(Integer flowId) {
        return workflowMapper.selectByPrimaryKey(flowId);
    }

    @Override
    public WorkflowFullExportRsp exportWorkflowFull(Integer flowId) {
        // 1. 查询工作流
        InterfaceWorkflowDO workflow = workflowMapper.selectByPrimaryKey(flowId);
        if (workflow == null) {
            throw new IllegalArgumentException("工作流不存在: " + flowId);
        }

        WorkflowFullExportRsp response = new WorkflowFullExportRsp();

        // 2. 构建工作流信息
        WorkflowFullExportRsp.WorkflowInfo workflowInfo = new WorkflowFullExportRsp.WorkflowInfo();
        workflowInfo.setFlowName(workflow.getFlowName());
        workflowInfo.setInterfaceUri(workflow.getInterfaceUri());
        workflowInfo.setContentType(workflow.getContentType() != null ? workflow.getContentType().name() : null);
        workflowInfo.setContentMetaInfo(workflow.getContentMetaInfo());
        workflowInfo.setStatus(workflow.getStatus());
        response.setWorkflowInfo(workflowInfo);

        // 3. 查询关联的节点
        List<Integer> firstFlowNodes = workflow.getFirstFlowNodes();
        if (firstFlowNodes == null || firstFlowNodes.isEmpty()) {
            response.setNodes(new ArrayList<>());
            workflowInfo.setFirstNodeIndexes(new ArrayList<>());
            return response;
        }

        // 4. 构建节点配置列表
        List<WorkflowFullExportRsp.NodeFullConfig> nodeConfigs = new ArrayList<>();
        Map<Integer, Integer> nodeIdToIndex = new HashMap<>();

        for (Integer nodeId : firstFlowNodes) {
            InterfaceWorkflowNodeView node = workflowNodeService.selectByNodeId(nodeId);
            if (node != null) {
                WorkflowFullExportRsp.NodeFullConfig nodeConfig = new WorkflowFullExportRsp.NodeFullConfig();
                nodeConfig.setNodeName(node.getNodeName());
                nodeConfig.setNodeType(node.getNodeType());
                nodeConfig.setParamFilterExpr(node.getParamFilterExpr());
                nodeConfig.setMetaInfo(node.getMetaInfo());
                nodeConfig.setKeywordField(node.getKeywordField());
                nodeConfig.setSplitField(node.getSplitField());
                nodeConfig.setScheduleExpr(node.getScheduleExpr());
                nodeConfig.setScheduleParamSourceType(node.getScheduleParamSourceType());
                nodeConfig.setRemoteExtension(node.getRemoteExtension());

                // 5. 查询节点的参数配置并构建树形结构
                List<NodeParamConfigDO> paramConfigs = nodeParamConfigService.findAllNodeParamConfigsUnpaged(nodeId, null);
                List<WorkflowFullExportRsp.ParamConfigDTO> paramTree = buildParamTree(paramConfigs, null);
                nodeConfig.setParamsConfig(paramTree);

                nodeIdToIndex.put(nodeId, nodeConfigs.size());
                nodeConfigs.add(nodeConfig);
            }
        }

        response.setNodes(nodeConfigs);

        // 6. 设置首节点索引
        List<Integer> firstNodeIndexes = firstFlowNodes.stream()
                .map(nodeIdToIndex::get)
                .filter(idx -> idx != null)
                .collect(Collectors.toList());
        workflowInfo.setFirstNodeIndexes(firstNodeIndexes);

        return response;
    }

    /**
     * 构建参数配置树形结构
     */
    private List<WorkflowFullExportRsp.ParamConfigDTO> buildParamTree(
            List<NodeParamConfigDO> paramConfigs, Integer parentId) {
        return paramConfigs.stream()
                .filter(config -> (parentId == null && config.getParentId() == null) ||
                        (parentId != null && parentId.equals(config.getParentId())))
                .map(config -> {
                    WorkflowFullExportRsp.ParamConfigDTO dto = new WorkflowFullExportRsp.ParamConfigDTO();
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

                    List<WorkflowFullExportRsp.ParamConfigDTO> children = buildParamTree(paramConfigs, config.getConfigId());
                    dto.setChildParam(children.isEmpty() ? null : children);
                    return dto;
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional("workflowAgentTransactionManager")
    public Integer importWorkflowFull(WorkflowFullExportRsp importData) {
        // 1. 创建工作流
        WorkflowFullExportRsp.WorkflowInfo workflowInfo = importData.getWorkflowInfo();
        InterfaceWorkflowDO workflow = InterfaceWorkflowDO.builder()
                .flowName(workflowInfo.getFlowName())
                .interfaceUri(workflowInfo.getInterfaceUri())
                .contentType(workflowInfo.getContentType() != null ?
                        com.helianhealth.agent.common.enums.WorkflowContentType.valueOf(workflowInfo.getContentType()) : null)
                .contentMetaInfo(workflowInfo.getContentMetaInfo())
                .firstFlowNodes(new ArrayList<>())
                .status(workflowInfo.getStatus())
                .build();
        workflowMapper.createWorkflow(workflow);
        Integer flowId = workflow.getFlowId();

        // 2. 创建节点并记录新nodeId
        List<WorkflowFullExportRsp.NodeFullConfig> nodes = importData.getNodes();
        List<Integer> newNodeIds = new ArrayList<>();

        if (nodes != null && !nodes.isEmpty()) {
            for (WorkflowFullExportRsp.NodeFullConfig nodeConfig : nodes) {
                // 创建节点
                WorkflowNodeCreateReq createReq = new WorkflowNodeCreateReq();
                createReq.setNodeName(nodeConfig.getNodeName());
                createReq.setFlowId(flowId);
                createReq.setNodeType(nodeConfig.getNodeType() != null ? nodeConfig.getNodeType().name() : null);
                createReq.setParamFilterExpr(nodeConfig.getParamFilterExpr());
                createReq.setMetaInfo(nodeConfig.getMetaInfo());
                createReq.setKeywordField(nodeConfig.getKeywordField());
                createReq.setSplitField(nodeConfig.getSplitField());
                createReq.setScheduleExpr(nodeConfig.getScheduleExpr());
                createReq.setScheduleParamSourceType(nodeConfig.getScheduleParamSourceType() != null ?
                        nodeConfig.getScheduleParamSourceType().name() : null);
                createReq.setRemoteExtension(nodeConfig.getRemoteExtension());

                int nodeId = workflowNodeService.save(createReq);
                newNodeIds.add(nodeId);

                // 导入参数配置
                List<WorkflowFullExportRsp.ParamConfigDTO> paramsConfig = nodeConfig.getParamsConfig();
                if (paramsConfig != null && !paramsConfig.isEmpty()) {
                    List<NodeParamConfigImportDTO.NodeParamConfigDTO> importParams = convertToImportDTO(paramsConfig);
                    nodeParamConfigService.importNodeParamConfig(nodeId, importParams);
                }
            }
        }

        // 3. 更新工作流的首节点
        List<Integer> firstNodeIndexes = workflowInfo.getFirstNodeIndexes();
        if (firstNodeIndexes != null && !firstNodeIndexes.isEmpty()) {
            List<Integer> firstFlowNodes = firstNodeIndexes.stream()
                    .filter(idx -> idx >= 0 && idx < newNodeIds.size())
                    .map(newNodeIds::get)
                    .collect(Collectors.toList());
            workflow.setFlowId(flowId);
            workflow.setFirstFlowNodes(firstFlowNodes);
            workflowMapper.updateByPrimaryKeySelective(workflow);
        }

        return flowId;
    }

    /**
     * 将导出的参数配置转换为导入DTO
     */
    private List<NodeParamConfigImportDTO.NodeParamConfigDTO> convertToImportDTO(
            List<WorkflowFullExportRsp.ParamConfigDTO> params) {
        return params.stream().map(p -> {
            NodeParamConfigImportDTO.NodeParamConfigDTO dto = new NodeParamConfigImportDTO.NodeParamConfigDTO();
            dto.setProcessType(p.getProcessType());
            dto.setSourceParamKey(p.getSourceParamKey());
            dto.setSourceParamType(p.getSourceParamType());
            dto.setParamDesc(p.getParamDesc());
            dto.setTargetParamKey(p.getTargetParamKey());
            dto.setTargetParamType(p.getTargetParamType());
            dto.setKeepSingleElementKey(p.getKeepSingleElementKey());
            dto.setSort(p.getSort());
            dto.setMappingType(p.getMappingType());
            dto.setMappingSource(p.getMappingSource());
            dto.setMappingRule(p.getMappingRule());
            if (p.getChildParam() != null && !p.getChildParam().isEmpty()) {
                dto.setChildParam(convertToImportDTO(p.getChildParam()));
            }
            return dto;
        }).collect(Collectors.toList());
    }
}
