package com.helianhealth.agent.invocation.context;

import lombok.Data;

/**
 * 调用日志上下文
 * 用于在调用链路中传递和收集日志信息
 */
@Data
public class LogContext {

    /**
     * 节点名称
     */
    private String nodeName;

    /**
     * 业务数据（JSON 格式）
     */
    private String businessData = "{}";

    /**
     * 业务关键字（用于日志检索）
     */
    private String keyword;

    /**
     * 解析后的调用前参数（可能JSON/SQL/XML等格式）
     */
    private String paramBeforeInvoke = "{}";

    /**
     * 远程调用响应（JSON 格式）
     */
    private String remoteInvokeResponse = "{}";

    /**
     * 调用后参数（JSON 格式）
     */
    private String paramAfterInvoke = "{}";

    /**
     * 调用时间
     */
    private String invokeTime;

    /**
     * 异常信息
     */
    private String errorMessage;

    /**
     * 调用是否成功
     */
    private boolean success = true;
}
