package com.helianhealth.agent.log.context;

import com.helianhealth.agent.model.dto.LogContext;

/**
 * 统一的日志上下文管理工具类
 * 当前只会在remoteInvoke中使用,remoteInvoke为单线程处理，todo暂时不考虑多线程问题
 */
public class LogContextHolder {

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
