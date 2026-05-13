package com.helianhealth.agent.repository.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.helianhealth.agent.common.base.PageList;
import com.helianhealth.agent.repository.mapper.agent.InterfaceFlowNodeMapper;
import com.helianhealth.agent.repository.model.assembler.WorkflowNodeAssembler;
import com.helianhealth.agent.repository.model.domain.WorkflowNode;
import com.helianhealth.agent.repository.model.po.InterfaceWorkflowNodePO;
import com.helianhealth.agent.repository.service.WorkflowNodeQueryService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 工作流节点查询服务基础实现
 */
@Service
public class WorkflowNodeQueryServiceImpl implements WorkflowNodeQueryService {

    protected final InterfaceFlowNodeMapper flowNodeMapper;

    public WorkflowNodeQueryServiceImpl(InterfaceFlowNodeMapper flowNodeMapper) {
        this.flowNodeMapper = flowNodeMapper;
    }

    @Override
    public WorkflowNode selectByNodeId(Integer nodeId) {
        InterfaceWorkflowNodePO po = flowNodeMapper.selectByNodeId(nodeId);
        return WorkflowNodeAssembler.toDomain(po);
    }

    @Override
    public List<WorkflowNode> selectAllNodes(Integer flowId) {
        return flowNodeMapper.selectAllNodes(flowId).stream()
                .map(WorkflowNodeAssembler::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public PageList<WorkflowNode> selectAllNodesPaged(Integer flowId, int pageNum, int pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        List<InterfaceWorkflowNodePO> poList = flowNodeMapper.selectAllNodes(flowId);
        PageInfo<InterfaceWorkflowNodePO> pageInfo = new PageInfo<>(poList);
        List<WorkflowNode> nodes = poList.stream().map(WorkflowNodeAssembler::toDomain).collect(Collectors.toList());
        PageList<WorkflowNode> pageList = new PageList<>();
        pageList.setRows(nodes);
        pageList.setTotal(pageInfo.getTotal());
        pageList.setPageNum(pageInfo.getPageNum());
        pageList.setPageSize(pageInfo.getPageSize());
        return pageList;
    }

}
