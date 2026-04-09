package com.helianhealth.agent.common.exception;

import lombok.Getter;

/**
 * 框架异常基类
 * <p>
 * 所有框架业务异常的根类，携带统一错误码和运行时上下文信息。
 * errorCode.message 是给人看的通用描述，detail 是给排障用的具体现场。
 */
@Getter
public class AgentException extends RuntimeException {

    private final AgentErrorCode errorCode;
    private final String detail;

    public AgentException(AgentErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.detail = null;
    }

    public AgentException(AgentErrorCode errorCode, String detail) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.detail = detail;
    }

    public AgentException(AgentErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
        this.detail = null;
    }

    public AgentException(AgentErrorCode errorCode, String detail, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
        this.detail = detail;
    }

    @Override
    public String getMessage() {
        if (detail != null && !detail.isEmpty()) {
            return String.format("[%d] %s | %s", errorCode.getCode(), errorCode.getMessage(), detail);
        }
        return String.format("[%d] %s", errorCode.getCode(), errorCode.getMessage());
    }
}
