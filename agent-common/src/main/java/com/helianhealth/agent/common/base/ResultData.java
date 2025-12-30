package com.helianhealth.agent.common.base;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ResultData<T> {

    private String code;

    private String message;

    private T rsp;

    private String alertMsg;
    private String reqParam;
    private String resParam;
    private Integer errorType;

    public ResultData() {
    }

    public ResultData(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public ResultData(ResultStatusCode statusCode, String message, T data) {
        this.code = statusCode.getCode();
        this.message = message;
        this.rsp = data;
    }

    public ResultData(ResultStatusCode statusCode, T data) {
        this.code = statusCode.getCode();
        this.message = statusCode.getMsg();
        this.rsp = data;
    }

    public ResultData(String code, String message, T rsp, String alertMsg, String reqParam, String resParam, Integer errorType) {
        this.code = code;
        this.message = message;
        this.rsp = rsp;
        this.alertMsg = alertMsg;
        this.reqParam = reqParam;
        this.resParam = resParam;
        this.errorType = errorType;
    }

    public ResultData(String code, String message, String reqParam, String resParam, Integer errorType) {
        this.code = code;
        this.message = message;
        this.reqParam = reqParam;
        this.resParam = resParam;
        this.errorType = errorType;
    }

    public ResultData(String code, String message, String reqParam, String resParam, Integer errorType, String alertMsg) {
        this.code = code;
        this.message = message;
        this.reqParam = reqParam;
        this.resParam = resParam;
        this.errorType = errorType;
        this.alertMsg = alertMsg;
    }

    public static <T> ResultData<T> fail(String msg, String reqParam, String resParam) {
        return new ResultData<>("-1", msg, reqParam, resParam, null);
    }

    public static <T> ResultData<T> fail() {
        return new ResultData<>("-1", null, null, null, null);
    }

    public static <T> ResultData<T> ok(T data) {
        return new ResultData<>(ResultStatusCode.OK, data);
    }

    public static <T> ResultData<T> ok() {
        return new ResultData<>(ResultStatusCode.OK, null);
    }
}
