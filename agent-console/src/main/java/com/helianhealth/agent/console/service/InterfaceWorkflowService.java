package com.helianhealth.agent.console.service;

import com.helianhealth.agent.common.base.PageList;
import com.helianhealth.agent.console.controller.reponse.WorkflowFullExportRsp;
import com.helianhealth.agent.repository.model.domain.Workflow;

import java.util.List;

public interface InterfaceWorkflowService {

    int save(Workflow workflow);

    PageList<Workflow> getAllWorkflows(int pageNum, int pageSize);

    List<Workflow> getAllWorkflowsWithoutPaged();

    int update(Workflow workflow);

    void deleteByFlowId(Integer flowId);

    WorkflowFullExportRsp exportWorkflowFull(Integer flowId);

    Integer importWorkflowFull(WorkflowFullExportRsp importData);
}
