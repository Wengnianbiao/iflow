package com.helianhealth.agent.invocation.context;

/**
 * 日志收集门面
 * 作为 SPI 扩展和内部组件收集调用日志的统一入口
 * 隐藏 LogContext 和 ThreadLocal 的实现细节
 */
public class LogCollector {

    /**
     * 初始化日志上下文（调用链入口处调用）
     */
    public static void init() {
        LogContext context = new LogContext();
        InvocationLogContextHolder.setContext(context);
    }

    /**
     * 收集节点名称
     */
    public static void collectNodeName(String nodeName) {
        LogContext context = InvocationLogContextHolder.getContext();
        if (context != null) {
            context.setNodeName(nodeName);
        }
    }

    /**
     * 收集业务关键字
     */
    public static void collectKeyword(String keyword) {
        LogContext context = InvocationLogContextHolder.getContext();
        if (context != null) {
            context.setKeyword(keyword);
        }
    }

    /**
     * 收集业务数据
     */
    public static void collectBusinessData(String businessDataJson) {
        LogContext context = InvocationLogContextHolder.getContext();
        if (context != null) {
            context.setBusinessData(businessDataJson);
        }
    }

    /**
     * 收集调用前参数（参数解析后的结果，可能是JSON/SQL/XML等格式）
     */
    public static void collectParamBeforeInvoke(String paramBeforeInvoke) {
        LogContext context = InvocationLogContextHolder.getContext();
        if (context != null) {
            context.setParamBeforeInvoke(paramBeforeInvoke);
        }
    }

    /**
     * 收集远程调用原始响应
     */
    public static void collectRemoteInvokeResponse(String responseJson) {
        LogContext context = InvocationLogContextHolder.getContext();
        if (context != null) {
            context.setRemoteInvokeResponse(responseJson);
        }
    }

    /**
     * 收集远程调用原始响应转化后结果
     */
    public static void collectRemoteInvokeResponseParsed(String parsedResponseJson) {
        LogContext context = InvocationLogContextHolder.getContext();
        if (context != null) {

            context.setRemoteInvokeResponseParsed(parsedResponseJson);
        }
    }

    /**
     * 收集调用后参数（最终返回结果）
     */
    public static void collectParamAfterInvoke(String resultJson) {
        LogContext context = InvocationLogContextHolder.getContext();
        if (context != null) {
            context.setParamAfterInvoke(resultJson);
        }
    }

    /**
     * 收集调用耗时
     */
    public static void collectInvokeTime(long durationMs) {
        LogContext context = InvocationLogContextHolder.getContext();
        if (context != null) {
            context.setInvokeTime(Long.toString(durationMs));
        }
    }

    /**
     * 收集异常信息
     */
    public static void collectError(String errorMessage) {
        LogContext context = InvocationLogContextHolder.getContext();
        if (context != null) {
            context.setErrorMessage(errorMessage);
            context.setSuccess(false);
        }
    }

    /**
     * 获取当前日志上下文（内部使用，用于最终保存）
     */
    public static LogContext getContext() {
        return InvocationLogContextHolder.getContext();
    }

    /**
     * 清理日志上下文（调用链结束处调用）
     */
    public static void clear() {
        InvocationLogContextHolder.clearContext();
    }
}
