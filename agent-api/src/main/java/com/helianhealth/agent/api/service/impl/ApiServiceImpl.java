package com.helianhealth.agent.api.service.impl;

import com.helianhealth.agent.api.WorkFlowOrchestrator;
import com.helianhealth.agent.api.model.response.JarvisResultData;
import com.helianhealth.agent.api.model.request.JarvisRequest;
import com.helianhealth.agent.api.service.ApiService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    public JarvisResultData<Object> invokeJarvisApi(JarvisRequest request) {
        return dispatcherManager.jarvisDispatch(request);
    }

    @Override
    public String invokeOpenApi(String requestBody, String interfaceUri) {
        return dispatcherManager.dispatch(requestBody, interfaceUri);
    }
}
