package com.helianhealth.iflow.gateway.rest;

import java.util.LinkedHashMap;
import java.util.Map;

/** 已解析的 HTTP 入站数据。files 包含文件元信息及 contentBase64；multipart 的 rawBody 为 null。 */
public record OpenApiRequest(
        Object body,
        Map<String, Object> query,
        Map<String, Object> headers,
        Map<String, Object> files,
        String method,
        String path,
        String rawBody) {

    public OpenApiRequest(
            Object body,
            Map<String, Object> query,
            Map<String, Object> headers,
            String method,
            String path,
            String rawBody) {
        this(body, query, headers, Map.of(), method, path, rawBody);
    }

    public Map<String, Object> toWorkflowInput() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("body", body);
        input.put("query", query);
        input.put("headers", headers);
        input.put("files", files);
        input.put("method", method);
        input.put("path", path);
        input.put("rawBody", rawBody);
        return input;
    }
}
