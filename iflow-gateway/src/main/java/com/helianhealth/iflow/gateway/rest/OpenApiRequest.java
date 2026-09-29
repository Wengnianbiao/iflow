package com.helianhealth.iflow.gateway.rest;

import com.helianhealth.iflow.common.domain.run.FileAccess;

import java.util.LinkedHashMap;
import java.util.Map;

/** 已解析的 HTTP 入站上下文。rawBody 为 null 表示未启用原文保存。 */
public record OpenApiRequest(
        Object body,
        Map<String, Object> query,
        Map<String, Object> headers,
        Map<String, Object> files,
        String method,
        String path,
        String rawBody,
        FileAccess fileAccess,
        AutoCloseable resourceScope) implements AutoCloseable {

    public OpenApiRequest(
            Object body,
            Map<String, Object> query,
            Map<String, Object> headers,
            String method,
            String path,
            String rawBody) {
        this(body, query, headers, Map.of(), method, path, rawBody, null, () -> {});
    }

    public Map<String, Object> toWorkflowInput() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("body", body);
        input.put("query", query);
        input.put("headers", headers);
        input.put("files", files);
        input.put("method", method);
        input.put("path", path);
        if (rawBody != null) {
            input.put("rawBody", rawBody);
        }
        return input;
    }

    @Override
    public void close() throws Exception {
        if (resourceScope != null) {
            resourceScope.close();
        }
    }
}
