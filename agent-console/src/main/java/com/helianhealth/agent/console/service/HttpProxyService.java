package com.helianhealth.agent.console.service;

import com.alibaba.fastjson2.JSON;
import com.helianhealth.agent.console.controller.request.HttpProxyRequest;
import com.helianhealth.agent.console.controller.reponse.HttpProxyResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * HTTP代理调用服务
 * 用于从后端发起HTTP请求，绕过浏览器CORS限制
 */
@Service
@Slf4j
public class HttpProxyService {

    /**
     * 执行HTTP代理请求
     */
    public HttpProxyResponse execute(HttpProxyRequest request) {
        // 参数校验
        if (request == null || !StringUtils.hasText(request.getUrl())) {
            throw new IllegalArgumentException("请求URL不能为空");
        }
        
        String method = request.getMethod();
        if (!StringUtils.hasText(method)) {
            method = "GET";
        }
        
        // 构建RestTemplate（每次新建以支持不同超时配置）
        int timeout = request.getTimeout() != null ? request.getTimeout() : 30000;
        RestTemplate restTemplate = createRestTemplate(timeout);
        
        // 构建URL（包含查询参数）
        URI uri = buildUri(request.getUrl(), request.getQueryParams());
        
        // 构建请求头
        HttpHeaders headers = new HttpHeaders();
        if (request.getHeaders() != null) {
            request.getHeaders().forEach(headers::set);
        }
        
        // 如果没有设置Content-Type，默认设置为JSON
        if (!headers.containsKey(HttpHeaders.CONTENT_TYPE) && request.getBody() != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        
        // 构建请求体
        Object body = request.getBody();
        String bodyStr = null;
        if (body != null) {
            bodyStr = body instanceof String ? (String) body : JSON.toJSONString(body);
        }
        
        HttpEntity<String> entity = new HttpEntity<>(bodyStr, headers);
        
        log.info("HTTP代理请求: {} {}, headers={}", method, uri, headers);
        
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                uri,
                HttpMethod.valueOf(method.toUpperCase()),
                entity,
                String.class
            );
            
            return buildResponse(response);
        } catch (HttpStatusCodeException e) {
            // HTTP错误（4xx, 5xx）
            log.warn("HTTP代理请求返回错误状态码: {} {}", e.getStatusCode(), e.getStatusText());
            return HttpProxyResponse.builder()
                .status(e.getStatusCode().value())
                .statusText(e.getStatusText())
                .headers(extractHeaders(e.getResponseHeaders()))
                .body(parseResponseBody(e.getResponseBodyAsString()))
                .build();
        } catch (Exception e) {
            log.error("HTTP代理请求失败: {}", e.getMessage(), e);
            throw new RuntimeException("HTTP请求失败: " + e.getMessage(), e);
        }
    }
    
    private RestTemplate createRestTemplate(int timeout) {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory = 
            new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return new RestTemplate(factory);
    }
    
    private URI buildUri(String url, Map<String, String> queryParams) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url);
        if (queryParams != null && !queryParams.isEmpty()) {
            queryParams.forEach(builder::queryParam);
        }
        return builder.build().toUri();
    }
    
    private HttpProxyResponse buildResponse(ResponseEntity<String> response) {
        return HttpProxyResponse.builder()
            .status(response.getStatusCode().value())
            .statusText(response.getStatusCode().toString())
            .headers(extractHeaders(response.getHeaders()))
            .body(parseResponseBody(response.getBody()))
            .build();
    }
    
    private Map<String, String> extractHeaders(HttpHeaders headers) {
        Map<String, String> result = new HashMap<>();
        if (headers != null) {
            headers.forEach((key, values) -> {
                if (values != null && !values.isEmpty()) {
                    result.put(key, String.join(", ", values));
                }
            });
        }
        return result;
    }
    
    private Object parseResponseBody(String body) {
        if (body == null || body.isEmpty()) {
            return null;
        }
        // 尝试解析为JSON
        try {
            if (body.trim().startsWith("{") || body.trim().startsWith("[")) {
                return JSON.parse(body);
            }
        } catch (Exception e) {
            // 不是JSON，返回原始字符串
        }
        return body;
    }
}
