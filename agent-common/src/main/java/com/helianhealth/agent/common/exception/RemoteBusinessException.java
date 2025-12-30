package com.helianhealth.agent.common.exception;

public enum RemoteBusinessException implements BusinessCode, BusinessExceptionSupplier {
    ;

    @Override
    public String getCode() {
        return null;
    }

    @Override
    public String getMessage() {
        return null;
    }

    @Override
    public BaseBusinessException toException() {
        return null;
    }

    @Override
    public BaseBusinessException toException(String message) {
        return null;
    }
}
