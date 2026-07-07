package com.helianhealth.agent.common.exception;

/**
 * 配置异常
 * <p>
 * 用于工作流配置错误、参数配置错误、表达式配置错误等场景。
 * 这类异常表示"用户配置了错误的工作流"，运维人员需要去修改配置。
 */
public class WorkFlowConfigException extends WorkFlowException {

    public WorkFlowConfigException(WorkFlowErrorCode errorCode) {
        super(errorCode);
    }

    public WorkFlowConfigException(WorkFlowErrorCode errorCode, String detail) {
        super(errorCode, detail);
    }

    public WorkFlowConfigException(WorkFlowErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public WorkFlowConfigException(WorkFlowErrorCode errorCode, String detail, Throwable cause) {
        super(errorCode, detail, cause);
    }
}
