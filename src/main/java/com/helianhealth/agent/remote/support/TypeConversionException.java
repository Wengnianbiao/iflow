package com.helianhealth.agent.remote.support;

/**
 * 类型转换异常
 */
public class TypeConversionException extends RuntimeException {
    public TypeConversionException(String message) {
        super(message);
    }

    public TypeConversionException(String message, Throwable cause) {
        super(message, cause);
    }
}
