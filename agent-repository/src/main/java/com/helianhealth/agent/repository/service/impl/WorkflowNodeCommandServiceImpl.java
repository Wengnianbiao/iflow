package com.helianhealth.agent.repository.service.impl;

import com.helianhealth.agent.repository.mapper.InterfaceFlowNodeMapper;
import com.helianhealth.agent.repository.model.po.InterfaceWorkflowNodePO;
import com.helianhealth.agent.repository.service.WorkflowNodeCommandService;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 工作流节点写操作服务实现
 */
@Component
public class WorkflowNodeCommandServiceImpl implements WorkflowNodeCommandService {

    private final InterfaceFlowNodeMapper flowNodeMapper;

    public WorkflowNodeCommandServiceImpl(InterfaceFlowNodeMapper flowNodeMapper) {
        this.flowNodeMapper = flowNodeMapper;
    }

    @Override
    public void createNode(InterfaceWorkflowNodePO node) {
        flowNodeMapper.createNode(node);
    }

    @Override
    public int updateNodeSelective(InterfaceWorkflowNodePO node) {
        return flowNodeMapper.updateNodeSelective(node);
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

    @Override
    public List<InterfaceWorkflowNodePO> selectAllNodes(Integer flowId) {
        return flowNodeMapper.selectAllNodes(flowId);
    }

    @Override
    public InterfaceWorkflowNodePO selectNodeEntity(Integer nodeId) {
        return flowNodeMapper.selectByNodeId(nodeId);
    }
}
