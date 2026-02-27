package com.helianhealth.agent.repository.service;

import com.helianhealth.agent.repository.model.po.InterfaceWorkflowDO;

import java.util.List;

/**
 * 工作流写操作服务
 */
public interface WorkflowCommandService {

    int createWorkflow(InterfaceWorkflowDO workflow);

    List<InterfaceWorkflowDO> selectAllWorkflows();

    int updateWorkflow(InterfaceWorkflowDO workflow);

    void deleteWorkflow(Integer flowId);

    InterfaceWorkflowDO selectWorkflowEntity(Integer flowId);
}
