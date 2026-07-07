package com.helianhealth.agent.core.exception;

/** 表示当前操作可在稍后安全重试。 */
public class TransientException extends RuntimeException {

    public TransientException(String message) {
        super(message);
    }

    public TransientException(String message, Throwable cause) {
        super(message, cause);
    }
}
