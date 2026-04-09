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
     * 收集上游调用入参
     */
    public static void collectRequest(String businessDataJson) {
        LogContext context = InvocationLogContextHolder.getContext();
        if (context != null) {
            context.setBusinessData(businessDataJson);
        }
    }

    /**
     * 收集调用前参数（可能是JSON/SQL/XML等格式）
     */
    public static void collectInvokeRequest(String paramBeforeInvoke) {
        LogContext context = InvocationLogContextHolder.getContext();
        if (context != null) {
            context.setParamBeforeInvoke(paramBeforeInvoke);
        }
    }

    /**
     * 收集远程调用原始响应（可能是JSON/XML等格式）
     */
    public static void collectInvokeResponse(String responseJson) {
        LogContext context = InvocationLogContextHolder.getContext();
        if (context != null) {
            context.setRemoteInvokeResponse(responseJson);
        }
    }

    /**
     * 收集远程调用原始响应转化后结果
     */
    public static void collectInvokeParsedResponse(String parsedResponseJson) {
        LogContext context = InvocationLogContextHolder.getContext();
        if (context != null) {

            context.setRemoteInvokeResponseParsed(parsedResponseJson);
        }
    }

    /**
     * 收集调用后参数
     */
    public static void collectResponse(String resultJson) {
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
