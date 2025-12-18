package com.helianhealth.agent.invocation.remote.wrapper;

import com.helianhealth.agent.workflow.model.domain.InterfaceWorkflowNode;
import com.helianhealth.agent.workflow.model.dto.ParamTreeNode;
import com.helianhealth.agent.invocation.remote.InterfaceClientProxy;
import com.helianhealth.agent.invocation.remote.filter.AgentFilter;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;

import java.util.List;
import java.util.Map;

/**
 * 过滤链wrapper，在执行真正的doInvoke方法之前，会走过扩展的过滤链操作
 */
public class InterfaceClientProxy2FilterChainWrapper implements InterfaceClientProxy {

    public final InterfaceClientProxy proxy;

    public InterfaceClientProxy2FilterChainWrapper(InterfaceClientProxy proxy) {
        this.proxy = proxy;
    }

    @Override
    public Map<String, Object> doInvoke(InterfaceWorkflowNode flowNode, List<ParamTreeNode> params) {
        return buildFilterChain(proxy, flowNode.getUrl()).doInvoke(flowNode, params);
    }

    private InterfaceClientProxy buildFilterChain(InterfaceClientProxy proxy, URL url) {
        InterfaceClientProxy last = proxy;
        List<AgentFilter> filters = ExtensionLoader.getExtensionLoader(AgentFilter.class).getActivateExtension(url, "filter");
        for (int i = filters.size() - 1; i >= 0; i--) {
            AgentFilter agentFilter = filters.get(i);
            InterfaceClientProxy next = last;
            last = (flowNode, params) -> agentFilter.doInvoke(next, flowNode, params);
        }
        return last;
    }
}
