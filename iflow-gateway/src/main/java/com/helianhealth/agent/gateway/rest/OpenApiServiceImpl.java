package com.helianhealth.agent.gateway.rest;

import com.helianhealth.agent.common.domain.run.StartWorkflowRequest;
import com.helianhealth.agent.common.exception.WorkFlowConfigException;
import com.helianhealth.agent.common.exception.WorkFlowErrorCode;
import com.helianhealth.agent.common.exception.WorkFlowException;
import com.helianhealth.agent.common.utils.JsonUtils;
import com.helianhealth.agent.core.model.WorkflowModel;
import com.helianhealth.agent.core.service.WorkflowExecutionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@Slf4j
public class OpenApiServiceImpl implements OpenApiService {

    private static final String REQUEST_KEY = "request";
    private static final String RESPONSE_KEY = "response";

    private final WorkflowExecutionService workflowExecutionService;

    public OpenApiServiceImpl(WorkflowExecutionService workflowExecutionService) {
        this.workflowExecutionService = workflowExecutionService;
    }

    @Override
    public String invoke(String requestBody, String routeKey) {
        String workflowName = normalizeWorkflowName(routeKey);
        try {
            Map<String, Object> input = new LinkedHashMap<>();
            input.put(REQUEST_KEY, requestBody != null ? requestBody : "");
            WorkflowModel executed =
                    workflowExecutionService.startWorkflow(
                            StartWorkflowRequest.builder()
                                    .name(workflowName)
                                    .input(input)
                                    .build());
            return toOpenApiResponse(executed);
        } catch (WorkFlowConfigException e) {
            if (e.getErrorCode() == WorkFlowErrorCode.WORKFLOW_NOT_FOUND.getCode()) {
                log.error("open api接口未匹配工作流, name={}", workflowName);
                return buildErrorResponse(
                        WorkFlowErrorCode.METHOD_NOT_SUPPORT.getCode(), "name=" + workflowName);
            }
            log.error("open api调度异常, name={}: {}", workflowName, e.getMessage(), e);
            return buildErrorResponse(e.getErrorCode(), e.getErrorMessage());
        } catch (WorkFlowException e) {
            log.error("open api调度异常, name={}: {}", workflowName, e.getMessage(), e);
            return buildErrorResponse(e.getErrorCode(), e.getErrorMessage());
        } catch (Exception e) {
            log.error("open api调度未知异常, name={}", workflowName, e);
            return buildErrorResponse(WorkFlowErrorCode.INTERNAL_ERROR.getCode(), extractRootCauseMessage(e));
        }
    }

    /**
     * Open API 用请求路径作为工作流 name；去掉前导 {@code /}。
     */
    static String normalizeWorkflowName(String routeKey) {
        if (!StringUtils.hasText(routeKey)) {
            return routeKey;
        }
        String name = routeKey.trim();
        while (name.startsWith("/")) {
            name = name.substring(1);
        }
        return name;
    }

    private String toOpenApiResponse(WorkflowModel workflow) {
        if (!workflow.getStatus().isSuccessful()) {
            return buildErrorResponse(
                    WorkFlowErrorCode.INTERNAL_ERROR.getCode(),
                    resolveFailureMessage(workflow));
        }
        Map<String, Object> output = workflow.getOutput() != null ? workflow.getOutput() : Map.of();
        Object response = output.get(RESPONSE_KEY);
        if (response != null) {
            return String.valueOf(response);
        }
        return JsonUtils.toJsonString(output);
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
        error.put("message", message);
        return JsonUtils.toJsonString(error);
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
