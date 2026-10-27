package com.helianhealth.iflow.console.model.response;

import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowDef;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class WorkflowExecutionDetail {

    private String workflowId;
    private String status;
    private String previousStatus;
    private String correlationId;
    private Map<String, Object> input;
    private Object output;
    private WorkflowDef workflowDefinition;
    private List<TaskExecutionView> tasks;
    private long startTime;
    private long endTime;
    private long updateTime;
    private String reasonForIncompletion;
    private String failedTaskId;

    public static WorkflowExecutionDetail from(WorkflowModel workflow) {
        List<TaskExecutionView> taskViews =
                workflow.getTasks() == null
                        ? List.of()
                        : workflow.getTasks().stream()
                                .sorted(Comparator.comparingInt(TaskModel::getSeq))
                                .map(TaskExecutionView::from)
                                .toList();
        return WorkflowExecutionDetail.builder()
                .workflowId(workflow.getWorkflowId())
                .status(workflow.getStatus() == null ? null : workflow.getStatus().name())
                .previousStatus(
                        workflow.getPreviousStatus() == null
                                ? null
                                : workflow.getPreviousStatus().name())
                .correlationId(workflow.getCorrelationId())
                .input(workflow.getInput())
                .output(workflow.getOutput())
                .workflowDefinition(workflow.getWorkflowDefinition())
                .tasks(taskViews)
                .startTime(workflow.getStartTime())
                .endTime(workflow.getEndTime())
                .updateTime(workflow.getUpdateTime())
                .reasonForIncompletion(workflow.getReasonForInCompletion())
                .failedTaskId(workflow.getFailedTaskId())
                .build();
    }

    WorkflowExecutionDetail(
            String workflowId,
            String status,
            String previousStatus,
            String correlationId,
            Map<String, Object> input,
            Object output,
            WorkflowDef workflowDefinition,
            List<TaskExecutionView> tasks,
            long startTime,
            long endTime,
            long updateTime,
            String reasonForIncompletion,
            String failedTaskId) {
        this.workflowId = workflowId;
        this.status = status;
        this.previousStatus = previousStatus;
        this.correlationId = correlationId;
        this.input = input;
        this.output = output;
        this.workflowDefinition = workflowDefinition;
        this.tasks = tasks;
        this.startTime = startTime;
        this.endTime = endTime;
        this.updateTime = updateTime;
        this.reasonForIncompletion = reasonForIncompletion;
        this.failedTaskId = failedTaskId;
    }

    public String getWorkflowId() {
        return this.workflowId;
    }

    public String getStatus() {
        return this.status;
    }

    public String getPreviousStatus() {
        return this.previousStatus;
    }

    public String getCorrelationId() {
        return this.correlationId;
    }

    public Map<String, Object> getInput() {
        return this.input;
    }

    public Object getOutput() {
        return this.output;
    }

    public WorkflowDef getWorkflowDefinition() {
        return this.workflowDefinition;
    }

    public List<TaskExecutionView> getTasks() {
        return this.tasks;
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

    public String getReasonForIncompletion() {
        return this.reasonForIncompletion;
    }

    public String getFailedTaskId() {
        return this.failedTaskId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setPreviousStatus(String previousStatus) {
        this.previousStatus = previousStatus;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public void setInput(Map<String, Object> input) {
        this.input = input;
    }

    public void setOutput(Object output) {
        this.output = output;
    }

    public void setWorkflowDefinition(WorkflowDef workflowDefinition) {
        this.workflowDefinition = workflowDefinition;
    }

    public void setTasks(List<TaskExecutionView> tasks) {
        this.tasks = tasks;
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

    public void setReasonForIncompletion(String reasonForIncompletion) {
        this.reasonForIncompletion = reasonForIncompletion;
    }

    public void setFailedTaskId(String failedTaskId) {
        this.failedTaskId = failedTaskId;
    }

    protected boolean canEqual(Object other) {
        return other instanceof WorkflowExecutionDetail;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof WorkflowExecutionDetail other)) {
            return false;
        }
        if (!other.canEqual(this)) {
            return false;
        }
        return Objects.equals(getWorkflowId(), other.getWorkflowId())
                && Objects.equals(getStatus(), other.getStatus())
                && Objects.equals(getPreviousStatus(), other.getPreviousStatus())
                && Objects.equals(getCorrelationId(), other.getCorrelationId())
                && Objects.equals(getInput(), other.getInput())
                && Objects.equals(getOutput(), other.getOutput())
                && Objects.equals(getWorkflowDefinition(), other.getWorkflowDefinition())
                && Objects.equals(getTasks(), other.getTasks())
                && getStartTime() == other.getStartTime()
                && getEndTime() == other.getEndTime()
                && getUpdateTime() == other.getUpdateTime()
                && Objects.equals(getReasonForIncompletion(), other.getReasonForIncompletion())
                && Objects.equals(getFailedTaskId(), other.getFailedTaskId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                getWorkflowId(),
                getStatus(),
                getPreviousStatus(),
                getCorrelationId(),
                getInput(),
                getOutput(),
                getWorkflowDefinition(),
                getTasks(),
                getStartTime(),
                getEndTime(),
                getUpdateTime(),
                getReasonForIncompletion(),
                getFailedTaskId());
    }

    @Override
    public String toString() {
        return "WorkflowExecutionDetail(workflowId=" + getWorkflowId()
                + ", status=" + getStatus()
                + ", previousStatus=" + getPreviousStatus()
                + ", correlationId=" + getCorrelationId()
                + ", input=" + getInput()
                + ", output=" + getOutput()
                + ", workflowDefinition=" + getWorkflowDefinition()
                + ", tasks=" + getTasks()
                + ", startTime=" + getStartTime()
                + ", endTime=" + getEndTime()
                + ", updateTime=" + getUpdateTime()
                + ", reasonForIncompletion=" + getReasonForIncompletion()
                + ", failedTaskId=" + getFailedTaskId() + ")";
    }

    public static class WorkflowExecutionDetailBuilder {

        private String workflowId;
        private String status;
        private String previousStatus;
        private String correlationId;
        private Map<String, Object> input;
        private Object output;
        private WorkflowDef workflowDefinition;
        private List<TaskExecutionView> tasks;
        private long startTime;
        private long endTime;
        private long updateTime;
        private String reasonForIncompletion;
        private String failedTaskId;

        WorkflowExecutionDetailBuilder() {
        }

        public WorkflowExecutionDetailBuilder workflowId(String workflowId) {
            this.workflowId = workflowId;
            return this;
        }

        public WorkflowExecutionDetailBuilder status(String status) {
            this.status = status;
            return this;
        }

        public WorkflowExecutionDetailBuilder previousStatus(String previousStatus) {
            this.previousStatus = previousStatus;
            return this;
        }

        public WorkflowExecutionDetailBuilder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public WorkflowExecutionDetailBuilder input(Map<String, Object> input) {
            this.input = input;
            return this;
        }

        public WorkflowExecutionDetailBuilder output(Object output) {
            this.output = output;
            return this;
        }

        public WorkflowExecutionDetailBuilder workflowDefinition(WorkflowDef workflowDefinition) {
            this.workflowDefinition = workflowDefinition;
            return this;
        }

        public WorkflowExecutionDetailBuilder tasks(List<TaskExecutionView> tasks) {
            this.tasks = tasks;
            return this;
        }

        public WorkflowExecutionDetailBuilder startTime(long startTime) {
            this.startTime = startTime;
            return this;
        }

        public WorkflowExecutionDetailBuilder endTime(long endTime) {
            this.endTime = endTime;
            return this;
        }

        public WorkflowExecutionDetailBuilder updateTime(long updateTime) {
            this.updateTime = updateTime;
            return this;
        }

        public WorkflowExecutionDetailBuilder reasonForIncompletion(String reasonForIncompletion) {
            this.reasonForIncompletion = reasonForIncompletion;
            return this;
        }

        public WorkflowExecutionDetailBuilder failedTaskId(String failedTaskId) {
            this.failedTaskId = failedTaskId;
            return this;
        }

        public WorkflowExecutionDetail build() {
            return new WorkflowExecutionDetail(
                    workflowId,
                    status,
                    previousStatus,
                    correlationId,
                    input,
                    output,
                    workflowDefinition,
                    tasks,
                    startTime,
                    endTime,
                    updateTime,
                    reasonForIncompletion,
                    failedTaskId);
        }

        @Override
        public String toString() {
            return "WorkflowExecutionDetail.WorkflowExecutionDetailBuilder(workflowId=" + workflowId
                    + ", status=" + status
                    + ", previousStatus=" + previousStatus
                    + ", correlationId=" + correlationId
                    + ", input=" + input
                    + ", output=" + output
                    + ", workflowDefinition=" + workflowDefinition
                    + ", tasks=" + tasks
                    + ", startTime=" + startTime
                    + ", endTime=" + endTime
                    + ", updateTime=" + updateTime
                    + ", reasonForIncompletion=" + reasonForIncompletion
                    + ", failedTaskId=" + failedTaskId + ")";
        }
    }

    public static WorkflowExecutionDetailBuilder builder() {
        return new WorkflowExecutionDetailBuilder();
    }
}
