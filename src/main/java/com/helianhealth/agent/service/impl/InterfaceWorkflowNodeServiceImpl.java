package com.helianhealth.agent.service.impl;

import com.alibaba.fastjson2.JSON;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.helianhealth.agent.common.PageList;
import com.helianhealth.agent.controller.workflow.reponse.InterfaceWorkflowNodeView;
import com.helianhealth.agent.controller.workflow.reponse.NodeConfigExportRsp;
import com.helianhealth.agent.controller.workflow.reponse.NodeParamConfigExportRsp;
import com.helianhealth.agent.controller.workflow.request.WorkflowNodeCreateReq;
import com.helianhealth.agent.controller.workflow.request.WorkflowNodeUpdateReq;
import com.helianhealth.agent.enums.NodeType;
import com.helianhealth.agent.enums.ScheduleParamSourceType;
import com.helianhealth.agent.mapper.agent.InterfaceFlowNodeMapper;
import com.helianhealth.agent.model.convert.WorkflowNodeConvert;
import com.helianhealth.agent.model.domain.InterfaceWorkflowNode;
import com.helianhealth.agent.model.domain.NodeParamConfigDO;
import com.helianhealth.agent.model.dto.NodeConfigImportDTO;
import com.helianhealth.agent.model.dto.NodeParamConfigImportDTO;
import com.helianhealth.agent.model.po.InterfaceWorkflowNodePO;
import com.helianhealth.agent.remote.InterfaceRemoteInvoker;
import com.helianhealth.agent.service.InterfaceWorkflowNodeService;
import com.helianhealth.agent.service.NodeParamConfigService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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

            PageInfo<InterfaceWorkflowNodePO> pageInfo = new PageInfo<>(flowNodes);

            List<InterfaceWorkflowNodeView> workflowNodeViews = flowNodes.stream().
                    map(nodeConvert::poConvertToView).
                    collect(Collectors.toList());

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
                .splitField(nodeCreateReq.getSplitField())
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
                .splitField(nodeUpdateReq.getSplitField())
                .remoteExtension(nodeUpdateReq.getRemoteExtension())
                .scheduleExpr(nodeUpdateReq.getScheduleExpr())
                .scheduleParamSourceType(scheduleParamSourceType == null || scheduleParamSourceType.isEmpty() ?
                        null : ScheduleParamSourceType.valueOf(scheduleParamSourceType)).build();
        return flowNodeMapper.updateNode(workflowNodePO);
    }

    @Override
    @Transactional("agentTransactionManager")
    public void deleteByNodeId(Integer nodeId) {
        try {
            // 验证参数
            if (nodeId == null) {
                throw new IllegalArgumentException("参数不能为空，nodeId 必须存在");
            }
            // 执行删除操作
            flowNodeMapper.deleteByPrimaryKey(nodeId);
            List<NodeParamConfigDO> nodeParamConfigs = nodeParamConfigService.findAllNodeParamConfigsUnpaged(nodeId, null);
            nodeParamConfigs.forEach(nodeParamConfigDO -> nodeParamConfigService.deleteById(nodeParamConfigDO.getConfigId()));
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
    public NodeParamConfigExportRsp exportNodeParamConfig(Integer nodeId) {
        try {
            // 1. 查询节点的所有参数配置（包括所有处理类型）
            List<NodeParamConfigDO> paramConfigs = nodeParamConfigService.findAllNodeParamConfigsUnpaged(nodeId, null);

            // 2. 构建导出响应对象
            NodeParamConfigExportRsp response = new NodeParamConfigExportRsp();

            // 3. 设置节点信息
            NodeParamConfigExportRsp.NodeInfo nodeInfo = new NodeParamConfigExportRsp.NodeInfo();
            InterfaceWorkflowNodePO workflowNodePO = flowNodeMapper.selectByNodeId(nodeId);
            nodeInfo.setNodeName(workflowNodePO.getNodeName());
            nodeInfo.setNodeType(workflowNodePO.getNodeType().name());
            // 注意：如果需要完整的节点信息（如节点名称等），需要从其他服务或表中获取
            response.setNodeInfo(nodeInfo);

            // 4. 构建树形结构
            List<NodeParamConfigExportRsp.NodeParamConfigExportDTO> rootParams =
                    buildParamTree(paramConfigs, null);

            response.setParamsConfig(rootParams);

            return response;
        } catch (Exception e) {
            log.error("导出节点参数配置失败，nodeId={}", nodeId, e);
            throw new RuntimeException("导出节点参数配置失败: " + e.getMessage());
        }
    }

    @Override
    public NodeConfigExportRsp exportNodeConfig(Integer nodeId) {
        try {
            // 1. 查询节点的所有参数配置（包括所有处理类型）
            List<NodeParamConfigDO> paramConfigs = nodeParamConfigService.findAllNodeParamConfigsUnpaged(nodeId, null);

            // 2. 构建导出响应对象
            NodeConfigExportRsp response = new NodeConfigExportRsp();

            NodeConfigExportRsp.NodeInfo nodeInfo = new NodeConfigExportRsp.NodeInfo();
            InterfaceWorkflowNodePO workflowNodePO = flowNodeMapper.selectByNodeId(nodeId);
            nodeInfo.setNodeName(workflowNodePO.getNodeName());
            nodeInfo.setNodeType(workflowNodePO.getNodeType().name());
            nodeInfo.setMetaInfo(workflowNodePO.getMetaInfo());
            nodeInfo.setParamFilterExpr(workflowNodePO.getParamFilterExpr());
            nodeInfo.setKeywordField(workflowNodePO.getKeywordField());
            response.setNodeInfo(nodeInfo);

            List<NodeConfigExportRsp.NodeParamConfigDTO> rootParams =
                    buildNodeParamTree(paramConfigs, null);

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
    private List<NodeConfigExportRsp.NodeParamConfigDTO> buildNodeParamTree(
            List<NodeParamConfigDO> paramConfigs, Integer parentId) {

        return paramConfigs.stream()
                .filter(config -> (parentId == null && config.getParentId() == null) ||
                        (parentId != null && parentId.equals(config.getParentId())))
                .map(config -> {
                    NodeConfigExportRsp.NodeParamConfigDTO dto = convertToNodeExportDTO(config);
                    // 递归构建子节点
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
            // 1. 校验文件格式
            if (file == null || file.isEmpty()) {
                throw new IllegalArgumentException("文件不能为空");
            }

            String fileName = file.getOriginalFilename();
            if (!StringUtils.hasText(fileName) || !fileName.toLowerCase().endsWith(".json")) {
                throw new IllegalArgumentException("文件格式不正确，仅支持JSON格式文件");
            }

            // 2. 解析JSON文件
            String content = new String(file.getBytes());
            NodeConfigImportDTO importNodeDTO = JSON.parseObject(content, NodeConfigImportDTO.class);

            // 3.1 先创建节点
            NodeConfigImportDTO.NodeInfo nodeInfo = importNodeDTO.getNodeInfo();
            InterfaceWorkflowNodePO workflowNodePO = nodeConvert.importNodeToPo(nodeInfo);
            flowNodeMapper.createNode(workflowNodePO);
            Integer nodeId = workflowNodePO.getNodeId();

            List<NodeConfigImportDTO.NodeParamConfigDTO> sourceParams = importNodeDTO.getParamsConfig();
            List<NodeParamConfigImportDTO.NodeParamConfigDTO> targetParams = sourceParams.stream()
                    .map(nodeConvert::convertNodeParamConfig)
                    .collect(Collectors.toList());
            nodeParamConfigService.importNodeParamConfig(nodeId, targetParams);
        } catch (IOException e) {
            log.error("导入节点配置失败：", e);
        }
    }

    /**
     * 构建参数配置的树形结构
     * @param paramConfigs 所有参数配置
     * @param parentId 父节点ID，null表示根节点
     * @return 树形结构的参数配置列表
     */
    private List<NodeParamConfigExportRsp.NodeParamConfigExportDTO> buildParamTree(
            List<NodeParamConfigDO> paramConfigs, Integer parentId) {

        return paramConfigs.stream()
                .filter(config -> (parentId == null && config.getParentId() == null) ||
                        (parentId != null && parentId.equals(config.getParentId())))
                .map(config -> {
                    NodeParamConfigExportRsp.NodeParamConfigExportDTO dto = convertToExportDTO(config);
                    // 递归构建子节点
                    List<NodeParamConfigExportRsp.NodeParamConfigExportDTO> childParams =
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
    private NodeParamConfigExportRsp.NodeParamConfigExportDTO convertToExportDTO(NodeParamConfigDO config) {
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

    /**
     * 将NodeParamConfigDO转换为NodeParamConfigExportDTO（不包含层级关系）
     * @param config NodeParamConfigDO对象
     * @return NodeParamConfigExportDTO对象
     */
    private NodeConfigExportRsp.NodeParamConfigDTO convertToNodeExportDTO(NodeParamConfigDO config) {
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
}
