package com.helianhealth.iflow.common.exception;

/**
 * Agent异常基类
 */
public class WorkFlowException extends RuntimeException {

    private final int errorCode;
    private final String errorMessage;
    private Throwable causeThrowable;

    public WorkFlowException(WorkFlowErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode.getCode();
        this.errorMessage = errorCode.getMessage();
    }

    public WorkFlowException(WorkFlowErrorCode errorCode, String errorMessage) {
        super(errorCode.getMessage());
        this.errorCode = errorCode.getCode();
        this.errorMessage = errorMessage;
    }

    public WorkFlowException(WorkFlowErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode.getCode();
        this.errorMessage = errorCode.getMessage();
        this.causeThrowable = getRootCause(cause);
    }

    public WorkFlowException(WorkFlowErrorCode errorCode, String errorMessage, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode.getCode();
        this.errorMessage = errorMessage;
        this.causeThrowable = getRootCause(cause);
    }

    public int getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Throwable getCauseThrowable() {
        return causeThrowable;
    }

    private Throwable getRootCause(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    @Override
    public String getMessage() {
        return String.format("[%d] %s", errorCode, errorMessage);
    }
}
