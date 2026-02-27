package com.helianhealth.agent.repository.service.impl;

import com.helianhealth.agent.repository.mapper.InterfaceWorkflowMapper;
import com.helianhealth.agent.repository.model.po.InterfaceWorkflowDO;
import com.helianhealth.agent.repository.service.WorkflowCommandService;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 工作流写操作服务实现
 */
@Component
public class WorkflowCommandServiceImpl implements WorkflowCommandService {

    private final InterfaceWorkflowMapper workflowMapper;

    public WorkflowCommandServiceImpl(InterfaceWorkflowMapper workflowMapper) {
        this.workflowMapper = workflowMapper;
    }

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
}
