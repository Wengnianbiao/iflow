package com.helianhealth.iflow.core.dao;

import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;

import java.util.List;
import java.util.Optional;

/**
 * 执行态 DAO。
 *
 * <p>承载运行时 {@link WorkflowModel} 与 {@link TaskModel} 的持久化访问。
 * 接口定义在 {@code iflow-core}，具体实现由各数据库厂商 DAO 分别提供。
 *
 * <p>设计要点：
 * <ul>
 *   <li>定义快照内联：{@link WorkflowModel#getWorkflowDefinition()} 和
 *       {@link TaskModel#getWorkflowTask()} 随执行实例整体序列化，回放无需再查 metadata。</li>
 *   <li>同一 referenceTaskName 可能有多条 {@link TaskModel}（每次重试派生），
 *       实现需以 {@code (workflowId, referenceTaskName, retryCount)} 维持唯一性。</li>
 *   <li>事件 / TTL / 并发限制等高级能力暂不引入，预留未来扩展。</li>
 * </ul>
 */
public interface ExecutionDAO {

    // ===== Task =====

    /**
     * 批量创建任务实例。
     *
     * <p><b>主键约束</b>：对于同一个工作流，{@code (referenceTaskName, retryCount)} 必须唯一 ——
     * 同一引用名的同一次重试只应插入一行；重复插入应被实现忽略或拒绝，避免重放时重复创建。
     *
     * @return 实际创建成功的任务列表（已被去重过滤后的结果）
     */
    List<TaskModel> createTasks(List<TaskModel> tasks);

    /**
     * 更新任务实例（按 {@link TaskModel#getTaskId()}）。
     */
    void updateTask(TaskModel task);

    /**
     * 按任务实例 ID 取任务。
     */
    Optional<TaskModel> getTask(String taskId);

    /**
     * 批量取任务（保持入参 taskIds 的顺序）。
     */
    List<TaskModel> getTasks(List<String> taskIds);

    /**
     * 删除任务实例。
     *
     * @return 是否真的删除了一行
     */
    boolean removeTask(String taskId);

    /**
     * 取指定工作流实例的全部任务（按 {@link TaskModel#getSeq()} 升序）。
     */
    List<TaskModel> getTasksForWorkflow(String workflowId);

    /**
     * 取指定工作流实例下、指定引用名的所有任务（含全部重试派生）。
     *
     * <p>调用方按 {@code retryCount} 自行取最新或全量，避免实现层做隐式语义选择。
     */
    List<TaskModel> getTasksByReferenceName(String workflowId, String referenceTaskName);

    /**
     * 按任务类型取等待执行或执行中的任务（SCHEDULED、IN_PROGRESS）。
     *
     * <p>用于运行态查询与监控统计，不用于恢复或后台拉取执行。
     */
    List<TaskModel> getPendingTasksForTaskType(String taskType);

    /**
     * 取全部等待执行或执行中的任务（SCHEDULED、IN_PROGRESS）。
     *
     * <p>仅用于运维查询和监控统计，不用于任务恢复。
     */
    List<TaskModel> getPendingTasks();

    /**
     * 统计指定任务定义当前等待执行及执行中的实例数（用于配额监控）。
     */
    long getInProgressTaskCount(String taskDefName);

    // ===== Workflow =====

    /**
     * 创建工作流实例。
     */
    void createWorkflow(WorkflowModel workflow);

    /**
     * 更新工作流实例（按 {@link WorkflowModel#getWorkflowId()}）。
     *
     * @return 工作流实例 ID
     */
    String updateWorkflow(WorkflowModel workflow);

    /**
     * 取工作流实例（不含任务列表，仅头部信息）。
     */
    WorkflowModel getWorkflow(String workflowId);

    /**
     * 取工作流实例。
     *
     * @param includeTasks 是否同时加载并按 seq 排序填充 {@link WorkflowModel#getTasks()}
     */
    Optional<WorkflowModel> getWorkflow(String workflowId, boolean includeTasks);

    /**
     * 删除工作流实例（连同其下全部任务）。
     */
    void removeWorkflow(String workflowId);

    /**
     * 取指定工作流定义当前处于运行中的实例 ID 列表。
     */
    List<String> getRunningWorkflowIds(String workflowRoute, int version);

    /**
     * 取全部处于 RUNNING 的工作流实例 ID。
     *
     * <p>供超时 sweep 扫描。扫到到期实例后只做终止，不用于恢复或继续调度。
     */
    List<String> getRunningWorkflowIds();

    /**
     * 取指定工作流定义当前处于运行中的实例（含任务列表）。
     */
    List<WorkflowModel> getPendingWorkflowsByType(String workflowRoute, int version);

    /**
     * 统计指定工作流定义的运行中实例数。
     */
    long getPendingWorkflowCount(String workflowRoute);

    /**
     * 按时间窗口查询某工作流定义的执行实例（不含任务列表）。
     *
     * @param startTime 起始时间戳（毫秒，含）
     * @param endTime   结束时间戳（毫秒，含）
     */
    List<WorkflowModel> getWorkflowsByType(String workflowRoute, long startTime, long endTime);

    /**
     * 按 correlationId 查询某工作流定义的执行实例。
     *
     * @param includeTasks 是否同时加载任务列表
     */
    List<WorkflowModel> getWorkflowsByCorrelationId(
            String workflowRoute, String correlationId, boolean includeTasks);

    /**
     * 查询某工作流定义最近的执行实例（按 start_time 降序，不含任务列表）。
     */
    List<WorkflowModel> listRecentInstances(String workflowRoute, int version, int limit);
}
