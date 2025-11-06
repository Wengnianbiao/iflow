package com.helianhealth.agent.remote.filter.impl;

import com.helianhealth.agent.model.domain.InterfaceWorkflowNode;
import com.helianhealth.agent.model.dto.ParamTreeNode;
import com.helianhealth.agent.remote.InterfaceClientProxy;
import com.helianhealth.agent.remote.filter.AgentFilter;
import org.apache.dubbo.common.extension.Activate;

import java.util.List;
import java.util.Map;

@Activate(order = 100)
public class MockAgentFilter implements AgentFilter {
    @Override
    public Map<String, Object> doInvoke(InterfaceClientProxy next, InterfaceWorkflowNode flowNode, List<ParamTreeNode> params) {
        //通过参数判断是否直接mock
        return next.doInvoke(flowNode, params);
    }
}
