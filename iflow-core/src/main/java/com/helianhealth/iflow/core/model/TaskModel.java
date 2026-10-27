package com.helianhealth.iflow.core.model;

import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowTask;
import com.helianhealth.iflow.common.domain.run.Task;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowDef;

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
     * 节点任务名称（{@code WorkflowTask.name}；为空时退化为 {@link #taskType}）
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
     * 执行域只表达图关系；HTTP/JDBC/SPI/WEBSERVICE 经异步任务池执行，MAPPING 和控制节点由驱动线程推进。
     */
    private String forkTaskId;

    /**
     * 循环执行的迭代索引
     */
    private int iteration;

    /**
     * 是否循环体任务（iteration > 0 表示 DO_WHILE 迭代内任务）。
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

    public String getTaskId() {
        return this.taskId;
    }

    public String getWorkflowInstanceId() {
        return this.workflowInstanceId;
    }

    public String getWorkflowType() {
        return this.workflowType;
    }

    public String getTaskType() {
        return this.taskType;
    }

    public String getReferenceTaskName() {
        return this.referenceTaskName;
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

    public int getRetryCount() {
        return this.retryCount;
    }

    public int getSeq() {
        return this.seq;
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

    public String getReasonForInCompletion() {
        return this.reasonForInCompletion;
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

    public int getIteration() {
        return this.iteration;
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

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public void setTaskDefName(String taskDefName) {
        this.taskDefName = taskDefName;
    }

    public void setReferenceTaskName(String referenceTaskName) {
        this.referenceTaskName = referenceTaskName;
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

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public void setSeq(int seq) {
        this.seq = seq;
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

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
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

    public void setReasonForInCompletion(String reasonForInCompletion) {
        this.reasonForInCompletion = reasonForInCompletion;
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

    public void setIteration(int iteration) {
        this.iteration = iteration;
    }
}
