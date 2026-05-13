package com.helianhealth.agent.repository.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.helianhealth.agent.common.base.PageList;
import com.helianhealth.agent.repository.mapper.agent.InterfaceWorkflowMapper;
import com.helianhealth.agent.repository.model.assembler.WorkflowAssembler;
import com.helianhealth.agent.repository.model.domain.Workflow;
import com.helianhealth.agent.repository.model.po.InterfaceWorkflowPO;
import com.helianhealth.agent.repository.service.WorkflowQueryService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 工作流查询服务基础实现
 */
@Service
public class WorkflowQueryServiceImpl implements WorkflowQueryService {

    protected final InterfaceWorkflowMapper workflowMapper;

    public WorkflowQueryServiceImpl(InterfaceWorkflowMapper workflowMapper) {
        this.workflowMapper = workflowMapper;
    }

    @Override
    public Optional<Workflow> findByInterfaceUri(String interfaceUri) {
        return Optional.ofNullable(workflowMapper.selectByInterfaceUri(interfaceUri))
                .map(WorkflowAssembler::toDomain);
    }

    @Override
    public Workflow selectByFlowId(Integer flowId) {
        return WorkflowAssembler.toDomain(workflowMapper.selectByFlowId(flowId));
    }

    @Override
    public List<Workflow> selectAllWorkflows() {
        return workflowMapper.selectAllWorkflows().stream()
                .map(WorkflowAssembler::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public PageList<Workflow> selectAllWorkflowsPaged(int pageNum, int pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        List<InterfaceWorkflowPO> poList = workflowMapper.selectAllWorkflows();
        PageInfo<InterfaceWorkflowPO> pageInfo = new PageInfo<>(poList);
        List<Workflow> workflows = poList.stream().map(WorkflowAssembler::toDomain).collect(Collectors.toList());
        PageList<Workflow> pageList = new PageList<>();
        pageList.setRows(workflows);
        pageList.setTotal(pageInfo.getTotal());
        pageList.setPageNum(pageInfo.getPageNum());
        pageList.setPageSize(pageInfo.getPageSize());
        return pageList;
    }

}
