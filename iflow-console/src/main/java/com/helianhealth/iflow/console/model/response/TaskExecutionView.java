package com.helianhealth.iflow.console.model.response;

import com.helianhealth.iflow.core.model.TaskModel;

import java.util.Map;
import java.util.Objects;

public class TaskExecutionView {

    private String taskId;
    private String workflowInstanceId;
    private String referenceTaskName;
    private int retryCount;
    private int seq;
    private String status;
    private String taskType;
    private String taskDefName;
    private Map<String, Object> inputData;
    private Object outputData;
    private String reasonForIncompletion;
    private long scheduledTime;
    private long startTime;
    private long endTime;
    private long updateTime;
    private String workerId;
    private String parentTaskId;
    private String keyword;

    public static TaskExecutionView from(TaskModel task) {
        return TaskExecutionView.builder()
                .taskId(task.getTaskId())
                .workflowInstanceId(task.getWorkflowInstanceId())
                .referenceTaskName(task.getReferenceTaskName())
                .retryCount(task.getRetryCount())
                .seq(task.getSeq())
                .status(task.getStatus() == null ? null : task.getStatus().name())
                .taskType(task.getTaskType())
                .taskDefName(task.getTaskDefName())
                .inputData(task.getInputData())
                .outputData(task.getOutputData())
                .reasonForIncompletion(task.getReasonForInCompletion())
                .scheduledTime(task.getScheduledTime())
                .startTime(task.getStartTime())
                .endTime(task.getEndTime())
                .updateTime(task.getUpdateTime())
                .parentTaskId(task.getParentTaskId())
                .build();
    }

    TaskExecutionView(
            String taskId,
            String workflowInstanceId,
            String referenceTaskName,
            int retryCount,
            int seq,
            String status,
            String taskType,
            String taskDefName,
            Map<String, Object> inputData,
            Object outputData,
            String reasonForIncompletion,
            long scheduledTime,
            long startTime,
            long endTime,
            long updateTime,
            String workerId,
            String parentTaskId,
            String keyword) {
        this.taskId = taskId;
        this.workflowInstanceId = workflowInstanceId;
        this.referenceTaskName = referenceTaskName;
        this.retryCount = retryCount;
        this.seq = seq;
        this.status = status;
        this.taskType = taskType;
        this.taskDefName = taskDefName;
        this.inputData = inputData;
        this.outputData = outputData;
        this.reasonForIncompletion = reasonForIncompletion;
        this.scheduledTime = scheduledTime;
        this.startTime = startTime;
        this.endTime = endTime;
        this.updateTime = updateTime;
        this.workerId = workerId;
        this.parentTaskId = parentTaskId;
        this.keyword = keyword;
    }

    public String getTaskId() {
        return this.taskId;
    }

    public String getWorkflowInstanceId() {
        return this.workflowInstanceId;
    }

    public String getReferenceTaskName() {
        return this.referenceTaskName;
    }

    public int getRetryCount() {
        return this.retryCount;
    }

    public int getSeq() {
        return this.seq;
    }

    public String getStatus() {
        return this.status;
    }

    public String getTaskType() {
        return this.taskType;
    }

    public String getTaskDefName() {
        return this.taskDefName;
    }

    public Map<String, Object> getInputData() {
        return this.inputData;
    }

    public Object getOutputData() {
        return this.outputData;
    }

    public String getReasonForIncompletion() {
        return this.reasonForIncompletion;
    }

    public long getScheduledTime() {
        return this.scheduledTime;
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

    public String getWorkerId() {
        return this.workerId;
    }

    public String getParentTaskId() {
        return this.parentTaskId;
    }

    public String getKeyword() {
        return this.keyword;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public void setWorkflowInstanceId(String workflowInstanceId) {
        this.workflowInstanceId = workflowInstanceId;
    }

    public void setReferenceTaskName(String referenceTaskName) {
        this.referenceTaskName = referenceTaskName;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public void setSeq(int seq) {
        this.seq = seq;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public void setTaskDefName(String taskDefName) {
        this.taskDefName = taskDefName;
    }

    public void setInputData(Map<String, Object> inputData) {
        this.inputData = inputData;
    }

    public void setOutputData(Object outputData) {
        this.outputData = outputData;
    }

    public void setReasonForIncompletion(String reasonForIncompletion) {
        this.reasonForIncompletion = reasonForIncompletion;
    }

    public void setScheduledTime(long scheduledTime) {
        this.scheduledTime = scheduledTime;
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

    public void setWorkerId(String workerId) {
        this.workerId = workerId;
    }

    public void setParentTaskId(String parentTaskId) {
        this.parentTaskId = parentTaskId;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    protected boolean canEqual(Object other) {
        return other instanceof TaskExecutionView;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof TaskExecutionView other)) {
            return false;
        }
        if (!other.canEqual(this)) {
            return false;
        }
        return Objects.equals(getTaskId(), other.getTaskId())
                && Objects.equals(getWorkflowInstanceId(), other.getWorkflowInstanceId())
                && Objects.equals(getReferenceTaskName(), other.getReferenceTaskName())
                && getRetryCount() == other.getRetryCount()
                && getSeq() == other.getSeq()
                && Objects.equals(getStatus(), other.getStatus())
                && Objects.equals(getTaskType(), other.getTaskType())
                && Objects.equals(getTaskDefName(), other.getTaskDefName())
                && Objects.equals(getInputData(), other.getInputData())
                && Objects.equals(getOutputData(), other.getOutputData())
                && Objects.equals(getReasonForIncompletion(), other.getReasonForIncompletion())
                && getScheduledTime() == other.getScheduledTime()
                && getStartTime() == other.getStartTime()
                && getEndTime() == other.getEndTime()
                && getUpdateTime() == other.getUpdateTime()
                && Objects.equals(getWorkerId(), other.getWorkerId())
                && Objects.equals(getParentTaskId(), other.getParentTaskId())
                && Objects.equals(getKeyword(), other.getKeyword());
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                getTaskId(),
                getWorkflowInstanceId(),
                getReferenceTaskName(),
                getRetryCount(),
                getSeq(),
                getStatus(),
                getTaskType(),
                getTaskDefName(),
                getInputData(),
                getOutputData(),
                getReasonForIncompletion(),
                getScheduledTime(),
                getStartTime(),
                getEndTime(),
                getUpdateTime(),
                getWorkerId(),
                getParentTaskId(),
                getKeyword());
    }

    @Override
    public String toString() {
        return "TaskExecutionView(taskId=" + getTaskId()
                + ", workflowInstanceId=" + getWorkflowInstanceId()
                + ", referenceTaskName=" + getReferenceTaskName()
                + ", retryCount=" + getRetryCount()
                + ", seq=" + getSeq()
                + ", status=" + getStatus()
                + ", taskType=" + getTaskType()
                + ", taskDefName=" + getTaskDefName()
                + ", inputData=" + getInputData()
                + ", outputData=" + getOutputData()
                + ", reasonForIncompletion=" + getReasonForIncompletion()
                + ", scheduledTime=" + getScheduledTime()
                + ", startTime=" + getStartTime()
                + ", endTime=" + getEndTime()
                + ", updateTime=" + getUpdateTime()
                + ", workerId=" + getWorkerId()
                + ", parentTaskId=" + getParentTaskId()
                + ", keyword=" + getKeyword() + ")";
    }

    public static class TaskExecutionViewBuilder {

        private String taskId;
        private String workflowInstanceId;
        private String referenceTaskName;
        private int retryCount;
        private int seq;
        private String status;
        private String taskType;
        private String taskDefName;
        private Map<String, Object> inputData;
        private Object outputData;
        private String reasonForIncompletion;
        private long scheduledTime;
        private long startTime;
        private long endTime;
        private long updateTime;
        private String workerId;
        private String parentTaskId;
        private String keyword;

        TaskExecutionViewBuilder() {
        }

        public TaskExecutionViewBuilder taskId(String taskId) {
            this.taskId = taskId;
            return this;
        }

        public TaskExecutionViewBuilder workflowInstanceId(String workflowInstanceId) {
            this.workflowInstanceId = workflowInstanceId;
            return this;
        }

        public TaskExecutionViewBuilder referenceTaskName(String referenceTaskName) {
            this.referenceTaskName = referenceTaskName;
            return this;
        }

        public TaskExecutionViewBuilder retryCount(int retryCount) {
            this.retryCount = retryCount;
            return this;
        }

        public TaskExecutionViewBuilder seq(int seq) {
            this.seq = seq;
            return this;
        }

        public TaskExecutionViewBuilder status(String status) {
            this.status = status;
            return this;
        }

        public TaskExecutionViewBuilder taskType(String taskType) {
            this.taskType = taskType;
            return this;
        }

        public TaskExecutionViewBuilder taskDefName(String taskDefName) {
            this.taskDefName = taskDefName;
            return this;
        }

        public TaskExecutionViewBuilder inputData(Map<String, Object> inputData) {
            this.inputData = inputData;
            return this;
        }

        public TaskExecutionViewBuilder outputData(Object outputData) {
            this.outputData = outputData;
            return this;
        }

        public TaskExecutionViewBuilder reasonForIncompletion(String reasonForIncompletion) {
            this.reasonForIncompletion = reasonForIncompletion;
            return this;
        }

        public TaskExecutionViewBuilder scheduledTime(long scheduledTime) {
            this.scheduledTime = scheduledTime;
            return this;
        }

        public TaskExecutionViewBuilder startTime(long startTime) {
            this.startTime = startTime;
            return this;
        }

        public TaskExecutionViewBuilder endTime(long endTime) {
            this.endTime = endTime;
            return this;
        }

        public TaskExecutionViewBuilder updateTime(long updateTime) {
            this.updateTime = updateTime;
            return this;
        }

        public TaskExecutionViewBuilder workerId(String workerId) {
            this.workerId = workerId;
            return this;
        }

        public TaskExecutionViewBuilder parentTaskId(String parentTaskId) {
            this.parentTaskId = parentTaskId;
            return this;
        }

        public TaskExecutionViewBuilder keyword(String keyword) {
            this.keyword = keyword;
            return this;
        }

        public TaskExecutionView build() {
            return new TaskExecutionView(
                    taskId,
                    workflowInstanceId,
                    referenceTaskName,
                    retryCount,
                    seq,
                    status,
                    taskType,
                    taskDefName,
                    inputData,
                    outputData,
                    reasonForIncompletion,
                    scheduledTime,
                    startTime,
                    endTime,
                    updateTime,
                    workerId,
                    parentTaskId,
                    keyword);
        }

        @Override
        public String toString() {
            return "TaskExecutionView.TaskExecutionViewBuilder(taskId=" + taskId
                    + ", workflowInstanceId=" + workflowInstanceId
                    + ", referenceTaskName=" + referenceTaskName
                    + ", retryCount=" + retryCount
                    + ", seq=" + seq
                    + ", status=" + status
                    + ", taskType=" + taskType
                    + ", taskDefName=" + taskDefName
                    + ", inputData=" + inputData
                    + ", outputData=" + outputData
                    + ", reasonForIncompletion=" + reasonForIncompletion
                    + ", scheduledTime=" + scheduledTime
                    + ", startTime=" + startTime
                    + ", endTime=" + endTime
                    + ", updateTime=" + updateTime
                    + ", workerId=" + workerId
                    + ", parentTaskId=" + parentTaskId
                    + ", keyword=" + keyword + ")";
        }
    }

    public static TaskExecutionViewBuilder builder() {
        return new TaskExecutionViewBuilder();
    }
}
