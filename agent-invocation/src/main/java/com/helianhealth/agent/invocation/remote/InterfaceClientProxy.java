package com.helianhealth.agent.invocation.remote;

import com.helianhealth.agent.common.extension.AgentAdaptive;
import com.helianhealth.agent.common.extension.AgentSPI;
import com.helianhealth.agent.invocation.remote.model.InterfaceWorkflowNode;
import com.helianhealth.agent.invocation.remote.model.ParamTreeNode;

import java.util.List;
import java.util.Map;

/**
 * 接口调用客户端代理
 */
@AgentSPI("default")
public interface InterfaceClientProxy {

    @AgentAdaptive("nodeType")
    Map<String, Object> doInvoke(InterfaceWorkflowNode flowNode,
                                 List<ParamTreeNode> params);
}
