package com.helianhealth.iflow.common.base;

/**
 * 框架标准响应数据
 */
public class ResultData<T> {

    private final int code;

    private final String message;

    private final T data;

    public ResultData(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> ResultData<T> ok(T data) {
        return new ResultData<>(200, "OK", data);
    }

    public static <T> ResultData<T> ok() {
        return new ResultData<>(200, "OK", null);
    }

    public static <T> ResultData<T> error(String message) {
        return new ResultData<>(-1, message, null);
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }
}
