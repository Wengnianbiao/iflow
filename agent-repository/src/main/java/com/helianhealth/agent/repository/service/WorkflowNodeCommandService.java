package com.helianhealth.agent.repository.service;

import com.helianhealth.agent.repository.model.po.InterfaceWorkflowNodePO;

import java.util.List;

/**
 * 工作流节点写操作服务
 */
public interface WorkflowNodeCommandService {

    void createNode(InterfaceWorkflowNodePO node);

    int updateNode(InterfaceWorkflowNodePO node);

    void deleteNode(Integer nodeId);

    List<InterfaceWorkflowNodePO> selectAllNodes(Integer flowId);

    InterfaceWorkflowNodePO selectNodeEntity(Integer nodeId);
}
