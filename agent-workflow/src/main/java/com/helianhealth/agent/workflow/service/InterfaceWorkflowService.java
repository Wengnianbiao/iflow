package com.helianhealth.agent.workflow.service;

import com.helianhealth.agent.common.PageList;
import com.helianhealth.agent.workflow.model.domain.InterfaceWorkflowDO;

import java.util.List;
import java.util.Optional;

public interface InterfaceWorkflowService {

    Optional<InterfaceWorkflowDO> findByInterfaceUri(String interfaceUri);

    int save(InterfaceWorkflowDO interfaceInstanceDO);

    PageList<InterfaceWorkflowDO> getAllWorkflows(int pageNum, int pageSize);

    List<InterfaceWorkflowDO> getAllWorkflowsWithoutPaged();

    int update(InterfaceWorkflowDO interfaceInstanceDO);

    void deleteByFlowId(Integer flowId);

    InterfaceWorkflowDO selectByFlowId(Integer flowId);
}
