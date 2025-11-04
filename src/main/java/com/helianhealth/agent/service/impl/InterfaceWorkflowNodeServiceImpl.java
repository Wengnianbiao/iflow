package com.helianhealth.agent.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.helianhealth.agent.common.PageList;
import com.helianhealth.agent.controller.workflow.reponse.InterfaceWorkflowNodeView;
import com.helianhealth.agent.controller.workflow.reponse.NodeParamConfigExportResponse;
import com.helianhealth.agent.controller.workflow.request.WorkflowNodeCreateReq;
import com.helianhealth.agent.controller.workflow.request.WorkflowNodeUpdateReq;
import com.helianhealth.agent.enums.NodeType;
import com.helianhealth.agent.enums.ScheduleParamSourceType;
import com.helianhealth.agent.mapper.agent.InterfaceFlowNodeMapper;
import com.helianhealth.agent.model.convert.WorkflowNodeConvert;
import com.helianhealth.agent.model.domain.InterfaceWorkflowNode;
import com.helianhealth.agent.model.domain.NodeParamConfigDO;
import com.helianhealth.agent.model.po.InterfaceWorkflowNodePO;
import com.helianhealth.agent.remote.InterfaceRemoteInvoker;
import com.helianhealth.agent.service.InterfaceWorkflowNodeService;
import com.helianhealth.agent.service.NodeParamConfigService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Slf4j
public class InterfaceWorkflowNodeServiceImpl implements InterfaceWorkflowNodeService {

    private final InterfaceFlowNodeMapper flowNodeMapper;
    private final WorkflowNodeConvert nodeConvert;
    private final InterfaceRemoteInvoker interfaceRemoteInvoker = ExtensionLoader.getExtensionLoader(InterfaceRemoteInvoker.class).getAdaptiveExtension();
    private final NodeParamConfigService nodeParamConfigService;

    @Autowired
    public InterfaceWorkflowNodeServiceImpl(
            InterfaceFlowNodeMapper flowNodeMapper,
            WorkflowNodeConvert nodeConvert,
            NodeParamConfigService nodeParamConfigService) {
        this.flowNodeMapper = flowNodeMapper;
        this.nodeConvert = nodeConvert;
        this.nodeParamConfigService = nodeParamConfigService;
    }


    @Override
    public InterfaceWorkflowNodeView selectByNodeId(Integer flowId) {
        InterfaceWorkflowNodePO workflowNodePO = flowNodeMapper.selectByNodeId(flowId);
        return nodeConvert.poConvertToView(workflowNodePO);
    }

    @Override
    public PageList<InterfaceWorkflowNodeView> getAllNodes(Integer flowId, int pageNum, int pageSize) {
        try {
            PageHelper.startPage(pageNum, pageSize);
            // 将查询结果转换为 PageInfo 对象
            List<InterfaceWorkflowNodePO> flowNodes = flowNodeMapper.selectAllNodes(flowId);
            List<InterfaceWorkflowNodeView> workflowNodeViews = flowNodes.stream().
                    map(nodeConvert::poConvertToView).
                    collect(Collectors.toList());
            PageInfo<InterfaceWorkflowNodeView> pageInfo = new PageInfo<>(workflowNodeViews);

            // 构建 PageList 对象
            PageList<InterfaceWorkflowNodeView> pageList = new PageList<>();
            pageList.setRows(workflowNodeViews);
            pageList.setTotal(pageInfo.getTotal());
            pageList.setPageNum(pageInfo.getPageNum());
            pageList.setPageSize(pageInfo.getPageSize());
            return pageList;
        } catch (Exception e) {
            log.error("获取工作流节点列表失败", e);
            throw new RuntimeException("获取工作流节点列表失败: " + e.getMessage());
        }
    }

    @Override
    public List<InterfaceWorkflowNodeView> getAllNodesWithoutPaged() {
        List<InterfaceWorkflowNodePO> interfaceWorkflowNodePOS = flowNodeMapper.selectAllNodes(null);

        return interfaceWorkflowNodePOS.stream().
                map(nodeConvert::poConvertToView).
                collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> executeFlowNode(InterfaceWorkflowNode flowNode, Map<String, Object> businessData) {
        // 节点处理就是一个远程调用
        return interfaceRemoteInvoker.remoteInvoke(flowNode, businessData);
    }

    @Override
    public int save(WorkflowNodeCreateReq nodeCreateReq) {
        String scheduleParamSourceType = nodeCreateReq.getScheduleParamSourceType();
        InterfaceWorkflowNodePO workflowNodePO = InterfaceWorkflowNodePO.builder()
                .nodeName(nodeCreateReq.getNodeName())
                .flowId(nodeCreateReq.getFlowId())
                .paramFilterExpr(nodeCreateReq.getParamFilterExpr())
                .nodeType(NodeType.valueOf(nodeCreateReq.getNodeType()))
                .metaInfo(nodeCreateReq.getMetaInfo())
                .keywordField(nodeCreateReq.getKeywordField())
                .scheduleExpr(nodeCreateReq.getScheduleExpr())
                .remoteExtension(nodeCreateReq.getRemoteExtension())
                .scheduleParamSourceType(scheduleParamSourceType == null || scheduleParamSourceType.isEmpty() ?
                        null : ScheduleParamSourceType.valueOf(scheduleParamSourceType)).build();

        return flowNodeMapper.createNode(workflowNodePO);
    }

    @Override
    public int update(WorkflowNodeUpdateReq nodeUpdateReq) {
        String scheduleParamSourceType = nodeUpdateReq.getScheduleParamSourceType();
        InterfaceWorkflowNodePO workflowNodePO = InterfaceWorkflowNodePO.builder()
                .nodeId(nodeUpdateReq.getNodeId())
                .nodeName(nodeUpdateReq.getNodeName())
                .flowId(nodeUpdateReq.getFlowId())
                .paramFilterExpr(nodeUpdateReq.getParamFilterExpr())
                .nodeType(NodeType.valueOf(nodeUpdateReq.getNodeType()))
                .metaInfo(nodeUpdateReq.getMetaInfo())
                .keywordField(nodeUpdateReq.getKeywordField())
                .remoteExtension(nodeUpdateReq.getRemoteExtension())
                .scheduleExpr(nodeUpdateReq.getScheduleExpr())
                .scheduleParamSourceType(scheduleParamSourceType == null || scheduleParamSourceType.isEmpty() ?
                        null : ScheduleParamSourceType.valueOf(scheduleParamSourceType)).build();
        return flowNodeMapper.updateNode(workflowNodePO);
    }

    @Override
    public void deleteByNodeId(Integer nodeId) {
        try {
            // 验证参数
            if (nodeId == null) {
                throw new IllegalArgumentException("参数不能为空，nodeId 必须存在");
            }
            // 执行删除操作
            flowNodeMapper.deleteByPrimaryKey(nodeId);
        } catch (Exception e) {
            log.error("删除节点失败", e);
            throw new RuntimeException("删除节点失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional("agentTransactionManager")
    public void deleteAllConfigByNodeId(Integer nodeId) {
        InterfaceWorkflowNodePO interfaceWorkflowNodeDO = flowNodeMapper.selectByNodeId(nodeId);
        List<NodeParamConfigDO> allNodeParamConfigsUnpaged = nodeParamConfigService.findAllNodeParamConfigsUnpaged(nodeId, null);
        allNodeParamConfigsUnpaged.forEach(nodeParamConfigDO -> nodeParamConfigService.deleteById(nodeParamConfigDO.getConfigId()));
        log.info("删除节点参数配置成功，节点名称：{}，共删除{}条配置项.", interfaceWorkflowNodeDO.getNodeName(), allNodeParamConfigsUnpaged.size());
    }

    @Override
    public NodeParamConfigExportResponse exportNodeParamConfig(Integer nodeId) {
        try {
            // 1. 查询节点的所有参数配置（包括所有处理类型）
            List<NodeParamConfigDO> paramConfigs = nodeParamConfigService.findAllNodeParamConfigsUnpaged(nodeId, null);

            // 2. 构建导出响应对象
            NodeParamConfigExportResponse response = new NodeParamConfigExportResponse();

            // 3. 设置节点信息
            NodeParamConfigExportResponse.NodeInfo nodeInfo = new NodeParamConfigExportResponse.NodeInfo();
            InterfaceWorkflowNodePO workflowNodePO = flowNodeMapper.selectByNodeId(nodeId);
            nodeInfo.setNodeName(workflowNodePO.getNodeName());
            nodeInfo.setNodeType(workflowNodePO.getNodeType().name());
            // 注意：如果需要完整的节点信息（如节点名称等），需要从其他服务或表中获取
            response.setNodeInfo(nodeInfo);

            // 4. 构建树形结构
            List<NodeParamConfigExportResponse.NodeParamConfigExportDTO> rootParams =
                    buildParamTree(paramConfigs, null);

            response.setParamsConfig(rootParams);

            return response;
        } catch (Exception e) {
            log.error("导出节点参数配置失败，nodeId={}", nodeId, e);
            throw new RuntimeException("导出节点参数配置失败: " + e.getMessage());
        }
    }

    /**
     * 构建参数配置的树形结构
     * @param paramConfigs 所有参数配置
     * @param parentId 父节点ID，null表示根节点
     * @return 树形结构的参数配置列表
     */
    private List<NodeParamConfigExportResponse.NodeParamConfigExportDTO> buildParamTree(
            List<NodeParamConfigDO> paramConfigs, Integer parentId) {

        return paramConfigs.stream()
                .filter(config -> (parentId == null && config.getParentId() == null) ||
                        (parentId != null && parentId.equals(config.getParentId())))
                .map(config -> {
                    NodeParamConfigExportResponse.NodeParamConfigExportDTO dto = convertToExportDTO(config);
                    // 递归构建子节点
                    List<NodeParamConfigExportResponse.NodeParamConfigExportDTO> childParams =
                            buildParamTree(paramConfigs, config.getConfigId());
                    dto.setChildParam(childParams.isEmpty() ? null : childParams);
                    return dto;
                })
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 将NodeParamConfigDO转换为NodeParamConfigExportDTO（不包含层级关系）
     * @param config NodeParamConfigDO对象
     * @return NodeParamConfigExportDTO对象
     */
    private NodeParamConfigExportResponse.NodeParamConfigExportDTO convertToExportDTO(NodeParamConfigDO config) {
        NodeParamConfigExportResponse.NodeParamConfigExportDTO dto =
                new NodeParamConfigExportResponse.NodeParamConfigExportDTO();

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
}
