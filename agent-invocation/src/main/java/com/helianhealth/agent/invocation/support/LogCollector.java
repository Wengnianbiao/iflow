package com.helianhealth.agent.invocation.support;

import com.helianhealth.agent.common.utils.SpringContextHolder;
import com.helianhealth.agent.invocation.context.InvocationLogContextHolder;
import com.helianhealth.agent.invocation.context.LogContext;
import com.helianhealth.agent.repository.model.domain.InvokeLog;
import com.helianhealth.agent.repository.service.InterfaceInvokeLogService;
import lombok.extern.slf4j.Slf4j;

/**
 * 日志收集门面（面向业务侧 / SPI 扩展方）
 *
 * <p>提供两类能力：
 * <ul>
 *   <li><b>链路内采集</b>：{@code collectXxx(...)} 系列方法向当前线程 {@link LogContext} 补充字段，
 *       配合 {@code InterfaceRemoteInvokerLogWrapper} 已建立的 ThreadLocal 使用，典型场景为 SPI 扩展
 *       在 {@code remoteInvoke} 内部记录请求/响应/耗时。</li>
 *   <li><b>直接入库</b>：{@link #saveInvokeLog(String, String, String, String, String, String, long)}
 *       带参一步写库，<b>不依赖</b> ThreadLocal，典型场景为 Handler 无节点概念、无调用链生命周期时
 *       需要持久化一条日志。</li>
 * </ul>
 *
 * <p>ThreadLocal 生命周期由框架内部的 {@code LogLifecycle} 管理，业务侧无需感知。
 */
@Slf4j
public final class LogCollector {

    private LogCollector() {
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
     * 直接入库：适用于 Handler 等无调用链节点上下文的场景，带参一步写库。
     * <p>异常时记录 log.error，不影响主调用链。
     *
     * @param nodeName       节点名称
     * @param keyword        业务关键字
     * @param request        上游调用入参（业务数据 JSON）
     * @param invokeRequest  调用前参数（可能是 JSON/SQL/XML 等格式）
     * @param invokeResponse 远程调用原始响应
     * @param response       调用后参数（给上游的最终响应）
     * @param invokeTimeMs   调用耗时（毫秒）
     */
    public static void saveInvokeLog(String nodeName,
                                     String keyword,
                                     String request,
                                     String invokeRequest,
                                     String invokeResponse,
                                     String response,
                                     long invokeTimeMs) {
        try {
            InterfaceInvokeLogService logService = SpringContextHolder.getBean(InterfaceInvokeLogService.class);
            logService.saveInvokeLog(InvokeLog.builder()
                    .nodeName(nodeName)
                    .keyword(keyword)
                    .businessData(request)
                    .paramBeforeInvoke(invokeRequest)
                    .remoteInvokeResponse(invokeResponse)
                    .paramAfterInvoke(response)
                    .invokeTime(Long.toString(invokeTimeMs))
                    .build());
        } catch (Exception e) {
            log.error("保存接口调用日志失败", e);
        }
    }
}
