package com.helianhealth.agent.invocation.remote.filter;

import com.helianhealth.agent.common.extension.AgentSPI;
import com.helianhealth.agent.invocation.remote.InterfaceClientProxy;
import com.helianhealth.agent.invocation.remote.model.InterfaceWorkflowNode;
import com.helianhealth.agent.invocation.remote.model.ParamTreeNode;

import java.util.List;
import java.util.Map;

/**
 * SPI扩展agent过滤
 */
@AgentSPI
public interface AgentFilter {

    Map<String, Object> doInvoke(InterfaceClientProxy next, InterfaceWorkflowNode flowNode, List<ParamTreeNode> params);
}
