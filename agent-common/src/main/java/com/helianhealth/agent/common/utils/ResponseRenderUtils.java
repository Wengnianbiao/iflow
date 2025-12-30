package com.helianhealth.agent.common.utils;

import com.helianhealth.agent.common.base.ResultData;
import com.helianhealth.agent.common.base.ResultStatusCode;

public class ResponseRenderUtils {

    public static <T> ResultData<T> success() {
        return new ResultData<>(ResultStatusCode.OK, ResultStatusCode.OK.getMsg(), null);
    }

    public static <T> ResultData<T> render(T rsp) {
        return new ResultData<>(ResultStatusCode.OK, ResultStatusCode.OK.getMsg(), rsp);
    }

    public static <T> ResultData<T> error(String msg) {
        return new ResultData<>(ResultStatusCode.ERROR, msg, null);
    }
}
