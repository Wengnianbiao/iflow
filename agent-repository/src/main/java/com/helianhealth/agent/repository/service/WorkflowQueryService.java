package com.helianhealth.agent.repository.service;

import com.helianhealth.agent.repository.model.domain.Workflow;

import java.util.Optional;

/**
 * 工作流查询服务（供运行时模块使用）
 */
public interface WorkflowQueryService {

    Optional<Workflow> findByInterfaceUri(String interfaceUri);

    Workflow selectByFlowId(Integer flowId);
}
