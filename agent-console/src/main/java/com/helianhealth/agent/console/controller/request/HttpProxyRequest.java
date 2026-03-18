package com.helianhealth.agent.console.controller.request;

import lombok.Data;

import java.util.Map;

/**
 * HTTP代理调用请求
 */
@Data
public class HttpProxyRequest {
    
    /**
     * 目标URL
     */
    private String url;
    
    /**
     * HTTP方法 (GET, POST, PUT, DELETE, PATCH等)
     */
    private String method;
    
    /**
     * 请求头
     */
    private Map<String, String> headers;
    
    /**
     * 请求体（JSON对象或字符串）
     */
    private Object body;
    
    /**
     * URL查询参数
     */
    private Map<String, String> queryParams;
    
    /**
     * 超时时间（毫秒）
     */
    private Integer timeout;
}
