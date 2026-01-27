package com.helianhealth.agent.invocation.utils;

import com.helianhealth.agent.invocation.remote.manager.HttpApiClientManager;
import org.apache.hc.core5.http.ParseException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class HttpUtils {

    private static final HttpApiClientManager httpApiClientManager = HttpApiClientManager.getInstance();

    // 默认headers
    private static final Map<String, String> DEFAULT_HEADERS = new HashMap<>();
    static {
        DEFAULT_HEADERS.put("Content-Type", "application/json");
    }

    public static String get(String url) throws IOException, ParseException {
        return httpApiClientManager.executeGet(url, DEFAULT_HEADERS);
    }

    public static String get(String url, Map<String, String> headers) throws IOException, ParseException {
        return httpApiClientManager.executeGet(url, headers);
    }

    public static String post(String url, String requestBody) throws IOException, ParseException {
        return httpApiClientManager.executePost(url, DEFAULT_HEADERS, requestBody);
    }

    public static String post(String url,  Map<String, String> headers, String requestBody) throws IOException, ParseException {
        return httpApiClientManager.executePost(url, headers, requestBody);
    }

    /**
     * 发送 application/x-www-form-urlencoded 格式的 POST 请求
     * @param url 请求URL
     * @param formParams 表单参数（会自动编码）
     * @return 响应内容
     * @throws IOException IO异常
     * @throws ParseException 解析异常
     */
    public static String postForm(String url, Map<String, String> formParams) throws IOException, ParseException {
        return httpApiClientManager.executePostForm(url, null, formParams);
    }

    /**
     * 发送 application/x-www-form-urlencoded 格式的 POST 请求（带自定义 Headers）
     * @param url 请求URL
     * @param headers 自定义请求头
     * @param formParams 表单参数（会自动编码）
     * @return 响应内容
     * @throws IOException IO异常
     * @throws ParseException 解析异常
     */
    public static String postForm(String url, Map<String, String> headers, Map<String, String> formParams) throws IOException, ParseException {
        return httpApiClientManager.executePostForm(url, headers, formParams);
    }
}
