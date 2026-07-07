package com.helianhealth.agent.console.model.response;

import com.helianhealth.agent.core.model.WorkflowModel;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WorkflowExecutionSummary {

    private String workflowId;
    private String workflowName;
    private int workflowVersion;
    private String status;
    private String correlationId;
    private long startTime;
    private long endTime;
    private long updateTime;
    private Long durationMs;
    private String reasonForIncompletion;
    private String failedTaskId;

    public static WorkflowExecutionSummary from(WorkflowModel workflow) {
        long startTime = workflow.getStartTime();
        long endTime = workflow.getEndTime();
        Long durationMs = null;
        if (startTime > 0) {
            durationMs = (endTime > 0 ? endTime : System.currentTimeMillis()) - startTime;
        }
        return WorkflowExecutionSummary.builder()
                .workflowId(workflow.getWorkflowId())
                .workflowName(workflow.getWorkflowName())
                .workflowVersion(workflow.getWorkflowVersion())
                .status(workflow.getStatus() == null ? null : workflow.getStatus().name())
                .correlationId(workflow.getCorrelationId())
                .startTime(startTime)
                .endTime(endTime)
                .updateTime(workflow.getUpdateTime())
                .durationMs(durationMs)
                .reasonForIncompletion(workflow.getReasonForInCompletion())
                .failedTaskId(workflow.getFailedTaskId())
                .build();
    }
}
