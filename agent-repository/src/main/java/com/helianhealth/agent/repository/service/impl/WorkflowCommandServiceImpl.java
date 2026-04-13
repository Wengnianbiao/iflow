package com.helianhealth.agent.repository.service.impl;

import com.helianhealth.agent.repository.mapper.agent.InterfaceWorkflowMapper;
import com.helianhealth.agent.repository.model.assembler.WorkflowAssembler;
import com.helianhealth.agent.repository.model.domain.Workflow;
import com.helianhealth.agent.repository.model.po.InterfaceWorkflowPO;
import com.helianhealth.agent.repository.service.WorkflowCommandService;
import org.springframework.stereotype.Service;

/**
 * 工作流写操作服务实现
 */
@Service
public class WorkflowCommandServiceImpl implements WorkflowCommandService {

    private final InterfaceWorkflowMapper workflowMapper;

    public WorkflowCommandServiceImpl(InterfaceWorkflowMapper workflowMapper) {
        this.workflowMapper = workflowMapper;
    }

    @Override
    public int createWorkflow(Workflow workflow) {
        InterfaceWorkflowPO po = WorkflowAssembler.toPO(workflow);
        int result = workflowMapper.createWorkflow(po);
        workflow.setFlowId(po.getFlowId());
        return result;
    }

    @Override
    public int updateWorkflow(Workflow workflow) {
        InterfaceWorkflowPO po = WorkflowAssembler.toPO(workflow);
        return workflowMapper.updateByPrimaryKeySelective(po);
    }

    @Override
    public void deleteWorkflow(Integer flowId) {
        workflowMapper.deleteByPrimaryKey(flowId);
    }

}
