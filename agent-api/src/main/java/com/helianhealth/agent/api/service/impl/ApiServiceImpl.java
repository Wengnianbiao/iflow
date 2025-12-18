package com.helianhealth.agent.api.service.impl;

import com.helianhealth.agent.api.FlowNodeDispatcher;
import com.helianhealth.agent.common.ResultData;
import com.helianhealth.agent.api.model.request.JarvisRequest;
import com.helianhealth.agent.api.service.ApiService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;

/**
 * API服务实现类
 */
@Service
@Slf4j
@AllArgsConstructor
public class ApiServiceImpl implements ApiService {

    private final FlowNodeDispatcher dispatcherManager;

    @Override
    public ResultData<Object> invokeJarvisApi(JarvisRequest request) {
        MDC.put("method", request.getBusinessMethod() == null ? "" : request.getBusinessMethod());
        try {
            return dispatcherManager.jarvisDispatch(request);
        } finally {
            MDC.clear();
        }
    }

    @Override
    public String invokeOpenApi(String requestBody, HttpServletRequest httpRequest) {
        String requestURI = httpRequest.getRequestURI();
        MDC.put("invokeUri", requestURI == null ? "" : requestURI);
        try {
            return dispatcherManager.dispatch(requestBody, requestURI);
        } finally {
            MDC.clear();
        }
    }
}
