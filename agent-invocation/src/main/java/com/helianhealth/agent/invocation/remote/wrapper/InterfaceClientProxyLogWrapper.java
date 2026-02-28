package com.helianhealth.agent.invocation.remote.wrapper;

import com.helianhealth.agent.invocation.context.LogCollector;
import com.helianhealth.agent.invocation.remote.InterfaceClientProxy;
import com.helianhealth.agent.invocation.remote.model.ParamTreeNode;
import com.helianhealth.agent.repository.model.domain.WorkflowNode;

import java.util.List;
import java.util.Map;

/**
 * 日志记录wrapper，记录调用耗时
 * 注：paramBeforeInvoke 和 remoteInvokeResponse 已在 AbstractProtocolAdapter 中收集
 */
public class InterfaceClientProxyLogWrapper implements InterfaceClientProxy {

    private final InterfaceClientProxy interfaceClientProxy;

    public InterfaceClientProxyLogWrapper(InterfaceClientProxy interfaceClientProxy) {
        this.interfaceClientProxy = interfaceClientProxy;
    }

    @Override
    public Map<String, Object> doInvoke(WorkflowNode flowNode, List<ParamTreeNode> params) {
        long doInvokeStartTime = System.currentTimeMillis();
        try {
            return interfaceClientProxy.doInvoke(flowNode, params);
        } finally {
            // 计算并记录doInvoke方法执行耗时
            long doInvokeTime = System.currentTimeMillis() - doInvokeStartTime;
            LogCollector.collectInvokeTime(doInvokeTime);
        }
    }
}
