package com.helianhealth.agent.repository.service;

import com.helianhealth.agent.common.base.PageList;
import com.helianhealth.agent.repository.model.domain.Workflow;

import java.util.List;
import java.util.Optional;

/**
 * 工作流查询服务
 */
public interface WorkflowQueryService {

    Optional<Workflow> findByInterfaceUri(String interfaceUri);

    Workflow selectByFlowId(Integer flowId);

    /**
     * 查询所有工作流
     */
    List<Workflow> selectAllWorkflows();

    /**
     * 分页查询工作流
     */
    PageList<Workflow> selectAllWorkflowsPaged(int pageNum, int pageSize);
}
