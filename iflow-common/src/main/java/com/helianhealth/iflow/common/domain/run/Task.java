package com.helianhealth.iflow.common.domain.run;

import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowTask;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 任务运行时视图。
 */
public class Task {

    public enum Status {
        /** 已登记并等待首次执行，包括在线程池中排队。 */
        SCHEDULED(false, true, true),
        /** 已实际开始执行，或正在等待控制任务的完成条件。 */
        IN_PROGRESS(false, true, true),
        CANCELED(true, false, false),
        FAILED(true, false, true),
        FAILED_WITH_TERMINAL_ERROR(true, false, false),
        COMPLETED(true, true, true),
        COMPLETED_WITH_ERRORS(true, true, true),
        TIMED_OUT(true, false, true),
        SKIPPED(true, true, false);

        private final boolean terminal;
        private final boolean successful;
        private final boolean retriable;

        Status(boolean terminal, boolean successful, boolean retriable) {
            this.terminal = terminal;
            this.successful = successful;
            this.retriable = retriable;
        }

        public boolean isTerminal() {
            return terminal;
        }

        public boolean isSuccessful() {
            return successful;
        }

        public boolean isRetriable() {
            return retriable;
        }
    }

    private String taskType;

    private Status status;

    private Status previousStatus;

    private Map<String, Object> inputData = new HashMap<>();

    private Object outputData = new HashMap<>();

    private String referenceTaskName;

    private int retryCount;

    private int seq;

    private String correlationId;

    private String taskDefName;

    private long scheduledTime;

    private long firstScheduledTime;

    private long startTime;

    private long endTime;

    private long updateTime;

    private String reasonForIncompletion;

    private String workerId;

    private String taskId;

    private String workflowInstanceId;

    private String workflowType;

    private String retriedTaskId;

    private boolean retried;

    private boolean executed;

    private WorkflowTask workflowTask;

    private String parentTaskId;

    private String forkTaskId;

    private String keyword;

    private int iteration;

    public String getTaskDefName() {
        if (taskDefName == null || taskDefName.isEmpty()) {
            return taskType;
        }
        return taskDefName;
    }

    public String getTaskType() {
        return this.taskType;
    }

    public Status getStatus() {
        return this.status;
    }

    public Status getPreviousStatus() {
        return this.previousStatus;
    }

    public Map<String, Object> getInputData() {
        return this.inputData;
    }

    public Object getOutputData() {
        return this.outputData;
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

    public String getCorrelationId() {
        return this.correlationId;
    }

    public long getScheduledTime() {
        return this.scheduledTime;
    }

    public long getFirstScheduledTime() {
        return this.firstScheduledTime;
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

    public String getWorkerId() {
        return this.workerId;
    }

    public String getTaskId() {
        return this.taskId;
    }

    public String getWorkflowInstanceId() {
        return this.workflowInstanceId;
    }

    public String getWorkflowType() {
        return this.workflowType;
    }

    public String getRetriedTaskId() {
        return this.retriedTaskId;
    }

    public boolean isRetried() {
        return this.retried;
    }

    public boolean isExecuted() {
        return this.executed;
    }

    public WorkflowTask getWorkflowTask() {
        return this.workflowTask;
    }

    public String getParentTaskId() {
        return this.parentTaskId;
    }

    public String getForkTaskId() {
        return this.forkTaskId;
    }

    public String getKeyword() {
        return this.keyword;
    }

    public int getIteration() {
        return this.iteration;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public void setPreviousStatus(Status previousStatus) {
        this.previousStatus = previousStatus;
    }

    public void setInputData(Map<String, Object> inputData) {
        this.inputData = inputData;
    }

    public void setOutputData(Object outputData) {
        this.outputData = outputData;
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

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public void setTaskDefName(String taskDefName) {
        this.taskDefName = taskDefName;
    }

    public void setScheduledTime(long scheduledTime) {
        this.scheduledTime = scheduledTime;
    }

    public void setFirstScheduledTime(long firstScheduledTime) {
        this.firstScheduledTime = firstScheduledTime;
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

    public void setWorkerId(String workerId) {
        this.workerId = workerId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public void setWorkflowInstanceId(String workflowInstanceId) {
        this.workflowInstanceId = workflowInstanceId;
    }

    public void setWorkflowType(String workflowType) {
        this.workflowType = workflowType;
    }

    public void setRetriedTaskId(String retriedTaskId) {
        this.retriedTaskId = retriedTaskId;
    }

    public void setRetried(boolean retried) {
        this.retried = retried;
    }

    public void setExecuted(boolean executed) {
        this.executed = executed;
    }

    public void setWorkflowTask(WorkflowTask workflowTask) {
        this.workflowTask = workflowTask;
    }

    public void setParentTaskId(String parentTaskId) {
        this.parentTaskId = parentTaskId;
    }

    public void setForkTaskId(String forkTaskId) {
        this.forkTaskId = forkTaskId;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public void setIteration(int iteration) {
        this.iteration = iteration;
    }

    protected boolean canEqual(Object other) {
        return other instanceof Task;
    }

    public Task() {
    }

    public Task(
            String taskType,
            Status status,
            Status previousStatus,
            Map<String, Object> inputData,
            Object outputData,
            String referenceTaskName,
            int retryCount,
            int seq,
            String correlationId,
            String taskDefName,
            long scheduledTime,
            long firstScheduledTime,
            long startTime,
            long endTime,
            long updateTime,
            String reasonForIncompletion,
            String workerId,
            String taskId,
            String workflowInstanceId,
            String workflowType,
            String retriedTaskId,
            boolean retried,
            boolean executed,
            WorkflowTask workflowTask,
            String parentTaskId,
            String forkTaskId,
            String keyword,
            int iteration) {
        this.taskType = taskType;
        this.status = status;
        this.previousStatus = previousStatus;
        this.inputData = inputData;
        this.outputData = outputData;
        this.referenceTaskName = referenceTaskName;
        this.retryCount = retryCount;
        this.seq = seq;
        this.correlationId = correlationId;
        this.taskDefName = taskDefName;
        this.scheduledTime = scheduledTime;
        this.firstScheduledTime = firstScheduledTime;
        this.startTime = startTime;
        this.endTime = endTime;
        this.updateTime = updateTime;
        this.reasonForIncompletion = reasonForIncompletion;
        this.workerId = workerId;
        this.taskId = taskId;
        this.workflowInstanceId = workflowInstanceId;
        this.workflowType = workflowType;
        this.retriedTaskId = retriedTaskId;
        this.retried = retried;
        this.executed = executed;
        this.workflowTask = workflowTask;
        this.parentTaskId = parentTaskId;
        this.forkTaskId = forkTaskId;
        this.keyword = keyword;
        this.iteration = iteration;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof Task other)) {
            return false;
        }
        if (!other.canEqual(this)) {
            return false;
        }
        return Objects.equals(getTaskType(), other.getTaskType())
                && Objects.equals(getStatus(), other.getStatus())
                && Objects.equals(getPreviousStatus(), other.getPreviousStatus())
                && Objects.equals(getInputData(), other.getInputData())
                && Objects.equals(getOutputData(), other.getOutputData())
                && Objects.equals(getReferenceTaskName(), other.getReferenceTaskName())
                && getRetryCount() == other.getRetryCount()
                && getSeq() == other.getSeq()
                && Objects.equals(getCorrelationId(), other.getCorrelationId())
                && Objects.equals(getTaskDefName(), other.getTaskDefName())
                && getScheduledTime() == other.getScheduledTime()
                && getFirstScheduledTime() == other.getFirstScheduledTime()
                && getStartTime() == other.getStartTime()
                && getEndTime() == other.getEndTime()
                && getUpdateTime() == other.getUpdateTime()
                && Objects.equals(getReasonForIncompletion(), other.getReasonForIncompletion())
                && Objects.equals(getWorkerId(), other.getWorkerId())
                && Objects.equals(getTaskId(), other.getTaskId())
                && Objects.equals(getWorkflowInstanceId(), other.getWorkflowInstanceId())
                && Objects.equals(getWorkflowType(), other.getWorkflowType())
                && Objects.equals(getRetriedTaskId(), other.getRetriedTaskId())
                && isRetried() == other.isRetried()
                && isExecuted() == other.isExecuted()
                && Objects.equals(getWorkflowTask(), other.getWorkflowTask())
                && Objects.equals(getParentTaskId(), other.getParentTaskId())
                && Objects.equals(getForkTaskId(), other.getForkTaskId())
                && Objects.equals(getKeyword(), other.getKeyword())
                && getIteration() == other.getIteration();
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                getTaskType(),
                getStatus(),
                getPreviousStatus(),
                getInputData(),
                getOutputData(),
                getReferenceTaskName(),
                getRetryCount(),
                getSeq(),
                getCorrelationId(),
                getTaskDefName(),
                getScheduledTime(),
                getFirstScheduledTime(),
                getStartTime(),
                getEndTime(),
                getUpdateTime(),
                getReasonForIncompletion(),
                getWorkerId(),
                getTaskId(),
                getWorkflowInstanceId(),
                getWorkflowType(),
                getRetriedTaskId(),
                isRetried(),
                isExecuted(),
                getWorkflowTask(),
                getParentTaskId(),
                getForkTaskId(),
                getKeyword(),
                getIteration());
    }

    @Override
    public String toString() {
        return "Task(taskType=" + getTaskType()
                + ", status=" + getStatus()
                + ", previousStatus=" + getPreviousStatus()
                + ", inputData=" + getInputData()
                + ", outputData=" + getOutputData()
                + ", referenceTaskName=" + getReferenceTaskName()
                + ", retryCount=" + getRetryCount()
                + ", seq=" + getSeq()
                + ", correlationId=" + getCorrelationId()
                + ", taskDefName=" + getTaskDefName()
                + ", scheduledTime=" + getScheduledTime()
                + ", firstScheduledTime=" + getFirstScheduledTime()
                + ", startTime=" + getStartTime()
                + ", endTime=" + getEndTime()
                + ", updateTime=" + getUpdateTime()
                + ", reasonForIncompletion=" + getReasonForIncompletion()
                + ", workerId=" + getWorkerId()
                + ", taskId=" + getTaskId()
                + ", workflowInstanceId=" + getWorkflowInstanceId()
                + ", workflowType=" + getWorkflowType()
                + ", retriedTaskId=" + getRetriedTaskId()
                + ", retried=" + isRetried()
                + ", executed=" + isExecuted()
                + ", workflowTask=" + getWorkflowTask()
                + ", parentTaskId=" + getParentTaskId()
                + ", forkTaskId=" + getForkTaskId()
                + ", keyword=" + getKeyword()
                + ", iteration=" + getIteration() + ")";
    }

    public static class TaskBuilder {

        private String taskType;
        private Status status;
        private Status previousStatus;
        private Map<String, Object> inputData;
        private boolean inputDataSet;
        private Object outputData;
        private boolean outputDataSet;
        private String referenceTaskName;
        private int retryCount;
        private int seq;
        private String correlationId;
        private String taskDefName;
        private long scheduledTime;
        private long firstScheduledTime;
        private long startTime;
        private long endTime;
        private long updateTime;
        private String reasonForIncompletion;
        private String workerId;
        private String taskId;
        private String workflowInstanceId;
        private String workflowType;
        private String retriedTaskId;
        private boolean retried;
        private boolean executed;
        private WorkflowTask workflowTask;
        private String parentTaskId;
        private String forkTaskId;
        private String keyword;
        private int iteration;

        TaskBuilder() {
        }

        public TaskBuilder taskType(String taskType) {
            this.taskType = taskType;
            return this;
        }

        public TaskBuilder status(Status status) {
            this.status = status;
            return this;
        }

        public TaskBuilder previousStatus(Status previousStatus) {
            this.previousStatus = previousStatus;
            return this;
        }

        public TaskBuilder inputData(Map<String, Object> inputData) {
            this.inputData = inputData;
            this.inputDataSet = true;
            return this;
        }

        public TaskBuilder outputData(Object outputData) {
            this.outputData = outputData;
            this.outputDataSet = true;
            return this;
        }

        public TaskBuilder referenceTaskName(String referenceTaskName) {
            this.referenceTaskName = referenceTaskName;
            return this;
        }

        public TaskBuilder retryCount(int retryCount) {
            this.retryCount = retryCount;
            return this;
        }

        public TaskBuilder seq(int seq) {
            this.seq = seq;
            return this;
        }

        public TaskBuilder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public TaskBuilder taskDefName(String taskDefName) {
            this.taskDefName = taskDefName;
            return this;
        }

        public TaskBuilder scheduledTime(long scheduledTime) {
            this.scheduledTime = scheduledTime;
            return this;
        }

        public TaskBuilder firstScheduledTime(long firstScheduledTime) {
            this.firstScheduledTime = firstScheduledTime;
            return this;
        }

        public TaskBuilder startTime(long startTime) {
            this.startTime = startTime;
            return this;
        }

        public TaskBuilder endTime(long endTime) {
            this.endTime = endTime;
            return this;
        }

        public TaskBuilder updateTime(long updateTime) {
            this.updateTime = updateTime;
            return this;
        }

        public TaskBuilder reasonForIncompletion(String reasonForIncompletion) {
            this.reasonForIncompletion = reasonForIncompletion;
            return this;
        }

        public TaskBuilder workerId(String workerId) {
            this.workerId = workerId;
            return this;
        }

        public TaskBuilder taskId(String taskId) {
            this.taskId = taskId;
            return this;
        }

        public TaskBuilder workflowInstanceId(String workflowInstanceId) {
            this.workflowInstanceId = workflowInstanceId;
            return this;
        }

        public TaskBuilder workflowType(String workflowType) {
            this.workflowType = workflowType;
            return this;
        }

        public TaskBuilder retriedTaskId(String retriedTaskId) {
            this.retriedTaskId = retriedTaskId;
            return this;
        }

        public TaskBuilder retried(boolean retried) {
            this.retried = retried;
            return this;
        }

        public TaskBuilder executed(boolean executed) {
            this.executed = executed;
            return this;
        }

        public TaskBuilder workflowTask(WorkflowTask workflowTask) {
            this.workflowTask = workflowTask;
            return this;
        }

        public TaskBuilder parentTaskId(String parentTaskId) {
            this.parentTaskId = parentTaskId;
            return this;
        }

        public TaskBuilder forkTaskId(String forkTaskId) {
            this.forkTaskId = forkTaskId;
            return this;
        }

        public TaskBuilder keyword(String keyword) {
            this.keyword = keyword;
            return this;
        }

        public TaskBuilder iteration(int iteration) {
            this.iteration = iteration;
            return this;
        }

        public Task build() {
            return new Task(
                    taskType,
                    status,
                    previousStatus,
                    inputDataSet ? inputData : new HashMap<>(),
                    outputDataSet ? outputData : new HashMap<>(),
                    referenceTaskName,
                    retryCount,
                    seq,
                    correlationId,
                    taskDefName,
                    scheduledTime,
                    firstScheduledTime,
                    startTime,
                    endTime,
                    updateTime,
                    reasonForIncompletion,
                    workerId,
                    taskId,
                    workflowInstanceId,
                    workflowType,
                    retriedTaskId,
                    retried,
                    executed,
                    workflowTask,
                    parentTaskId,
                    forkTaskId,
                    keyword,
                    iteration);
        }

        @Override
        public String toString() {
            return "Task.TaskBuilder(taskType=" + taskType
                    + ", status=" + status
                    + ", previousStatus=" + previousStatus
                    + ", inputData=" + inputData
                    + ", outputData=" + outputData
                    + ", referenceTaskName=" + referenceTaskName
                    + ", retryCount=" + retryCount
                    + ", seq=" + seq
                    + ", correlationId=" + correlationId
                    + ", taskDefName=" + taskDefName
                    + ", scheduledTime=" + scheduledTime
                    + ", firstScheduledTime=" + firstScheduledTime
                    + ", startTime=" + startTime
                    + ", endTime=" + endTime
                    + ", updateTime=" + updateTime
                    + ", reasonForIncompletion=" + reasonForIncompletion
                    + ", workerId=" + workerId
                    + ", taskId=" + taskId
                    + ", workflowInstanceId=" + workflowInstanceId
                    + ", workflowType=" + workflowType
                    + ", retriedTaskId=" + retriedTaskId
                    + ", retried=" + retried
                    + ", executed=" + executed
                    + ", workflowTask=" + workflowTask
                    + ", parentTaskId=" + parentTaskId
                    + ", forkTaskId=" + forkTaskId
                    + ", keyword=" + keyword
                    + ", iteration=" + iteration + ")";
        }
    }

    public static TaskBuilder builder() {
        return new TaskBuilder();
    }
}
