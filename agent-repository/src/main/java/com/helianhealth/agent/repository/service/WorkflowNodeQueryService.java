package com.helianhealth.agent.repository.service;

import com.helianhealth.agent.repository.model.domain.WorkflowNode;

/**
 * 工作流节点查询服务（供运行时模块使用）
 */
public interface WorkflowNodeQueryService {

    WorkflowNode selectByNodeId(Integer nodeId);
}
