package com.helianhealth.iflow.gateway.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.helianhealth.iflow.common.domain.run.StartWorkflowRequest;
import com.helianhealth.iflow.common.exception.WorkFlowConfigException;
import com.helianhealth.iflow.common.exception.WorkFlowErrorCode;
import com.helianhealth.iflow.common.exception.WorkFlowException;
import com.helianhealth.iflow.core.model.WorkflowModel;
import com.helianhealth.iflow.core.service.WorkflowExecutionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@Slf4j
public class OpenApiServiceImpl implements OpenApiService {

    private static final String RESPONSE_KEY = "response";

    private final WorkflowExecutionService workflowExecutionService;
    private final ObjectWriter jsonWriter;

    public OpenApiServiceImpl(WorkflowExecutionService workflowExecutionService, ObjectMapper objectMapper) {
        this.workflowExecutionService = workflowExecutionService;
        this.jsonWriter = objectMapper.copy()
                .setSerializationInclusion(JsonInclude.Include.ALWAYS)
                .writer().without(SerializationFeature.INDENT_OUTPUT);
    }

    @Override
    public String invoke(OpenApiRequest request) {
        String workflowRoute = normalizeWorkflowRoute(request.path());
        try {
            WorkflowModel executed =
                    workflowExecutionService.startWorkflowSync(
                            StartWorkflowRequest.builder()
                                    .route(workflowRoute)
                                    .input(request.toWorkflowInput())
                                    .build());
            return toOpenApiResponse(executed);
        } catch (WorkFlowConfigException e) {
            if (e.getErrorCode() == WorkFlowErrorCode.WORKFLOW_NOT_FOUND.getCode()) {
                log.error("open api接口未匹配工作流, route={}", workflowRoute);
                return buildErrorResponse(
                        WorkFlowErrorCode.METHOD_NOT_SUPPORT.getCode(), "route=" + workflowRoute);
            }
            log.error("open api调度异常, route={}: {}", workflowRoute, e.getMessage(), e);
            return buildErrorResponse(e.getErrorCode(), e.getErrorMessage());
        } catch (WorkFlowException e) {
            log.error("open api调度异常, route={}: {}", workflowRoute, e.getMessage(), e);
            return buildErrorResponse(e.getErrorCode(), e.getErrorMessage());
        } catch (Exception e) {
            log.error("open api调度未知异常, route={}", workflowRoute, e);
            return buildErrorResponse(WorkFlowErrorCode.INTERNAL_ERROR.getCode(), extractRootCauseMessage(e));
        }
    }

    /**
     * Open API 用请求路径作为工作流 route；去掉前导 {@code /}。
     */
    static String normalizeWorkflowRoute(String routeKey) {
        if (!StringUtils.hasText(routeKey)) {
            return routeKey;
        }
        String route = routeKey.trim();
        while (route.startsWith("/")) {
            route = route.substring(1);
        }
        return route;
    }

    private String toOpenApiResponse(WorkflowModel workflow) {
        if (!workflow.getStatus().isSuccessful()) {
            return buildErrorResponse(
                    WorkFlowErrorCode.INTERNAL_ERROR.getCode(),
                    resolveFailureMessage(workflow));
        }
        Object output = workflow.getOutput();
        if (output instanceof Map<?, ?> map && map.get(RESPONSE_KEY) != null) {
            return String.valueOf(map.get(RESPONSE_KEY));
        }
        return output instanceof String text ? text : toJsonString(output);
    }

    private String resolveFailureMessage(WorkflowModel workflow) {
        if (StringUtils.hasText(workflow.getReasonForInCompletion())) {
            return workflow.getReasonForInCompletion();
        }
        return workflow.getStatus().name();
    }

    private String buildErrorResponse(int code, String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        if (message != null) {
            error.put("message", message);
        }
        return toJsonString(error);
    }

    private String toJsonString(Object value) {
        try {
            return jsonWriter.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("JSON序列化失败: " + e.getMessage(), e);
        }
    }

    private String extractRootCauseMessage(Throwable e) {
        Throwable current = e;
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().isEmpty()) {
                return current.getMessage();
            }
            current = current.getCause();
        }
        return e.getClass().getName();
    }
}
