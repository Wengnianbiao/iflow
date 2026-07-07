package com.helianhealth.agent.gateway.jarvis;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.helianhealth.agent.common.domain.run.StartWorkflowRequest;
import com.helianhealth.agent.common.exception.WorkFlowConfigException;
import com.helianhealth.agent.common.exception.WorkFlowErrorCode;
import com.helianhealth.agent.common.exception.WorkFlowException;
import com.helianhealth.agent.common.utils.JsonUtils;
import com.helianhealth.agent.core.model.WorkflowModel;
import com.helianhealth.agent.core.service.WorkflowExecutionService;
import com.helianhealth.agent.gateway.jarvis.model.JarvisRequest;
import com.helianhealth.agent.gateway.jarvis.model.JarvisResultData;
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
    public JarvisResultData<Object> invoke(JarvisRequest request) {

        String workflowName = request.getBusinessMethod();
        try {
            Map<String, Object> input = JsonUtils.fromBean(request);
            WorkflowModel executedWorkflow =
                    workflowExecutionService.startWorkflow(
                            StartWorkflowRequest.builder()
                                    .name(workflowName)
                                    .input(input)
                                    .build());
            return toJarvisResult(executedWorkflow);
        } catch (WorkFlowConfigException e) {
            if (e.getErrorCode() == WorkFlowErrorCode.WORKFLOW_NOT_FOUND.getCode()) {
                log.warn("jarvis接口未匹配工作流, name={}", workflowName);
                return JarvisResultData.ok();
            }
            log.error("jarvis调度异常, name={}: {}", workflowName, e.getMessage(), e);
            return JarvisResultData.error(workflowName + " | " + e.getMessage());
        } catch (WorkFlowException e) {
            log.error("jarvis调度异常, name={}: {}", workflowName, e.getMessage(), e);
            return JarvisResultData.error(workflowName + " | " + e.getMessage());
        } catch (Exception e) {
            log.error("jarvis调度未知异常, name={}", workflowName, e);
            return JarvisResultData.error(workflowName + " | " + extractRootCauseMessage(e));
        }
    }

    private JarvisResultData<Object> toJarvisResult(WorkflowModel workflow) {
        if (!workflow.getStatus().isSuccessful()) {
            return JarvisResultData.error(resolveFailureMessage(workflow));
        }
        Map<String, Object> output = workflow.getOutput() != null ? workflow.getOutput() : Map.of();
        if (output.isEmpty()) {
            return JarvisResultData.ok();
        }
        return JSON.parseObject(JSON.toJSONString(output), new TypeReference<>() {
        });
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
