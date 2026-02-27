package com.helianhealth.agent.repository.mapper;

import com.helianhealth.agent.repository.model.po.InterfaceWorkflowDO;
import tk.mybatis.mapper.common.Mapper;

import java.util.List;

public interface InterfaceWorkflowMapper extends Mapper<InterfaceWorkflowDO> {

    InterfaceWorkflowDO selectByInterfaceUri(String interfaceUri);

    List<InterfaceWorkflowDO> selectAllWorkflows();

    int createWorkflow(InterfaceWorkflowDO workflow);
}
