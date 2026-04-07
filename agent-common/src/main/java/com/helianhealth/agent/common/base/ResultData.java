package com.helianhealth.agent.common.base;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 框架标准响应数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResultData<T> {

    private String code;

    private String message;

    private T rsp;

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

    public static <T> ResultData<T> ok(T data) {
        return new ResultData<>(ResultStatusCode.OK, data);
    }

    public static <T> ResultData<T> ok() {
        return new ResultData<>(ResultStatusCode.OK, null);
    }
}
