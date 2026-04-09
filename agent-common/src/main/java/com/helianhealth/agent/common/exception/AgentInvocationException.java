package com.helianhealth.agent.common.exception;

/**
 * 远程调用异常
 * <p>
 * 用于数据库连接失败、SQL执行失败、HTTP调用超时、WebService调用失败等场景。
 * 这类异常表示"远程目标有问题"，需要排查网络或对端服务。
 */
public class AgentInvocationException extends AgentException {

    public AgentInvocationException(AgentErrorCode errorCode) {
        super(errorCode);
    }

    public AgentInvocationException(AgentErrorCode errorCode, String detail) {
        super(errorCode, detail);
    }

    public AgentInvocationException(AgentErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public AgentInvocationException(AgentErrorCode errorCode, String detail, Throwable cause) {
        super(errorCode, detail, cause);
    }
}
