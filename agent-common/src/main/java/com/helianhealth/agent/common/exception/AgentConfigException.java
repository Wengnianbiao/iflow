package com.helianhealth.agent.common.exception;

/**
 * 配置异常
 * <p>
 * 用于工作流配置错误、参数配置错误、表达式配置错误等场景。
 * 这类异常表示"用户配置了错误的工作流"，运维人员需要去修改配置。
 */
public class AgentConfigException extends AgentException {

    public AgentConfigException(AgentErrorCode errorCode) {
        super(errorCode);
    }

    public AgentConfigException(AgentErrorCode errorCode, String detail) {
        super(errorCode, detail);
    }

    public AgentConfigException(AgentErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public AgentConfigException(AgentErrorCode errorCode, String detail, Throwable cause) {
        super(errorCode, detail, cause);
    }
}
