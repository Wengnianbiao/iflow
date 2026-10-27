package com.helianhealth.iflow.common.domain.run;

import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowDef;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Objects;

/**
 * 工作流运行时视图
 *
 * <p>用于 API 响应、快照 {@code rawJSON}、持久化 JSON —— 与引擎内可变 {@code WorkflowModel} 分离。
 * 不含 Index / 队列 / 外部大载荷存储字段。
 */
public class Workflow {

    public enum WorkflowStatus {
        RUNNING(false, false),
        COMPLETED(true, true),
        FAILED(true, false),
        TIMED_OUT(true, false),
        /** 人工或显式终止，属于未成功的终态。 */
        TERMINATED(true, false),
        INTERRUPTED(true, false);

        private final boolean terminal;
        private final boolean successful;

        WorkflowStatus(boolean terminal, boolean successful) {
            this.terminal = terminal;
            this.successful = successful;
        }

        public boolean isTerminal() {
            return terminal;
        }

        public boolean isSuccessful() {
            return successful;
        }
    }

    private WorkflowStatus status = WorkflowStatus.RUNNING;

    private WorkflowStatus previousStatus;

    private long endTime;

    private String workflowId;

    private List<Task> tasks = new LinkedList<>();

    private Map<String, Object> input = new HashMap<>();

    private Object output = new HashMap<>();

    private String correlationId;

    private String reasonForIncompletion;

    private Set<String> failedReferenceTaskNames = new HashSet<>();

    private Set<String> failedTaskNames = new HashSet<>();

    private WorkflowDef workflowDefinition;

    private int priority = 0;

    private long lastRetriedTime;

    private String failedTaskId;

    /** 实例创建时间 */
    private long createTime;

    private long updateTime;

    public long getStartTime() {
        return createTime;
    }

    public void setStartTime(long startTime) {
        this.createTime = startTime;
    }

    public String getWorkflowRoute() {
        return workflowDefinition == null ? null : workflowDefinition.getRoute();
    }

    public String getWorkflowName() {
        return workflowDefinition == null ? null : workflowDefinition.getName();
    }

    public int getWorkflowVersion() {
        return workflowDefinition == null ? 0 : workflowDefinition.getVersion();
    }

    public Task getTaskByRefName(String refName) {
        if (refName == null) {
            throw new IllegalArgumentException("refName cannot be null");
        }
        Task last = null;
        for (Task task : tasks) {
            if (refName.equals(task.getReferenceTaskName())) {
                last = task;
            }
        }
        return last;
    }

    public String toShortString() {
        return getWorkflowRoute() + "." + getWorkflowVersion() + "/" + workflowId;
    }

    public WorkflowStatus getStatus() {
        return this.status;
    }

    public WorkflowStatus getPreviousStatus() {
        return this.previousStatus;
    }

    public long getEndTime() {
        return this.endTime;
    }

    public String getWorkflowId() {
        return this.workflowId;
    }

    public List<Task> getTasks() {
        return this.tasks;
    }

    public Map<String, Object> getInput() {
        return this.input;
    }

    public Object getOutput() {
        return this.output;
    }

    public String getCorrelationId() {
        return this.correlationId;
    }

    public String getReasonForIncompletion() {
        return this.reasonForIncompletion;
    }

    public Set<String> getFailedReferenceTaskNames() {
        return this.failedReferenceTaskNames;
    }

    public Set<String> getFailedTaskNames() {
        return this.failedTaskNames;
    }

    public WorkflowDef getWorkflowDefinition() {
        return this.workflowDefinition;
    }

    public int getPriority() {
        return this.priority;
    }

    public long getLastRetriedTime() {
        return this.lastRetriedTime;
    }

    public String getFailedTaskId() {
        return this.failedTaskId;
    }

    public long getCreateTime() {
        return this.createTime;
    }

    public long getUpdateTime() {
        return this.updateTime;
    }

    public void setStatus(WorkflowStatus status) {
        this.status = status;
    }

    public void setPreviousStatus(WorkflowStatus previousStatus) {
        this.previousStatus = previousStatus;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    public void setTasks(List<Task> tasks) {
        this.tasks = tasks;
    }

    public void setInput(Map<String, Object> input) {
        this.input = input;
    }

    public void setOutput(Object output) {
        this.output = output;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public void setReasonForIncompletion(String reasonForIncompletion) {
        this.reasonForIncompletion = reasonForIncompletion;
    }

    public void setFailedReferenceTaskNames(Set<String> failedReferenceTaskNames) {
        this.failedReferenceTaskNames = failedReferenceTaskNames;
    }

    public void setFailedTaskNames(Set<String> failedTaskNames) {
        this.failedTaskNames = failedTaskNames;
    }

    public void setWorkflowDefinition(WorkflowDef workflowDefinition) {
        this.workflowDefinition = workflowDefinition;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public void setLastRetriedTime(long lastRetriedTime) {
        this.lastRetriedTime = lastRetriedTime;
    }

    public void setFailedTaskId(String failedTaskId) {
        this.failedTaskId = failedTaskId;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    public void setUpdateTime(long updateTime) {
        this.updateTime = updateTime;
    }

    protected boolean canEqual(Object other) {
        return other instanceof Workflow;
    }

    public Workflow() {
    }

    public Workflow(
            WorkflowStatus status,
            WorkflowStatus previousStatus,
            long endTime,
            String workflowId,
            List<Task> tasks,
            Map<String, Object> input,
            Object output,
            String correlationId,
            String reasonForIncompletion,
            Set<String> failedReferenceTaskNames,
            Set<String> failedTaskNames,
            WorkflowDef workflowDefinition,
            int priority,
            long lastRetriedTime,
            String failedTaskId,
            long createTime,
            long updateTime) {
        this.status = status;
        this.previousStatus = previousStatus;
        this.endTime = endTime;
        this.workflowId = workflowId;
        this.tasks = tasks;
        this.input = input;
        this.output = output;
        this.correlationId = correlationId;
        this.reasonForIncompletion = reasonForIncompletion;
        this.failedReferenceTaskNames = failedReferenceTaskNames;
        this.failedTaskNames = failedTaskNames;
        this.workflowDefinition = workflowDefinition;
        this.priority = priority;
        this.lastRetriedTime = lastRetriedTime;
        this.failedTaskId = failedTaskId;
        this.createTime = createTime;
        this.updateTime = updateTime;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof Workflow other)) {
            return false;
        }
        if (!other.canEqual(this)) {
            return false;
        }
        return Objects.equals(getStatus(), other.getStatus())
                && Objects.equals(getPreviousStatus(), other.getPreviousStatus())
                && getEndTime() == other.getEndTime()
                && Objects.equals(getWorkflowId(), other.getWorkflowId())
                && Objects.equals(getTasks(), other.getTasks())
                && Objects.equals(getInput(), other.getInput())
                && Objects.equals(getOutput(), other.getOutput())
                && Objects.equals(getCorrelationId(), other.getCorrelationId())
                && Objects.equals(getReasonForIncompletion(), other.getReasonForIncompletion())
                && Objects.equals(getFailedReferenceTaskNames(), other.getFailedReferenceTaskNames())
                && Objects.equals(getFailedTaskNames(), other.getFailedTaskNames())
                && Objects.equals(getWorkflowDefinition(), other.getWorkflowDefinition())
                && getPriority() == other.getPriority()
                && getLastRetriedTime() == other.getLastRetriedTime()
                && Objects.equals(getFailedTaskId(), other.getFailedTaskId())
                && getCreateTime() == other.getCreateTime()
                && getUpdateTime() == other.getUpdateTime();
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                getStatus(),
                getPreviousStatus(),
                getEndTime(),
                getWorkflowId(),
                getTasks(),
                getInput(),
                getOutput(),
                getCorrelationId(),
                getReasonForIncompletion(),
                getFailedReferenceTaskNames(),
                getFailedTaskNames(),
                getWorkflowDefinition(),
                getPriority(),
                getLastRetriedTime(),
                getFailedTaskId(),
                getCreateTime(),
                getUpdateTime());
    }

    @Override
    public String toString() {
        return "Workflow(status=" + getStatus()
                + ", previousStatus=" + getPreviousStatus()
                + ", endTime=" + getEndTime()
                + ", workflowId=" + getWorkflowId()
                + ", tasks=" + getTasks()
                + ", input=" + getInput()
                + ", output=" + getOutput()
                + ", correlationId=" + getCorrelationId()
                + ", reasonForIncompletion=" + getReasonForIncompletion()
                + ", failedReferenceTaskNames=" + getFailedReferenceTaskNames()
                + ", failedTaskNames=" + getFailedTaskNames()
                + ", workflowDefinition=" + getWorkflowDefinition()
                + ", priority=" + getPriority()
                + ", lastRetriedTime=" + getLastRetriedTime()
                + ", failedTaskId=" + getFailedTaskId()
                + ", createTime=" + getCreateTime()
                + ", updateTime=" + getUpdateTime() + ")";
    }

    public static class WorkflowBuilder {

        private WorkflowStatus status = WorkflowStatus.RUNNING;
        private WorkflowStatus previousStatus;
        private long endTime;
        private String workflowId;
        private List<Task> tasks;
        private boolean tasksSet;
        private Map<String, Object> input;
        private boolean inputSet;
        private Object output;
        private boolean outputSet;
        private String correlationId;
        private String reasonForIncompletion;
        private Set<String> failedReferenceTaskNames;
        private boolean failedReferenceTaskNamesSet;
        private Set<String> failedTaskNames;
        private boolean failedTaskNamesSet;
        private WorkflowDef workflowDefinition;
        private int priority = 0;
        private long lastRetriedTime;
        private String failedTaskId;
        private long createTime;
        private long updateTime;

        WorkflowBuilder() {
        }

        public WorkflowBuilder status(WorkflowStatus status) {
            this.status = status;
            return this;
        }

        public WorkflowBuilder previousStatus(WorkflowStatus previousStatus) {
            this.previousStatus = previousStatus;
            return this;
        }

        public WorkflowBuilder endTime(long endTime) {
            this.endTime = endTime;
            return this;
        }

        public WorkflowBuilder workflowId(String workflowId) {
            this.workflowId = workflowId;
            return this;
        }

        public WorkflowBuilder tasks(List<Task> tasks) {
            this.tasks = tasks;
            this.tasksSet = true;
            return this;
        }

        public WorkflowBuilder input(Map<String, Object> input) {
            this.input = input;
            this.inputSet = true;
            return this;
        }

        public WorkflowBuilder output(Object output) {
            this.output = output;
            this.outputSet = true;
            return this;
        }

        public WorkflowBuilder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public WorkflowBuilder reasonForIncompletion(String reasonForIncompletion) {
            this.reasonForIncompletion = reasonForIncompletion;
            return this;
        }

        public WorkflowBuilder failedReferenceTaskNames(Set<String> failedReferenceTaskNames) {
            this.failedReferenceTaskNames = failedReferenceTaskNames;
            this.failedReferenceTaskNamesSet = true;
            return this;
        }

        public WorkflowBuilder failedTaskNames(Set<String> failedTaskNames) {
            this.failedTaskNames = failedTaskNames;
            this.failedTaskNamesSet = true;
            return this;
        }

        public WorkflowBuilder workflowDefinition(WorkflowDef workflowDefinition) {
            this.workflowDefinition = workflowDefinition;
            return this;
        }

        public WorkflowBuilder priority(int priority) {
            this.priority = priority;
            return this;
        }

        public WorkflowBuilder lastRetriedTime(long lastRetriedTime) {
            this.lastRetriedTime = lastRetriedTime;
            return this;
        }

        public WorkflowBuilder failedTaskId(String failedTaskId) {
            this.failedTaskId = failedTaskId;
            return this;
        }

        public WorkflowBuilder createTime(long createTime) {
            this.createTime = createTime;
            return this;
        }

        public WorkflowBuilder updateTime(long updateTime) {
            this.updateTime = updateTime;
            return this;
        }

        public Workflow build() {
            return new Workflow(
                    status,
                    previousStatus,
                    endTime,
                    workflowId,
                    tasksSet ? tasks : new LinkedList<>(),
                    inputSet ? input : new HashMap<>(),
                    outputSet ? output : new HashMap<>(),
                    correlationId,
                    reasonForIncompletion,
                    failedReferenceTaskNamesSet ? failedReferenceTaskNames : new HashSet<>(),
                    failedTaskNamesSet ? failedTaskNames : new HashSet<>(),
                    workflowDefinition,
                    priority,
                    lastRetriedTime,
                    failedTaskId,
                    createTime,
                    updateTime);
        }

        @Override
        public String toString() {
            return "Workflow.WorkflowBuilder(status=" + status
                    + ", previousStatus=" + previousStatus
                    + ", endTime=" + endTime
                    + ", workflowId=" + workflowId
                    + ", tasks=" + tasks
                    + ", input=" + input
                    + ", output=" + output
                    + ", correlationId=" + correlationId
                    + ", reasonForIncompletion=" + reasonForIncompletion
                    + ", failedReferenceTaskNames=" + failedReferenceTaskNames
                    + ", failedTaskNames=" + failedTaskNames
                    + ", workflowDefinition=" + workflowDefinition
                    + ", priority=" + priority
                    + ", lastRetriedTime=" + lastRetriedTime
                    + ", failedTaskId=" + failedTaskId
                    + ", createTime=" + createTime
                    + ", updateTime=" + updateTime + ")";
        }
    }

    public static WorkflowBuilder builder() {
        return new WorkflowBuilder();
    }
}
