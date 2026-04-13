package com.helianhealth.agent.repository.service.impl;

import com.helianhealth.agent.repository.mapper.agent.InterfaceFlowNodeMapper;
import com.helianhealth.agent.repository.model.assembler.WorkflowNodeAssembler;
import com.helianhealth.agent.repository.model.domain.WorkflowNode;
import com.helianhealth.agent.repository.model.po.InterfaceWorkflowNodePO;
import com.helianhealth.agent.repository.service.WorkflowNodeCommandService;
import org.springframework.stereotype.Service;

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
    public int updateRemoteExtension(Integer nodeId, String remoteExtension) {
        return flowNodeMapper.updateRemoteExtension(nodeId, remoteExtension);
    }

    @Override
    public int updateExtensionParam(Integer nodeId, String extensionParam) {
        return flowNodeMapper.updateExtensionParam(nodeId, extensionParam);
    }

    @Override
    public int updateScheduleFields(Integer nodeId, String scheduleParamSourceType, String scheduleExpr) {
        return flowNodeMapper.updateScheduleFields(nodeId, scheduleParamSourceType, scheduleExpr);
    }

    @Override
    public void deleteNode(Integer nodeId) {
        flowNodeMapper.deleteByPrimaryKey(nodeId);
    }

}
