package com.helianhealth.agent.invocation.context;

import com.helianhealth.agent.workflow.model.dto.LogContext;

/**
 * 调用过程的日志上下文管理工具类
 * 用于在调用链路中传递日志信息
 * 当前只会在 remoteInvoke 中使用，remoteInvoke 为单线程处理
 */
public class InvocationLogContextHolder {

    private static final ThreadLocal<LogContext> logContext = new ThreadLocal<>();

    /**
     * 设置当前线程的日志上下文
     */
    public static void setContext(LogContext context) {
        logContext.set(context);
    }

    /**
     * 获取当前线程的日志上下文
     */
    public static LogContext getContext() {
        return logContext.get();
    }

    /**
     * 清除当前线程的日志上下文
     */
    public static void clearContext() {
        logContext.remove();
    }
}
