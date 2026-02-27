package com.helianhealth.agent.repository.service.impl;

import com.helianhealth.agent.repository.mapper.InterfaceFlowNodeMapper;
import com.helianhealth.agent.repository.model.dto.InterfaceWorkflowNodeView;
import com.helianhealth.agent.repository.model.po.InterfaceWorkflowNodePO;
import com.helianhealth.agent.repository.service.WorkflowNodeCommandService;
import com.helianhealth.agent.repository.service.WorkflowNodeQueryService;

import java.util.List;

/**
 * 工作流节点数据访问基础实现（查询 + 写操作）
 */
public class WorkflowNodeQueryServiceImpl implements WorkflowNodeQueryService, WorkflowNodeCommandService {

    protected final InterfaceFlowNodeMapper flowNodeMapper;

    public WorkflowNodeQueryServiceImpl(InterfaceFlowNodeMapper flowNodeMapper) {
        this.flowNodeMapper = flowNodeMapper;
    }

    // ========== Query ==========

    @Override
    public InterfaceWorkflowNodeView selectByNodeId(Integer nodeId) {
        InterfaceWorkflowNodePO po = flowNodeMapper.selectByNodeId(nodeId);
        return toView(po);
    }

    // ========== Command ==========

    @Override
    public void createNode(InterfaceWorkflowNodePO node) {
        flowNodeMapper.createNode(node);
    }

    @Override
    public int updateNode(InterfaceWorkflowNodePO node) {
        return flowNodeMapper.updateNode(node);
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

    // ========== Convert ==========

    protected InterfaceWorkflowNodeView toView(InterfaceWorkflowNodePO po) {
        if (po == null) {
            return null;
        }
        InterfaceWorkflowNodeView view = new InterfaceWorkflowNodeView();
        view.setNodeId(po.getNodeId());
        view.setNodeName(po.getNodeName());
        view.setFlowId(po.getFlowId());
        view.setParamFilterExpr(po.getParamFilterExpr());
        view.setNodeType(po.getNodeType());
        view.setMetaInfo(po.getMetaInfo());
        view.setSplitField(po.getSplitField());
        view.setScheduleExpr(po.getScheduleExpr());
        view.setScheduleParamSourceType(po.getScheduleParamSourceType());
        view.setKeywordField(po.getKeywordField());
        view.setRemoteExtension(po.getRemoteExtension());
        view.setExtensionParam(po.getExtensionParam());
        return view;
    }
}
