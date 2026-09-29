package com.helianhealth.iflow.gateway.rest;

import com.helianhealth.iflow.common.domain.run.StartWorkflowRequest;
import com.helianhealth.iflow.common.exception.WorkFlowConfigException;
import com.helianhealth.iflow.common.exception.WorkFlowErrorCode;
import com.helianhealth.iflow.common.exception.WorkFlowException;
import com.helianhealth.iflow.common.utils.JsonUtils;
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

    public OpenApiServiceImpl(WorkflowExecutionService workflowExecutionService) {
        this.workflowExecutionService = workflowExecutionService;
    }

    @Override
    public String invoke(OpenApiRequest request) {
        String workflowName = normalizeWorkflowName(request.path());
        try {
            WorkflowModel executed =
                    workflowExecutionService.startWorkflow(
                            StartWorkflowRequest.builder()
                                    .name(workflowName)
                                    .input(request.toWorkflowInput())
                                    .fileAccess(request.fileAccess())
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
        Object output = workflow.getOutput();
        if (output instanceof Map<?, ?> map && map.get(RESPONSE_KEY) != null) {
            return String.valueOf(map.get(RESPONSE_KEY));
        }
        return output instanceof String text ? text : JsonUtils.toJsonStringWithNulls(output);
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
