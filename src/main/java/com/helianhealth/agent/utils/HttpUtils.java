package com.helianhealth.agent.utils;

import com.helianhealth.agent.remote.manager.HttpApiClientManager;
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
}
