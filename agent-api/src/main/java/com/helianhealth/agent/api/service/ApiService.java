package com.helianhealth.agent.api.service;

import com.helianhealth.agent.common.ResultData;
import com.helianhealth.agent.api.model.request.JarvisRequest;

import javax.servlet.http.HttpServletRequest;

/**
 * API服务接口
 */
public interface ApiService {

    /**
     * 处理Jarvis API请求
     *
     * @param request Jarvis请求对象
     * @return 处理结果
     */
    ResultData<Object> invokeJarvisApi(JarvisRequest request);

    /**
     * 处理Open API请求
     *
     * @param requestBody 请求体
     * @param httpRequest HTTP请求对象
     * @return 响应字符串
     */
    String invokeOpenApi(String requestBody, HttpServletRequest httpRequest);
}
