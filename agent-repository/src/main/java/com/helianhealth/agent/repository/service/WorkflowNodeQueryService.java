package com.helianhealth.agent.repository.service;

import com.helianhealth.agent.repository.model.dto.InterfaceWorkflowNodeView;

/**
 * 工作流节点查询服务（供运行时模块使用）
 */
public interface WorkflowNodeQueryService {

    InterfaceWorkflowNodeView selectByNodeId(Integer nodeId);
}
