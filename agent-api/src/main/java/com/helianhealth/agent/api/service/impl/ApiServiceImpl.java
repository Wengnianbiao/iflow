package com.helianhealth.agent.api.service.impl;

import com.helianhealth.agent.api.WorkFlowOrchestrator;
import com.helianhealth.agent.common.base.ResultData;
import com.helianhealth.agent.api.model.request.JarvisRequest;
import com.helianhealth.agent.api.service.ApiService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

/**
 * API服务实现类
 */
@Service
@Slf4j
@AllArgsConstructor
public class ApiServiceImpl implements ApiService {

    private final WorkFlowOrchestrator dispatcherManager;

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
    public String invokeOpenApi(String requestBody, String interfaceUri) {
        MDC.put("invokeUri", interfaceUri == null ? "" : interfaceUri);
        try {
            return dispatcherManager.dispatch(requestBody, interfaceUri);
        } finally {
            MDC.clear();
        }
    }
}
