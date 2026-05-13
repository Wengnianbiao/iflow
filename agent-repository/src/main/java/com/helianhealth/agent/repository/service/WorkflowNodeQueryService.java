package com.helianhealth.agent.repository.service;

import com.helianhealth.agent.common.base.PageList;
import com.helianhealth.agent.repository.model.domain.WorkflowNode;

import java.util.List;

/**
 * 工作流节点查询服务
 */
public interface WorkflowNodeQueryService {

    WorkflowNode selectByNodeId(Integer nodeId);

    /**
     * 查询所有节点
     */
    List<WorkflowNode> selectAllNodes(Integer flowId);

    /**
     * 分页查询节点
     */
    PageList<WorkflowNode> selectAllNodesPaged(Integer flowId, int pageNum, int pageSize);
}
