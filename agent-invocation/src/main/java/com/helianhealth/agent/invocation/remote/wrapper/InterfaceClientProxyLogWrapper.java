package com.helianhealth.agent.invocation.remote.wrapper;

import com.helianhealth.agent.invocation.context.InvocationLogContextHolder;
import com.helianhealth.agent.invocation.context.LogContext;
import com.helianhealth.agent.invocation.remote.InterfaceClientProxy;
import com.helianhealth.agent.common.utils.JsonUtils;
import com.helianhealth.agent.invocation.remote.model.InterfaceWorkflowNode;
import com.helianhealth.agent.invocation.remote.model.ParamTreeNode;
import org.apache.dubbo.common.extension.Activate;

import java.util.List;
import java.util.Map;

/**
 * 日志记录wrapper，记录调用时间和响应结果
 */
public class InterfaceClientProxyLogWrapper implements InterfaceClientProxy {

    private final InterfaceClientProxy interfaceClientProxy;

    public InterfaceClientProxyLogWrapper(InterfaceClientProxy interfaceClientProxy) {
        this.interfaceClientProxy = interfaceClientProxy;
    }

    @Override
    public Map<String, Object> doInvoke(InterfaceWorkflowNode flowNode, List<ParamTreeNode> params) {
        LogContext context = InvocationLogContextHolder.getContext();
        if (context == null) {
            // 如果上下文不存在，直接执行方法
            return interfaceClientProxy.doInvoke(flowNode, params);
        }

        // 记录doInvoke方法开始时间
        long doInvokeStartTime = System.currentTimeMillis();
        try {
            // 执行doInvoke方法
            Map<String, Object> stringObjectMap = interfaceClientProxy.doInvoke(flowNode, params);
            // 记录响应结果
            context.setRemoteInvokeResponse(JsonUtils.toJsonString(stringObjectMap));
            return stringObjectMap;
        } finally {
            // 计算doInvoke方法执行耗时
            long doInvokeTime = System.currentTimeMillis() - doInvokeStartTime;
            context.setInvokeTime(Long.toString(doInvokeTime));
        }
    }
}
