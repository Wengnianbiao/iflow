package com.helianhealth.iflow.core.model;

import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowDef;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 工作流运行时模型
 *
 * <p>引擎内部状态机所操作的可变对象，承载一次工作流执行的完整上下文：
 * <ul>
 *   <li>定义快照：{@link #workflowDefinition}（启动时拷贝，运行期不再回查仓储）</li>
 *   <li>任务列表：{@link #tasks}（按调度顺序，含全部重试派生）</li>
 *   <li>状态机：{@link #status}、{@link #previousStatus}、{@link #failedTaskId}</li>
 *   <li>数据：{@link #input}、{@link #output}</li>
 * </ul>
 */
public class WorkflowModel {

    /**
     * 工作流状态机
     */
    public enum Status {
        RUNNING(false, false),
        COMPLETED(true, true),
        FAILED(true, false),
        TIMED_OUT(true, false),
        TERMINATED(true, false),
        INTERRUPTED(true, false);

        private final boolean terminal;
        private final boolean successful;

        Status(boolean terminal, boolean successful) {
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

    /**
     * 工作流实例 ID（全局唯一）
     */
    private String workflowId;

    /**
     * 当前状态
     */
    private volatile Status status = Status.RUNNING;

    /**
     * 上一次状态（每次 {@link #setStatus(Status)} 自动维护）
     */
    private Status previousStatus;

    /**
     * 关键字，启动时由定义的 {@code correlationExpression} 解析写入，运行时可由 {@code SET_KEYWORD} 覆盖。
     */
    private String correlationId;

    /**
     * 已调度的全部任务实例（按 seq 排序，含已完成 / 进行中 / 失败）
     */
    private List<TaskModel> tasks = new LinkedList<>();

    public void setTasks(List<TaskModel> tasks) {
        this.tasks = tasks;
    }

    /**
     * 工作流入参
     */
    private Map<String, Object> input = new HashMap<>();

    /**
     * 工作流出参
     */
    private Object output = new HashMap<>();

    /**
     * 工作流定义
     */
    private WorkflowDef workflowDefinition;

    /**
     * 开始时间
     */
    private long startTime;

    /**
     * 终态时间
     */
    private long endTime;

    /**
     * 最后一次状态更新时间
     */
    private long updateTime;

    /**
     * 最后一次工作流级重试时间
     */
    private long lastRetriedTime;

    /**
     * 失败原因
     */
    private String reasonForInCompletion;

    /**
     * 失败任务 ID（FAILED 时填充，便于直达失败现场）
     */
    private String failedTaskId;

    /**
     * 失败任务 referenceName 集合（包含被忽略 / 重试通过的也保留，便于审计）
     */
    private Set<String> failedReferenceTaskNames = new HashSet<>();

    /**
     * 失败任务 taskName 集合
     */
    private Set<String> failedTaskNames = new HashSet<>();

    /**
     * 设置状态：自动记录 {@link #previousStatus}。
     */
    public void setStatus(Status status) {
        if (this.status != status) {
            this.previousStatus = this.status;
        }
        this.status = status;
    }

    /**
     * 工作流路由（对应 {@link WorkflowDef#getRoute()}）
     */
    public String getWorkflowRoute() {
        return workflowDefinition == null ? null : workflowDefinition.getRoute();
    }

    public String getWorkflowName() {
        return workflowDefinition == null ? null : workflowDefinition.getName();
    }

    /**
     * 工作流定义版本（便捷方法）
     */
    public int getWorkflowVersion() {
        return workflowDefinition == null ? 0 : workflowDefinition.getVersion();
    }

    /**
     * 取指定 referenceTaskName 的最后一次任务实例。
     * <p>同一 ref 可能有多条（每次重试派生一条新 {@link TaskModel}），按列表顺序取最后一条即最新。
     */
    public TaskModel getTaskByRefName(String refName) {
        if (refName == null) {
            throw new IllegalArgumentException(
                    "refName cannot be null; dynamic task must set referenceTaskName explicitly");
        }
        TaskModel last = null;
        for (TaskModel task : tasks) {
            if (task.getReferenceTaskName() == null) {
                throw new IllegalStateException(
                        "Task " + task.getTaskDefName() + ", seq=" + task.getSeq()
                                + " does not have reference name specified");
            }
            if (refName.equals(task.getReferenceTaskName())) {
                last = task;
            }
        }
        return last;
    }

    public void addInput(String key, Object value) {
        this.input.put(key, value);
    }

    public void addInput(Map<String, Object> data) {
        if (data != null) {
            this.input.putAll(data);
        }
    }

    public void addOutput(String key, Object value) {
        outputAsMap().put(key, value);
    }

    public void addOutput(Map<String, Object> data) {
        if (data != null) {
            outputAsMap().putAll(data);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> outputAsMap() {
        if (!(output instanceof Map<?, ?>)) {
            throw new IllegalStateException("Workflow output must be an object to add named fields");
        }
        return (Map<String, Object>) output;
    }

    /**
     * 简短表示（日志友好）：{@code route.version/workflowId}
     */
    public String toShortString() {
        String route = workflowDefinition != null ? workflowDefinition.getRoute() : null;
        Integer version = workflowDefinition != null ? workflowDefinition.getVersion() : null;
        return String.format("%s.%s/%s", route, version, workflowId);
    }

    @Override
    public String toString() {
        String route = workflowDefinition != null ? workflowDefinition.getRoute() : null;
        Integer version = workflowDefinition != null ? workflowDefinition.getVersion() : null;
        return String.format("%s.%s/%s.%s", route, version, workflowId, status);
    }

    public String getWorkflowId() {
        return this.workflowId;
    }

    public Status getStatus() {
        return this.status;
    }

    public Status getPreviousStatus() {
        return this.previousStatus;
    }

    public String getCorrelationId() {
        return this.correlationId;
    }

    public List<TaskModel> getTasks() {
        return this.tasks;
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

    public long getStartTime() {
        return this.startTime;
    }

    public long getEndTime() {
        return this.endTime;
    }

    public long getUpdateTime() {
        return this.updateTime;
    }

    public long getLastRetriedTime() {
        return this.lastRetriedTime;
    }

    public String getReasonForInCompletion() {
        return this.reasonForInCompletion;
    }

    public String getFailedTaskId() {
        return this.failedTaskId;
    }

    public Set<String> getFailedReferenceTaskNames() {
        return this.failedReferenceTaskNames;
    }

    public Set<String> getFailedTaskNames() {
        return this.failedTaskNames;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    public void setPreviousStatus(Status previousStatus) {
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

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public void setUpdateTime(long updateTime) {
        this.updateTime = updateTime;
    }

    public void setLastRetriedTime(long lastRetriedTime) {
        this.lastRetriedTime = lastRetriedTime;
    }

    public void setReasonForInCompletion(String reasonForInCompletion) {
        this.reasonForInCompletion = reasonForInCompletion;
    }

    public void setFailedTaskId(String failedTaskId) {
        this.failedTaskId = failedTaskId;
    }

    public void setFailedReferenceTaskNames(Set<String> failedReferenceTaskNames) {
        this.failedReferenceTaskNames = failedReferenceTaskNames;
    }

    public void setFailedTaskNames(Set<String> failedTaskNames) {
        this.failedTaskNames = failedTaskNames;
    }
}
