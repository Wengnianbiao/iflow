package com.helianhealth.agent.repository.mapper.agent;

import com.helianhealth.agent.repository.model.po.InterfaceWorkflowPO;
import org.apache.ibatis.annotations.Param;
import tk.mybatis.mapper.common.Mapper;

import java.util.List;

public interface InterfaceWorkflowMapper {

    InterfaceWorkflowPO selectByInterfaceUri(String interfaceUri);

    List<InterfaceWorkflowPO> selectAllWorkflows();

    InterfaceWorkflowPO selectByFlowId(Integer flowId);

    int createWorkflow(InterfaceWorkflowPO workflow);

    int updateWorkflow(InterfaceWorkflowPO workflow);

    int updateRouteExtension(@Param("flowId") Integer flowId, @Param("routeExtension") String routeExtension);

    int deleteByFlowId(Integer flowId);
}
