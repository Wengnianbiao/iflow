package com.helianhealth.iflow.core.model;

import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowTask;
import com.helianhealth.iflow.common.domain.run.Task;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowDef;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

/**
 * 任务运行时模型
 *
 * <p>引擎内部状态机所操作的可变对象，承载一次任务执行的全部状态：
 * <ul>
 *   <li>关联：{@link #taskId}、{@link #workflowInstanceId}、{@link #referenceTaskName}</li>
 *   <li>定义快照：{@link #workflowTask}（启动时拷贝，运行期不再回查仓储）</li>
 *   <li>状态机：{@link #status}、{@link #previousStatus}、{@link #retryCount}</li>
 *   <li>数据：{@link #inputData}（模板替换后真实值）、{@link #outputData}（任务执行结果）</li>
 * </ul>
 *
 * <p>对外 API 层直接使用 {@link WorkflowTask}（定义层）与 {@link TaskModel}（运行时层），
 * 不再引入传输层 DTO —— 同进程同构架构下，多一层映射只会增加漏字段的风险。
 */
@Getter
@Setter
public class TaskModel {

    /**
     * 任务状态机
     *
     * <ul>
     *   <li>{@code terminal} —— 终态，不再调度</li>
     *   <li>{@code successful} —— 是否计入成功（影响 workflow 走向）</li>
     *   <li>{@code retriable} —— 失败后是否可重试</li>
     * </ul>
     */
    public enum Status {
        SCHEDULED(false, true, true),
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

    /**
     * 任务实例 ID（全局唯一）
     */
    private String taskId;

    /**
     * 所属工作流实例 ID（关联 {@link WorkflowModel#getWorkflowId()}）
     */
    private String workflowInstanceId;

    /**
     * 所属工作流定义名（冗余，便于日志 / 监控）
     */
    private String workflowType;

    /**
     * 任务类型：HTTP / JDBC / SWITCH / FORK_JOIN / INLINE 等
     * 对应 {@link WorkflowTask#getType()}
     */
    private String taskType;

    /**
     * 任务定义名（{@code TaskDef.name}；为空时退化为 {@link #taskType}）
     */
    private String taskDefName;

    /**
     * 工作流内引用名（{@link WorkflowTask#getTaskReferenceName()}），DAG 引用键
     */
    private String referenceTaskName;

    /**
     * 当前状态
     */
    private volatile Status status;

    /**
     * 上一次状态（每次 {@link #setStatus(Status)}）
     */
    private Status previousStatus;

    /**
     * 入参（已完成 ${...} 模板替换的真实值）
     */
    private Map<String, Object> inputData = new HashMap<>();

    /**
     * 任务原始返回值：可序列化为 JSON 的对象、数组、标量或 null，不添加引擎包装字段。
     * 控制任务可维护自己的 Map 输出；执行诊断使用 {@code reasonForInCompletion}。
     */
    private Object outputData = new HashMap<>();

    /**
     * 已重试次数
     */
    private int retryCount;

    /**
     * 同一工作流内任务的全局序号（按调度顺序自增）
     */
    private int seq;

    /**
     * 被重试任务的 taskId（链式追溯历史重试）
     */
    private String retriedTaskId;

    /**
     * 本任务是否已被后续重试任务接管
     */
    private boolean retried;

    /**
     * 任务是否已执行完成（含失败终态），区别于 status
     */
    private volatile boolean executed;

    /**
     * 关键字
     */
    private String correlationId;

    /**
     * 调度时间（毫秒）
     */
    private long scheduledTime;

    /**
     * 首次调度时间（重试时不重置，用于按总时长熔断）
     */
    private long firstScheduledTime;

    /**
     * 真正开始执行时间
     */
    private long startTime;

    /**
     * 完成时间（含失败终态）
     */
    private long endTime;

    /**
     * 最后一次状态更新时间
     */
    private long updateTime;

    /**
     * 失败原因（仅在非成功终态时填充）
     */
    private String reasonForInCompletion;

    /**
     * 任务定义快照（{@link WorkflowDef} ）。
     *
     * <p>引擎不依赖外部仓储反复查 DAG —— 实例落库时就把定义快照存进来，重放、回放、降级都自包含。
     */
    private WorkflowTask workflowTask;

    /**
     * 父任务 ID（FORK_JOIN / DO_WHILE 子任务、事件任务等关联父）
     */
    private String parentTaskId;

    /**
     * 当前所在的最内层 FORK 运行时任务 ID；为空表示根执行域。
     *
     * <p>嵌套 FORK 的控制节点自身保留外层 execution scope，子分支切换到新的 forkTaskId，
     * 对应 JOIN 再恢复控制节点原有 scope。
     * 业务任务在分支线程池执行，控制节点仍由工作流调用线程推进。
     */
    private String forkTaskId;

    /**
     * 循环执行的迭代索引
     */
    private int iteration;

    /**
     * 是否循环体任务（iteration &gt; 0 表示 DO_WHILE 迭代内任务）。
     */
    public boolean isLoopOverTask() {
        return iteration > 0;
    }

    /** 是否属于 FORK 分支执行域。 */
    public boolean isForkBranchTask() {
        return forkTaskId != null && !forkTaskId.isBlank();
    }

    /**
     * 设置状态：自动记录 {@link #previousStatus}，便于状态变更监听 / 审计。
     */
    public void setStatus(Status status) {
        if (this.status != status) {
            this.previousStatus = this.status;
        }
        this.status = status;
    }

    public String getTaskDefName() {
        if (taskDefName == null || taskDefName.isEmpty()) {
            return taskType;
        }
        return taskDefName;
    }

    public void addInput(String key, Object value) {
        this.inputData.put(key, value);
    }

    public void addInput(Map<String, Object> data) {
        if (data != null) {
            this.inputData.putAll(data);
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

    /** 供确定输出为对象的控制任务使用；不把数组、标量或 null 隐式转为 Map。 */
    @SuppressWarnings("unchecked")
    public Map<String, Object> outputAsMap() {
        if (!(outputData instanceof Map<?, ?>)) {
            throw new IllegalStateException("Task '" + referenceTaskName
                    + "' requires an object output, but was "
                    + (outputData == null ? "null" : outputData.getClass().getSimpleName()));
        }
        return (Map<String, Object>) outputData;
    }

    @Override
    public String toString() {
        return taskId == null ? referenceTaskName : taskId;
    }

    /** 转为对外运行时视图。 */
    public Task toTask() {
        return WorkflowExecutionMapper.toTask(this);
    }

    public static TaskModel fromTask(Task task) {
        return WorkflowExecutionMapper.toTaskModel(task);
    }
}
