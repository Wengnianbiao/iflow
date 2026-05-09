package com.helianhealth.agent.invocation.remote.wrapper;

import com.helianhealth.agent.invocation.remote.InterfaceClientProxy;
import com.helianhealth.agent.invocation.remote.InterfaceRemoteInvoker;
import com.helianhealth.agent.repository.service.InterfaceInvokeLogService;

/**
 * 包装器工厂类，用于显式创建包装链
 */
public class WrapperFactory {

    private WrapperFactory() {
    }

    /**
     * 为InterfaceClientProxy创建显式的包装链
     * 包装顺序：过滤器链 -> 原始代理
     * 注：耗时统计已移至 AbstractProtocolAdapter 模板方法中
     *
     * @param original 原始代理对象
     * @return 包装后的代理对象
     */
    public static InterfaceClientProxy createClientProxyChain(InterfaceClientProxy original) {
        // 应用过滤器链包装
        return new InterfaceClientProxyFilterChainWrapper(original);
    }

    /**
     * 为InterfaceRemoteInvoker创建显式的包装链
     * 包装顺序：日志包装 -> 原始调用器
     *
     * @param original 原始调用器对象
     * @param logService 日志服务
     * @return 包装后的调用器对象
     */
    public static InterfaceRemoteInvoker createRemoteInvokerChain(InterfaceRemoteInvoker original,
                                                                  InterfaceInvokeLogService logService) {
        return new InterfaceRemoteInvokerLogWrapper(original, logService);
    }
}
