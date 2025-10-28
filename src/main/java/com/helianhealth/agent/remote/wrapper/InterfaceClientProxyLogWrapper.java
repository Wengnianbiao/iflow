package com.helianhealth.agent.remote.wrapper;

import com.helianhealth.agent.log.aspect.FlowNodeLogAspect;
import com.helianhealth.agent.model.domain.InterfaceWorkflowNodeDO;
import com.helianhealth.agent.model.dto.LogContext;
import com.helianhealth.agent.model.dto.ParamTreeNode;
import com.helianhealth.agent.remote.InterfaceClientProxy;
import com.helianhealth.agent.utils.JsonUtils;

import java.util.List;
import java.util.Map;

public class InterfaceClientProxyLogWrapper implements InterfaceClientProxy {

    private final InterfaceClientProxy interfaceClientProxy;

    public InterfaceClientProxyLogWrapper(InterfaceClientProxy interfaceClientProxy){
        this.interfaceClientProxy = interfaceClientProxy;
    }

    private static final ThreadLocal<LogContext> logContext = new ThreadLocal<>();

    @Override
    public Map<String, Object> doInvoke(InterfaceWorkflowNodeDO flowNode, List<ParamTreeNode> params) {
        LogContext context = logContext.get();
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
