package com.helianhealth.agent.invocation.context;

/**
 * 日志上下文生命周期管理类
 *
 * <p><b>框架内部使用 API（Internal API）</b>：仅供框架基础设施（如调用链包装器、Open API Handler 默认实现）
 * 在调用链入口/出口处管理 {@link LogContext} 的生命周期。
 *
 * <p>SPI 扩展方与业务侧禁止直接调用，请使用
 * {@code com.helianhealth.agent.invocation.support.LogCollector} 完成日志字段采集与直接入库。
 */
public final class LogLifecycle {

    private LogLifecycle() {
    }

    /**
     * 初始化当前线程的日志上下文（调用链入口处调用）。
     * <p>重复调用会覆盖已有上下文，破坏采集链路，务必配对使用 init/clear。
     */
    public static void init() {
        InvocationLogContextHolder.setContext(new LogContext());
    }

    /**
     * 获取当前线程的日志上下文。
     */
    public static LogContext getContext() {
        return InvocationLogContextHolder.getContext();
    }

    /**
     * 清理当前线程的日志上下文（调用链出口处调用），防止 ThreadLocal 内存泄漏。
     */
    public static void clear() {
        InvocationLogContextHolder.clearContext();
    }
}
