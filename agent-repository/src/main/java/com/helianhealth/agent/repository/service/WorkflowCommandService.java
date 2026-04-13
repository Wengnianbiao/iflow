package com.helianhealth.agent.repository.service;

import com.helianhealth.agent.repository.model.domain.Workflow;

/**
 * 工作流写操作服务
 */
public interface WorkflowCommandService {

    int createWorkflow(Workflow workflow);

    int updateWorkflow(Workflow workflow);

    void deleteWorkflow(Integer flowId);
}
