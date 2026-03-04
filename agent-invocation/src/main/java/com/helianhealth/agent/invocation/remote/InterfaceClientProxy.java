package com.helianhealth.agent.invocation.remote;

import com.helianhealth.agent.common.extension.AgentAdaptive;
import com.helianhealth.agent.common.extension.AgentSPI;
import com.helianhealth.agent.repository.model.domain.WorkflowNode;
import com.helianhealth.agent.invocation.remote.param.model.ParamTreeNode;

import java.util.List;
import java.util.Map;

/**
 * 接口调用客户端代理
 */
@AgentSPI("default")
public interface InterfaceClientProxy {

    @AgentAdaptive("nodeType")
    Map<String, Object> doInvoke(WorkflowNode flowNode,
                                 List<ParamTreeNode> params);
}
