package com.helianhealth.agent.console.service;

import com.helianhealth.agent.common.base.PageList;
import com.helianhealth.agent.console.controller.reponse.WorkflowFullExportRsp;
import com.helianhealth.agent.repository.model.po.InterfaceWorkflowDO;
import com.helianhealth.agent.repository.service.WorkflowQueryService;

import java.util.List;

public interface InterfaceWorkflowService extends WorkflowQueryService {

    int save(InterfaceWorkflowDO interfaceInstanceDO);

    PageList<InterfaceWorkflowDO> getAllWorkflows(int pageNum, int pageSize);

    List<InterfaceWorkflowDO> getAllWorkflowsWithoutPaged();

    int update(InterfaceWorkflowDO interfaceInstanceDO);

    void deleteByFlowId(Integer flowId);

    WorkflowFullExportRsp exportWorkflowFull(Integer flowId);

    Integer importWorkflowFull(WorkflowFullExportRsp importData);
}
