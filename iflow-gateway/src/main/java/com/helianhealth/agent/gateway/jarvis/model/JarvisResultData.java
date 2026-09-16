package com.helianhealth.agent.gateway.jarvis.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Jarvis 链路错误响应数据。
 *
 * <p>成功响应不使用该结构，而是直接返回工作流输出。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JarvisResultData<T> {

    private String code;
    private String message;
    private T rsp;
    private String alertMsg;
    private String reqParam;
    private String resParam;
    private Integer errorType;

    public static <T> JarvisResultData<T> fail() {
        return JarvisResultData.<T>builder().code("-1").build();
    }

    public static <T> JarvisResultData<T> ok() {
        return JarvisResultData.<T>builder().code("200").message("OK").build();
    }

    public static <T> JarvisResultData<T> ok(T data) {
        return JarvisResultData.<T>builder().code("200").message("OK").rsp(data).build();
    }

    public static <T> JarvisResultData<T> error(String msg) {
        return JarvisResultData.<T>builder().code("-1").message(msg).build();
    }
}
