package com.helianhealth.agent.core.model;

import com.helianhealth.agent.common.domain.metadata.workflow.WorkflowDef;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.HashSet;
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
@Getter
@Setter
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
        PAUSED(false, true);

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
     * 关键字（启动时由定义 {@code correlationExpression} 表达式解析写入）
     * 同时可由 SET_KEYWORD task 去设置correlationId。
     */
    private String correlationId;

    /**
     * 已调度的全部任务实例（按 seq 排序，含已完成 / 进行中 / 失败）
     */
    private List<TaskModel> tasks = new CopyOnWriteArrayList<>();

    public void setTasks(List<TaskModel> tasks) {
        this.tasks =
                tasks == null
                        ? new CopyOnWriteArrayList<>()
                        : new CopyOnWriteArrayList<>(tasks);
    }

    /**
     * 工作流入参
     */
    private Map<String, Object> input = new HashMap<>();

    /**
     * 工作流出参
     */
    private Map<String, Object> output = new HashMap<>();

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
     * 工作流定义标识（对应 {@link WorkflowDef#getName()}）
     */
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
        this.output.put(key, value);
    }

    public void addOutput(Map<String, Object> data) {
        if (data != null) {
            this.output.putAll(data);
        }
    }

    /**
     * 简短表示（日志友好）：{@code name.version/workflowId}
     */
    public String toShortString() {
        String name = workflowDefinition != null ? workflowDefinition.getName() : null;
        Integer version = workflowDefinition != null ? workflowDefinition.getVersion() : null;
        return String.format("%s.%s/%s", name, version, workflowId);
    }

    @Override
    public String toString() {
        String name = workflowDefinition != null ? workflowDefinition.getName() : null;
        Integer version = workflowDefinition != null ? workflowDefinition.getVersion() : null;
        return String.format("%s.%s/%s.%s", name, version, workflowId, status);
    }
}
