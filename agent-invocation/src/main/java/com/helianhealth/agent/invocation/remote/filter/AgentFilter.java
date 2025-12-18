package com.helianhealth.agent.invocation.remote.filter;

import com.helianhealth.agent.workflow.model.domain.InterfaceWorkflowNode;
import com.helianhealth.agent.workflow.model.dto.ParamTreeNode;
import com.helianhealth.agent.invocation.remote.InterfaceClientProxy;
import org.apache.dubbo.common.extension.SPI;


import java.util.List;
import java.util.Map;

/**
 * SPI扩展agent过滤
 */
@SPI
public interface AgentFilter {

    Map<String, Object> doInvoke(InterfaceClientProxy next, InterfaceWorkflowNode flowNode, List<ParamTreeNode> params);
}
