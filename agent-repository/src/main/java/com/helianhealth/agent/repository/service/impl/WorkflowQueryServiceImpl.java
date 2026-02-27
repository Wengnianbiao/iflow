package com.helianhealth.agent.repository.service.impl;

import com.helianhealth.agent.repository.mapper.InterfaceWorkflowMapper;
import com.helianhealth.agent.repository.model.domain.Workflow;
import com.helianhealth.agent.repository.model.po.InterfaceWorkflowDO;
import com.helianhealth.agent.repository.service.WorkflowQueryService;

import java.util.Optional;

/**
 * 工作流查询服务基础实现
 */
public class WorkflowQueryServiceImpl implements WorkflowQueryService {

    protected final InterfaceWorkflowMapper workflowMapper;

    public WorkflowQueryServiceImpl(InterfaceWorkflowMapper workflowMapper) {
        this.workflowMapper = workflowMapper;
    }

    @Override
    public Optional<Workflow> findByInterfaceUri(String interfaceUri) {
        return Optional.ofNullable(workflowMapper.selectByInterfaceUri(interfaceUri))
                .map(this::toWorkflow);
    }

    @Override
    public Workflow selectByFlowId(Integer flowId) {
        return toWorkflow(workflowMapper.selectByPrimaryKey(flowId));
    }

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
