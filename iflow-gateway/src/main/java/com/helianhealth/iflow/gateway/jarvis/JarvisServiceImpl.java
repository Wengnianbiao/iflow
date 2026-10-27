package com.helianhealth.iflow.gateway.jarvis;

import com.helianhealth.iflow.common.domain.run.StartWorkflowRequest;
import com.helianhealth.iflow.common.exception.WorkFlowException;
import com.helianhealth.iflow.core.model.WorkflowModel;
import com.helianhealth.iflow.core.service.WorkflowExecutionService;
import com.helianhealth.iflow.gateway.jarvis.model.JarvisResultData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;

@Service
@Slf4j
public class JarvisServiceImpl implements JarvisService {

    private final WorkflowExecutionService workflowExecutionService;

    public JarvisServiceImpl(WorkflowExecutionService workflowExecutionService) {
        this.workflowExecutionService = workflowExecutionService;
    }

    @Override
    public Object invoke(Map<String, Object> input) {
        if (!(input.get("body") instanceof Map<?, ?> body)
                || !(body.get("BusinessMethod") instanceof String workflowRoute)
                || !StringUtils.hasText(workflowRoute)) {
            return JarvisResultData.error("BusinessMethod 必须为非空字符串");
        }
        try {
            WorkflowModel executedWorkflow =
                    workflowExecutionService.startWorkflowSync(
                            StartWorkflowRequest.builder()
                                    .route(workflowRoute)
                                    .input(input)
                                    .build());
            return toJarvisResponse(executedWorkflow);
        } catch (WorkFlowException e) {
            log.error("jarvis调度异常, route={}: {}", workflowRoute, e.getMessage(), e);
            return JarvisResultData.error(workflowRoute + " | " + e.getMessage());
        } catch (Exception e) {
            log.error("jarvis调度未知异常, route={}", workflowRoute, e);
            return JarvisResultData.error(workflowRoute + " | " + extractRootCauseMessage(e));
        }
    }

    private Object toJarvisResponse(WorkflowModel workflow) {
        if (!workflow.getStatus().isSuccessful()) {
            return JarvisResultData.error(resolveFailureMessage(workflow));
        }
        return workflow.getOutput();
    }

    private String resolveFailureMessage(WorkflowModel workflow) {
        if (StringUtils.hasText(workflow.getReasonForInCompletion())) {
            return workflow.getReasonForInCompletion();
        }
        return workflow.getStatus().name();
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
