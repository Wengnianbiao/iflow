package com.helianhealth.agent.invocation.remote.wrapper;

import com.helianhealth.agent.common.extension.AgentExtensionLoader;
import com.helianhealth.agent.invocation.remote.InterfaceClientProxy;
import com.helianhealth.agent.invocation.remote.filter.AgentFilter;
import com.helianhealth.agent.invocation.remote.model.InterfaceWorkflowNode;
import com.helianhealth.agent.invocation.remote.model.ParamTreeNode;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 过滤链wrapper，在执行真正的doInvoke方法之前，会走过扩展的过滤链操作
 */
public class InterfaceClientProxyFilterChainWrapper implements InterfaceClientProxy {

    public final InterfaceClientProxy proxy;

    public InterfaceClientProxyFilterChainWrapper(InterfaceClientProxy proxy) {
        this.proxy = proxy;
    }

    @Override
    public Map<String, Object> doInvoke(InterfaceWorkflowNode flowNode, List<ParamTreeNode> params) {
        return buildFilterChain(proxy, flowNode.getFilterParams()).doInvoke(flowNode, params);
    }

    private InterfaceClientProxy buildFilterChain(InterfaceClientProxy proxy, Map<String, String> filterParams) {
        InterfaceClientProxy last = proxy;
        List<AgentFilter> filters = AgentExtensionLoader
                .getExtensionLoader(AgentFilter.class)
                .getActivateExtensions(filterParams != null ? filterParams : Collections.emptyMap());
        for (int i = filters.size() - 1; i >= 0; i--) {
            AgentFilter agentFilter = filters.get(i);
            InterfaceClientProxy next = last;
            last = (flowNode, params) -> agentFilter.doInvoke(next, flowNode, params);
        }
        return last;
    }
}
