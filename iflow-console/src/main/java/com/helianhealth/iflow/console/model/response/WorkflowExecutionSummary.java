package com.helianhealth.iflow.console.model.response;

import com.helianhealth.iflow.core.model.WorkflowModel;
import com.helianhealth.iflow.common.domain.run.ExecutionSearch;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WorkflowExecutionSummary {

    private String workflowId;
    private String workflowRoute;
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

    public static WorkflowExecutionSummary from(ExecutionSearch.WorkflowSummary workflow) {
        Long duration = workflow.startTime() > 0
                ? (workflow.endTime() > 0 ? workflow.endTime() : System.currentTimeMillis()) - workflow.startTime() : null;
        return WorkflowExecutionSummary.builder().workflowId(workflow.workflowId()).workflowRoute(workflow.workflowRoute())
                .workflowName(workflow.workflowName()).workflowVersion(workflow.workflowVersion()).status(workflow.status())
                .correlationId(workflow.correlationId()).startTime(workflow.startTime()).endTime(workflow.endTime())
                .updateTime(workflow.updateTime()).durationMs(duration).reasonForIncompletion(workflow.reasonForIncompletion())
                .failedTaskId(workflow.failedTaskId()).build();
    }

    public static WorkflowExecutionSummary from(WorkflowModel workflow) {
        long startTime = workflow.getStartTime();
        long endTime = workflow.getEndTime();
        Long durationMs = null;
        if (startTime > 0) {
            durationMs = (endTime > 0 ? endTime : System.currentTimeMillis()) - startTime;
        }
        return WorkflowExecutionSummary.builder()
                .workflowId(workflow.getWorkflowId())
                .workflowRoute(workflow.getWorkflowRoute())
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
