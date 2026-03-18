package com.helianhealth.agent.console.controller.reponse;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * HTTP代理调用响应
 */
@Data
@Builder
public class HttpProxyResponse {
    
    /**
     * HTTP状态码
     */
    private int status;
    
    /**
     * 状态文本
     */
    private String statusText;
    
    /**
     * 响应头
     */
    private Map<String, String> headers;
    
    /**
     * 响应体
     */
    private Object body;
}
