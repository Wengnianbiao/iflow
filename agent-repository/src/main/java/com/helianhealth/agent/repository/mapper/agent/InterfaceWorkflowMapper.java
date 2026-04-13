package com.helianhealth.agent.repository.mapper.agent;

import com.helianhealth.agent.repository.model.po.InterfaceWorkflowPO;
import tk.mybatis.mapper.common.Mapper;

import java.util.List;

public interface InterfaceWorkflowMapper extends Mapper<InterfaceWorkflowPO> {

    InterfaceWorkflowPO selectByInterfaceUri(String interfaceUri);

    List<InterfaceWorkflowPO> selectAllWorkflows();

    int createWorkflow(InterfaceWorkflowPO workflow);
}
