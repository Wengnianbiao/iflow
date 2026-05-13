package com.helianhealth.agent.repository.service.impl;

import com.helianhealth.agent.common.utils.JsonUtils;
import com.helianhealth.agent.repository.mapper.agent.InterfaceFlowNodeMapper;
import com.helianhealth.agent.repository.model.assembler.WorkflowNodeAssembler;
import com.helianhealth.agent.repository.model.domain.WorkflowNode;
import com.helianhealth.agent.repository.model.po.InterfaceWorkflowNodePO;
import com.helianhealth.agent.repository.service.WorkflowNodeCommandService;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 工作流节点写操作服务实现
 */
@Service
public class WorkflowNodeCommandServiceImpl implements WorkflowNodeCommandService {

    private final InterfaceFlowNodeMapper flowNodeMapper;

    public WorkflowNodeCommandServiceImpl(InterfaceFlowNodeMapper flowNodeMapper) {
        this.flowNodeMapper = flowNodeMapper;
    }

    @Override
    public void createNode(WorkflowNode node) {
        InterfaceWorkflowNodePO po = WorkflowNodeAssembler.toPO(node);
        flowNodeMapper.createNode(po);
        node.setNodeId(po.getNodeId());
    }

    @Override
    public int updateNodeSelective(WorkflowNode node) {
        InterfaceWorkflowNodePO po = WorkflowNodeAssembler.toPO(node);
        return flowNodeMapper.updateNodeSelective(po);
    }

    @Override
    public void updateRemoteExtension(Integer nodeId, String remoteExtension) {
        flowNodeMapper.updateRemoteExtension(nodeId, remoteExtension);
    }

    @Override
    public void updateExtensionParam(Integer nodeId, String extensionParam) {
        flowNodeMapper.updateExtensionParam(nodeId, extensionParam);
    }

    @Override
    public void saveExtensionParam(Integer nodeId, Map<String, String> extensionParam) {
        String json = JsonUtils.toJsonString(extensionParam);
        flowNodeMapper.updateExtensionParam(nodeId, json);
    }

    @Override
    public int updateScheduleFields(Integer nodeId, String scheduleParamSourceType, String scheduleExpr) {
        return flowNodeMapper.updateScheduleFields(nodeId, scheduleParamSourceType, scheduleExpr);
    }

    @Override
    public void deleteNode(Integer nodeId) {
        flowNodeMapper.deleteByNodeId(nodeId);
    }

}
