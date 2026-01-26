package com.helianhealth.agent.invocation.remote.wrapper;

import com.helianhealth.agent.common.utils.SpringContextHolder;
import com.helianhealth.agent.invocation.remote.InterfaceClientProxy;
import com.helianhealth.agent.invocation.remote.InterfaceRemoteInvoker;
import com.helianhealth.agent.log.service.InterfaceInvokeLogService;

/**
 * 包装器工厂类，用于显式创建包装链
 */
public class WrapperFactory {

    private WrapperFactory() {
    }

    /**
     * 为InterfaceClientProxy创建显式的包装链
     * 包装顺序：过滤器链 -> 日志包装 -> 原始代理
     *
     * @param original 原始代理对象
     * @return 包装后的代理对象
     */
    public static InterfaceClientProxy createClientProxyChain(InterfaceClientProxy original) {
        InterfaceClientProxy wrapped = original;

        // 第一步：应用过滤器链包装
        wrapped = new InterfaceClientProxyFilterChainWrapper(wrapped);
        // 第二步：应用日志包装
        wrapped = new InterfaceClientProxyLogWrapper(wrapped);

        return wrapped;
    }

    /**
     * 为InterfaceRemoteInvoker创建显式的包装链
     * 包装顺序：日志包装 -> 原始调用器
     *
     * @param original 原始调用器对象
     * @return 包装后的调用器对象
     */
    public static InterfaceRemoteInvoker createRemoteInvokerChain(InterfaceRemoteInvoker original) {
        // 从Spring容器获取日志服务
        InterfaceInvokeLogService logService = SpringContextHolder.getBean(InterfaceInvokeLogService.class);
        // 应用日志包装，注入日志服务
        return new InterfaceRemoteInvokerLogWrapper(original, logService);
    }
}
