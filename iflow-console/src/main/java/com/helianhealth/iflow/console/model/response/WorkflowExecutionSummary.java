package com.helianhealth.iflow.console.model.response;

import com.helianhealth.iflow.core.model.WorkflowModel;
import com.helianhealth.iflow.common.domain.run.ExecutionSearch;

import java.util.Objects;

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

    WorkflowExecutionSummary(
            String workflowId,
            String workflowRoute,
            String workflowName,
            int workflowVersion,
            String status,
            String correlationId,
            long startTime,
            long endTime,
            long updateTime,
            Long durationMs,
            String reasonForIncompletion,
            String failedTaskId) {
        this.workflowId = workflowId;
        this.workflowRoute = workflowRoute;
        this.workflowName = workflowName;
        this.workflowVersion = workflowVersion;
        this.status = status;
        this.correlationId = correlationId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.updateTime = updateTime;
        this.durationMs = durationMs;
        this.reasonForIncompletion = reasonForIncompletion;
        this.failedTaskId = failedTaskId;
    }

    public String getWorkflowId() {
        return this.workflowId;
    }

    public String getWorkflowRoute() {
        return this.workflowRoute;
    }

    public String getWorkflowName() {
        return this.workflowName;
    }

    public int getWorkflowVersion() {
        return this.workflowVersion;
    }

    public String getStatus() {
        return this.status;
    }

    public String getCorrelationId() {
        return this.correlationId;
    }

    public long getStartTime() {
        return this.startTime;
    }

    public long getEndTime() {
        return this.endTime;
    }

    public long getUpdateTime() {
        return this.updateTime;
    }

    public Long getDurationMs() {
        return this.durationMs;
    }

    public String getReasonForIncompletion() {
        return this.reasonForIncompletion;
    }

    public String getFailedTaskId() {
        return this.failedTaskId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    public void setWorkflowRoute(String workflowRoute) {
        this.workflowRoute = workflowRoute;
    }

    public void setWorkflowName(String workflowName) {
        this.workflowName = workflowName;
    }

    public void setWorkflowVersion(int workflowVersion) {
        this.workflowVersion = workflowVersion;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public void setUpdateTime(long updateTime) {
        this.updateTime = updateTime;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public void setReasonForIncompletion(String reasonForIncompletion) {
        this.reasonForIncompletion = reasonForIncompletion;
    }

    public void setFailedTaskId(String failedTaskId) {
        this.failedTaskId = failedTaskId;
    }

    protected boolean canEqual(Object other) {
        return other instanceof WorkflowExecutionSummary;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof WorkflowExecutionSummary other)) {
            return false;
        }
        if (!other.canEqual(this)) {
            return false;
        }
        return Objects.equals(getWorkflowId(), other.getWorkflowId())
                && Objects.equals(getWorkflowRoute(), other.getWorkflowRoute())
                && Objects.equals(getWorkflowName(), other.getWorkflowName())
                && getWorkflowVersion() == other.getWorkflowVersion()
                && Objects.equals(getStatus(), other.getStatus())
                && Objects.equals(getCorrelationId(), other.getCorrelationId())
                && getStartTime() == other.getStartTime()
                && getEndTime() == other.getEndTime()
                && getUpdateTime() == other.getUpdateTime()
                && Objects.equals(getDurationMs(), other.getDurationMs())
                && Objects.equals(getReasonForIncompletion(), other.getReasonForIncompletion())
                && Objects.equals(getFailedTaskId(), other.getFailedTaskId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                getWorkflowId(),
                getWorkflowRoute(),
                getWorkflowName(),
                getWorkflowVersion(),
                getStatus(),
                getCorrelationId(),
                getStartTime(),
                getEndTime(),
                getUpdateTime(),
                getDurationMs(),
                getReasonForIncompletion(),
                getFailedTaskId());
    }

    @Override
    public String toString() {
        return "WorkflowExecutionSummary(workflowId=" + getWorkflowId()
                + ", workflowRoute=" + getWorkflowRoute()
                + ", workflowName=" + getWorkflowName()
                + ", workflowVersion=" + getWorkflowVersion()
                + ", status=" + getStatus()
                + ", correlationId=" + getCorrelationId()
                + ", startTime=" + getStartTime()
                + ", endTime=" + getEndTime()
                + ", updateTime=" + getUpdateTime()
                + ", durationMs=" + getDurationMs()
                + ", reasonForIncompletion=" + getReasonForIncompletion()
                + ", failedTaskId=" + getFailedTaskId() + ")";
    }

    public static class WorkflowExecutionSummaryBuilder {

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

        WorkflowExecutionSummaryBuilder() {
        }

        public WorkflowExecutionSummaryBuilder workflowId(String workflowId) {
            this.workflowId = workflowId;
            return this;
        }

        public WorkflowExecutionSummaryBuilder workflowRoute(String workflowRoute) {
            this.workflowRoute = workflowRoute;
            return this;
        }

        public WorkflowExecutionSummaryBuilder workflowName(String workflowName) {
            this.workflowName = workflowName;
            return this;
        }

        public WorkflowExecutionSummaryBuilder workflowVersion(int workflowVersion) {
            this.workflowVersion = workflowVersion;
            return this;
        }

        public WorkflowExecutionSummaryBuilder status(String status) {
            this.status = status;
            return this;
        }

        public WorkflowExecutionSummaryBuilder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public WorkflowExecutionSummaryBuilder startTime(long startTime) {
            this.startTime = startTime;
            return this;
        }

        public WorkflowExecutionSummaryBuilder endTime(long endTime) {
            this.endTime = endTime;
            return this;
        }

        public WorkflowExecutionSummaryBuilder updateTime(long updateTime) {
            this.updateTime = updateTime;
            return this;
        }

        public WorkflowExecutionSummaryBuilder durationMs(Long durationMs) {
            this.durationMs = durationMs;
            return this;
        }

        public WorkflowExecutionSummaryBuilder reasonForIncompletion(String reasonForIncompletion) {
            this.reasonForIncompletion = reasonForIncompletion;
            return this;
        }

        public WorkflowExecutionSummaryBuilder failedTaskId(String failedTaskId) {
            this.failedTaskId = failedTaskId;
            return this;
        }

        public WorkflowExecutionSummary build() {
            return new WorkflowExecutionSummary(
                    workflowId,
                    workflowRoute,
                    workflowName,
                    workflowVersion,
                    status,
                    correlationId,
                    startTime,
                    endTime,
                    updateTime,
                    durationMs,
                    reasonForIncompletion,
                    failedTaskId);
        }

        @Override
        public String toString() {
            return "WorkflowExecutionSummary.WorkflowExecutionSummaryBuilder(workflowId=" + workflowId
                    + ", workflowRoute=" + workflowRoute
                    + ", workflowName=" + workflowName
                    + ", workflowVersion=" + workflowVersion
                    + ", status=" + status
                    + ", correlationId=" + correlationId
                    + ", startTime=" + startTime
                    + ", endTime=" + endTime
                    + ", updateTime=" + updateTime
                    + ", durationMs=" + durationMs
                    + ", reasonForIncompletion=" + reasonForIncompletion
                    + ", failedTaskId=" + failedTaskId + ")";
        }
    }

    public static WorkflowExecutionSummaryBuilder builder() {
        return new WorkflowExecutionSummaryBuilder();
    }
}
