package com.helianhealth.agent.workflow.service;

import com.helianhealth.agent.common.base.PageList;
import com.helianhealth.agent.workflow.controller.reponse.WorkflowFullExportRsp;
import com.helianhealth.agent.workflow.model.domain.InterfaceWorkflowDO;

import java.util.List;
import java.util.Optional;

public interface InterfaceWorkflowService {

    Optional<InterfaceWorkflowDO> findByInterfaceUri(String interfaceUri);

    int save(InterfaceWorkflowDO interfaceInstanceDO);

    PageList<InterfaceWorkflowDO> getAllWorkflows(int pageNum, int pageSize);

    List<InterfaceWorkflowDO> getAllWorkflowsWithoutPaged();

    int update(InterfaceWorkflowDO interfaceInstanceDO);

    void deleteByFlowId(Integer flowId);

    InterfaceWorkflowDO selectByFlowId(Integer flowId);

    /**
     * 导出工作流完整配置（包含节点和参数配置）
     */
    WorkflowFullExportRsp exportWorkflowFull(Integer flowId);

    /**
     * 导入工作流完整配置（包含节点和参数配置）
     * @return 新创建的工作流ID
     */
    Integer importWorkflowFull(WorkflowFullExportRsp importData);
}
