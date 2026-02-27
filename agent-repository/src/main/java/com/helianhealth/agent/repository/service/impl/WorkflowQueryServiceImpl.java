package com.helianhealth.agent.repository.service.impl;

import com.helianhealth.agent.repository.mapper.InterfaceWorkflowMapper;
import com.helianhealth.agent.repository.model.domain.Workflow;
import com.helianhealth.agent.repository.model.po.InterfaceWorkflowDO;
import com.helianhealth.agent.repository.service.WorkflowCommandService;
import com.helianhealth.agent.repository.service.WorkflowQueryService;

import java.util.List;
import java.util.Optional;

/**
 * 工作流数据访问基础实现（查询 + 写操作）
 */
public class WorkflowQueryServiceImpl implements WorkflowQueryService, WorkflowCommandService {

    protected final InterfaceWorkflowMapper workflowMapper;

    public WorkflowQueryServiceImpl(InterfaceWorkflowMapper workflowMapper) {
        this.workflowMapper = workflowMapper;
    }

    // ========== Query ==========

    @Override
    public Optional<Workflow> findByInterfaceUri(String interfaceUri) {
        return Optional.ofNullable(workflowMapper.selectByInterfaceUri(interfaceUri))
                .map(this::toWorkflow);
    }

    @Override
    public Workflow selectByFlowId(Integer flowId) {
        return toWorkflow(workflowMapper.selectByPrimaryKey(flowId));
    }

    // ========== Command ==========

    @Override
    public int createWorkflow(InterfaceWorkflowDO workflow) {
        return workflowMapper.createWorkflow(workflow);
    }

    @Override
    public List<InterfaceWorkflowDO> selectAllWorkflows() {
        return workflowMapper.selectAllWorkflows();
    }

    @Override
    public int updateWorkflow(InterfaceWorkflowDO workflow) {
        return workflowMapper.updateByPrimaryKeySelective(workflow);
    }

    @Override
    public void deleteWorkflow(Integer flowId) {
        workflowMapper.deleteByPrimaryKey(flowId);
    }

    @Override
    public InterfaceWorkflowDO selectWorkflowEntity(Integer flowId) {
        return workflowMapper.selectByPrimaryKey(flowId);
    }

    // ========== Convert ==========

    protected Workflow toWorkflow(InterfaceWorkflowDO entity) {
        if (entity == null) {
            return null;
        }
        return Workflow.builder()
                .flowId(entity.getFlowId())
                .flowName(entity.getFlowName())
                .interfaceUri(entity.getInterfaceUri())
                .contentType(entity.getContentType())
                .contentMetaInfo(entity.getContentMetaInfo())
                .firstFlowNodes(entity.getFirstFlowNodes())
                .status(entity.getStatus())
                .build();
    }
}
