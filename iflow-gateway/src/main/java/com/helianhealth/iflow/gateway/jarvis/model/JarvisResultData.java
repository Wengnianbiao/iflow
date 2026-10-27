package com.helianhealth.iflow.gateway.jarvis.model;

import java.util.Objects;

/**
 * Jarvis 链路错误响应数据。
 *
 * <p>成功响应不使用该结构，而是直接返回工作流输出。
 */
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

    public String getCode() {
        return this.code;
    }

    public String getMessage() {
        return this.message;
    }

    public T getRsp() {
        return this.rsp;
    }

    public String getAlertMsg() {
        return this.alertMsg;
    }

    public String getReqParam() {
        return this.reqParam;
    }

    public String getResParam() {
        return this.resParam;
    }

    public Integer getErrorType() {
        return this.errorType;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setRsp(T rsp) {
        this.rsp = rsp;
    }

    public void setAlertMsg(String alertMsg) {
        this.alertMsg = alertMsg;
    }

    public void setReqParam(String reqParam) {
        this.reqParam = reqParam;
    }

    public void setResParam(String resParam) {
        this.resParam = resParam;
    }

    public void setErrorType(Integer errorType) {
        this.errorType = errorType;
    }

    protected boolean canEqual(Object other) {
        return other instanceof JarvisResultData;
    }

    public JarvisResultData() {
    }

    public JarvisResultData(
            String code,
            String message,
            T rsp,
            String alertMsg,
            String reqParam,
            String resParam,
            Integer errorType) {
        this.code = code;
        this.message = message;
        this.rsp = rsp;
        this.alertMsg = alertMsg;
        this.reqParam = reqParam;
        this.resParam = resParam;
        this.errorType = errorType;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof JarvisResultData<?> other)) {
            return false;
        }
        if (!other.canEqual(this)) {
            return false;
        }
        return Objects.equals(getCode(), other.getCode())
                && Objects.equals(getMessage(), other.getMessage())
                && Objects.equals(getRsp(), other.getRsp())
                && Objects.equals(getAlertMsg(), other.getAlertMsg())
                && Objects.equals(getReqParam(), other.getReqParam())
                && Objects.equals(getResParam(), other.getResParam())
                && Objects.equals(getErrorType(), other.getErrorType());
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                getCode(),
                getMessage(),
                getRsp(),
                getAlertMsg(),
                getReqParam(),
                getResParam(),
                getErrorType());
    }

    @Override
    public String toString() {
        return "JarvisResultData(code=" + getCode()
                + ", message=" + getMessage()
                + ", rsp=" + getRsp()
                + ", alertMsg=" + getAlertMsg()
                + ", reqParam=" + getReqParam()
                + ", resParam=" + getResParam()
                + ", errorType=" + getErrorType() + ")";
    }

    public static class JarvisResultDataBuilder<T> {

        private String code;
        private String message;
        private T rsp;
        private String alertMsg;
        private String reqParam;
        private String resParam;
        private Integer errorType;

        JarvisResultDataBuilder() {
        }

        public JarvisResultDataBuilder<T> code(String code) {
            this.code = code;
            return this;
        }

        public JarvisResultDataBuilder<T> message(String message) {
            this.message = message;
            return this;
        }

        public JarvisResultDataBuilder<T> rsp(T rsp) {
            this.rsp = rsp;
            return this;
        }

        public JarvisResultDataBuilder<T> alertMsg(String alertMsg) {
            this.alertMsg = alertMsg;
            return this;
        }

        public JarvisResultDataBuilder<T> reqParam(String reqParam) {
            this.reqParam = reqParam;
            return this;
        }

        public JarvisResultDataBuilder<T> resParam(String resParam) {
            this.resParam = resParam;
            return this;
        }

        public JarvisResultDataBuilder<T> errorType(Integer errorType) {
            this.errorType = errorType;
            return this;
        }

        public JarvisResultData<T> build() {
            return new JarvisResultData<>(
                    code,
                    message,
                    rsp,
                    alertMsg,
                    reqParam,
                    resParam,
                    errorType);
        }

        @Override
        public String toString() {
            return "JarvisResultData.JarvisResultDataBuilder(code=" + code
                    + ", message=" + message
                    + ", rsp=" + rsp
                    + ", alertMsg=" + alertMsg
                    + ", reqParam=" + reqParam
                    + ", resParam=" + resParam
                    + ", errorType=" + errorType + ")";
        }
    }

    public static <T> JarvisResultDataBuilder<T> builder() {
        return new JarvisResultDataBuilder<>();
    }
}
