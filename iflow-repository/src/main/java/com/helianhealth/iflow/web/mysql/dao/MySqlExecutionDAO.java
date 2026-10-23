package com.helianhealth.iflow.web.mysql.dao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.iflow.core.dao.ExecutionDAO;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import com.helianhealth.iflow.web.mysql.config.ConditionalOnMySqlRepository;
import com.helianhealth.iflow.web.mysql.util.Query;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Repository
@ConditionalOnMySqlRepository
public class MySqlExecutionDAO extends MySqlBaseDAO implements ExecutionDAO {

    /** 压缩后仍很大的写入。12MB 以上在当前 OceanBase 上大约要 1 分钟，默认 10 秒会失败。 */
    private static final long LARGE_WRITE_TIMEOUT_MICROS = 180_000_000L;

    private static final String WORKFLOW_COLUMNS =
            "id, workflow_id, correlation_id, json_data";

    private static final String TASK_COLUMNS =
            "id, task_id, json_data";
    private static final String ALIASED_TASK_COLUMNS =
            "t.id, t.task_id, t.json_data";

    private final MySqlExecutionJsonSupport jsonSupport;
    private final MySqlIndexDAO indexDAO;

    @Autowired
    public MySqlExecutionDAO(
            DataSource dataSource,
            ObjectMapper objectMapper,
            MySqlIndexDAO indexDAO) {
        super(objectMapper, dataSource);
        this.jsonSupport = new MySqlExecutionJsonSupport(objectMapper);
        this.indexDAO = indexDAO;
    }

    public MySqlExecutionDAO(DataSource dataSource, ObjectMapper objectMapper) {
        this(dataSource, objectMapper, new MySqlIndexDAO(dataSource, objectMapper,
                new com.helianhealth.iflow.core.execution.tasks.SystemTaskRegistry(java.util.Set.of())));
    }

    MySqlExecutionDAO(DataSource dataSource) {
        this(dataSource, new ObjectMapper());
    }

    @Override
    public List<TaskModel> createTasks(List<TaskModel> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return List.of();
        }
        return getWithTransaction(
                tx -> {
                    List<TaskModel> created = new LinkedList<>();
                    for (TaskModel task : tasks) {
                        Objects.requireNonNull(task.getTaskId(), "taskId");
                        Objects.requireNonNull(task.getWorkflowInstanceId(), "workflowInstanceId");
                        Objects.requireNonNull(task.getReferenceTaskName(), "referenceTaskName");

                        int scheduled =
                                insertTaskScheduled(
                                        tx,
                                        task.getWorkflowInstanceId(),
                                        MySqlExecutionJsonSupport.taskKey(task),
                                        task.getTaskId());
                        if (scheduled == 0) {
                            continue;
                        }

                        long now = System.currentTimeMillis();
                        if (task.getScheduledTime() == 0) {
                            task.setScheduledTime(now);
                        }
                        if (task.getFirstScheduledTime() == 0) {
                            task.setFirstScheduledTime(task.getScheduledTime());
                        }
                        if (task.getUpdateTime() == 0) {
                            task.setUpdateTime(now);
                        }
                        refreshTerminalTaskEndTime(task, now);

                        insertTaskInstance(tx, task);
                        indexDAO.indexTask(tx, task);
                        insertWorkflowToTask(tx, task.getWorkflowInstanceId(), task.getTaskId());
                        syncTaskInProgress(tx, task);
                        created.add(task);
                    }
                    return created;
                });
    }

    @Override
    public void updateTask(TaskModel task) {
        getWithTransaction(
                tx -> {
                    long now = System.currentTimeMillis();
                    task.setUpdateTime(now);
                    refreshTerminalTaskEndTime(task, now);
                    updateTaskInstance(tx, task);
                    indexDAO.indexTask(tx, task);
                    syncTaskInProgress(tx, task);
                    return null;
                });
    }

    @Override
    public Optional<TaskModel> getTask(String taskId) {
        return Optional.ofNullable(
                queryWithTransaction(
                        "SELECT " + TASK_COLUMNS + " FROM task WHERE task_id = ?",
                        q -> q.addParameter(taskId).executeAndFetch(this::readTaskOrNull)));
    }

    @Override
    public List<TaskModel> getTasks(List<String> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            return List.of();
        }
        List<TaskModel> tasks = getWithTransaction(tx -> selectTasksByIds(tx, taskIds));
        Map<String, TaskModel> byId =
                tasks.stream()
                        .collect(
                                Collectors.toMap(
                                        TaskModel::getTaskId,
                                        task -> task,
                                        (left, right) -> left,
                                        LinkedHashMap::new));
        return taskIds.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @Override
    public boolean removeTask(String taskId) {
        return getWithTransaction(
                tx -> {
                    deleteTaskInProgress(tx, taskId);
                    boolean removed = deleteTaskInstance(tx, taskId) > 0;
                    indexDAO.removeTask(tx, taskId);
                    return removed;
                });
    }

    @Override
    public List<TaskModel> getTasksForWorkflow(String workflowId) {
        return getWithTransaction(tx -> selectTasksForWorkflow(tx, workflowId));
    }

    @Override
    public List<TaskModel> getTasksByReferenceName(String workflowId, String referenceTaskName) {
        return getTasksForWorkflow(workflowId).stream()
                .filter(task -> Objects.equals(referenceTaskName, task.getReferenceTaskName()))
                .sorted(Comparator.comparingInt(TaskModel::getRetryCount))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @Override
    public List<TaskModel> getPendingTasksForTaskType(String taskType) {
        return queryWithTransaction(
                "SELECT "
                        + ALIASED_TASK_COLUMNS
                        + " FROM task t INNER JOIN task_in_progress p ON p.task_id = t.task_id "
                        + "WHERE p.task_def_name = ?",
                q -> q.addParameter(taskType).executeAndFetch(this::readTasks))
                .stream()
                .filter(MySqlExecutionDAO::isPendingTask)
                .sorted(Comparator.comparingInt(TaskModel::getSeq))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @Override
    public List<TaskModel> getPendingTasks() {
        return queryWithTransaction(
                        "SELECT "
                                + ALIASED_TASK_COLUMNS
                                + " FROM task t INNER JOIN task_in_progress p ON p.task_id = t.task_id",
                        q -> q.executeAndFetch(this::readTasks))
                .stream()
                .filter(MySqlExecutionDAO::isPendingTask)
                .sorted(Comparator.comparingLong(TaskModel::getScheduledTime))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @Override
    public long getInProgressTaskCount(String taskDefName) {
        return queryLong(
                "SELECT COUNT(*) FROM task_in_progress WHERE task_def_name = ?",
                q -> q.addParameter(taskDefName));
    }

    @Override
    public void createWorkflow(WorkflowModel workflow) {
        Objects.requireNonNull(workflow.getWorkflowId(), "workflowId");
        getWithTransaction(
                tx -> {
                    long now = System.currentTimeMillis();
                    if (workflow.getStartTime() == 0) {
                        workflow.setStartTime(now);
                    }
                    workflow.setUpdateTime(now);
                    refreshTerminalWorkflowEndTime(workflow, now);

                    insertWorkflowInstance(tx, workflow);
                    indexDAO.indexWorkflow(tx, workflow);
                    if (workflow.getStatus() == WorkflowModel.Status.RUNNING) {
                        insertWorkflowPending(
                                tx,
                                workflow.getWorkflowRoute(),
                                workflow.getWorkflowVersion(),
                                workflow.getWorkflowId());
                    }
                    return workflow.getWorkflowId();
                });
    }

    @Override
    public String updateWorkflow(WorkflowModel workflow) {
        return getWithTransaction(
                tx -> {
                    long now = System.currentTimeMillis();
                    workflow.setUpdateTime(now);
                    refreshTerminalWorkflowEndTime(workflow, now);

                    updateWorkflowInstance(tx, workflow);
                    indexDAO.indexWorkflow(tx, workflow);
                    if (workflow.getStatus() != null && workflow.getStatus().isTerminal()) {
                        deleteWorkflowPending(tx, workflow.getWorkflowId());
                    } else if (workflow.getStatus() == WorkflowModel.Status.RUNNING) {
                        insertWorkflowPending(
                                tx,
                                workflow.getWorkflowRoute(),
                                workflow.getWorkflowVersion(),
                                workflow.getWorkflowId());
                    }
                    return workflow.getWorkflowId();
                });
    }

    @Override
    public WorkflowModel getWorkflow(String workflowId) {
        return getWithTransaction(
                tx -> {
                    WorkflowModel workflow = selectWorkflowInstance(tx, workflowId);
                    if (workflow == null) {
                        return null;
                    }

                    return workflow;
                });
    }

    @Override
    public Optional<WorkflowModel> getWorkflow(String workflowId, boolean includeTasks) {
        return Optional.ofNullable(
                getWithTransaction(
                        tx -> {
                            WorkflowModel workflow = selectWorkflowInstance(tx, workflowId);
                            if (workflow != null && includeTasks) {
                                workflow.setTasks(selectTasksForWorkflow(tx, workflowId));
                            }
                            return workflow;
                        }));
    }

    @Override
    public void removeWorkflow(String workflowId) {
        getWithTransaction(
                tx -> {
                    List<TaskModel> tasks = selectTasksForWorkflow(tx, workflowId);
                    deleteWorkflowPending(tx, workflowId);
                    deleteWorkflowToTasks(tx, workflowId);
                    for (TaskModel task : tasks) {
                        deleteTaskInProgress(tx, task.getTaskId());
                        deleteTaskInstance(tx, task.getTaskId());
                    }
                    boolean removed = deleteWorkflowInstance(tx, workflowId) > 0;
                    indexDAO.removeWorkflow(tx, workflowId);
                    return removed;
                });
    }

    @Override
    public List<String> getRunningWorkflowIds(String workflowRoute, int version) {
        return getPendingWorkflowsByType(workflowRoute, version).stream()
                .map(WorkflowModel::getWorkflowId)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @Override
    public List<String> getRunningWorkflowIds() {
        return queryWithTransaction(
                "SELECT workflow_id FROM workflow_pending",
                q -> q.executeAndFetch(String.class));
    }

    @Override
    public List<WorkflowModel> getPendingWorkflowsByType(String workflowRoute, int version) {
        return getWithTransaction(
                tx -> {
                    List<WorkflowModel> workflows =
                            query(
                                    tx,
                                    "SELECT w.* FROM workflow_pending p "
                                            + "INNER JOIN workflow w ON w.workflow_id = p.workflow_id "
                                            + "WHERE p.workflow_type = ?",
                                    q ->
                                            q.addParameter(workflowRoute)
                                                    .executeAndFetch(this::readWorkflows));
                    workflows = workflows.stream()
                            .filter(workflow -> matchesWorkflowType(workflow, workflowRoute, version))
                            .filter(workflow -> workflow.getStatus() == WorkflowModel.Status.RUNNING)
                            .collect(Collectors.toCollection(ArrayList::new));
                    for (WorkflowModel workflow : workflows) {
                        workflow.setTasks(
                                new LinkedList<>(selectTasksForWorkflow(tx, workflow.getWorkflowId())));
                    }
                    return workflows;
                });
    }

    @Override
    public long getPendingWorkflowCount(String workflowRoute) {
        return getPendingWorkflowsByType(workflowRoute, 0).size();
    }

    @Override
    public List<WorkflowModel> getWorkflowsByType(String workflowRoute, long startTime, long endTime) {
        return selectAllWorkflows().stream()
                .filter(workflow -> matchesWorkflowRoute(workflow, workflowRoute))
                .filter(workflow -> workflow.getStartTime() >= startTime && workflow.getStartTime() <= endTime)
                .sorted(Comparator.comparingLong(WorkflowModel::getStartTime))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @Override
    public List<WorkflowModel> getWorkflowsByCorrelationId(
            String workflowRoute, String correlationId, boolean includeTasks) {
        return getWithTransaction(
                tx -> {
                    List<WorkflowModel> workflows =
                            query(
                                    tx,
                                    "SELECT "
                                            + WORKFLOW_COLUMNS
                                            + " FROM workflow WHERE correlation_id = ?",
                                    q ->
                                            q.addParameter(correlationId)
                                                    .executeAndFetch(this::readWorkflows));
                    workflows = workflows.stream()
                            .filter(workflow -> matchesWorkflowRoute(workflow, workflowRoute))
                            .sorted(Comparator.comparingLong(WorkflowModel::getStartTime))
                            .collect(Collectors.toCollection(ArrayList::new));
                    if (includeTasks) {
                        for (WorkflowModel workflow : workflows) {
                            workflow.setTasks(
                                    new LinkedList<>(
                                            selectTasksForWorkflow(tx, workflow.getWorkflowId())));
                        }
                    }
                    return workflows;
                });
    }

    @Override
    public List<WorkflowModel> listRecentInstances(String workflowRoute, int version, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return selectAllWorkflows().stream()
                .filter(workflow -> matchesWorkflowType(workflow, workflowRoute, version))
                .sorted(Comparator.comparingLong(WorkflowModel::getStartTime).reversed())
                .limit(safeLimit)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private void insertWorkflowInstance(Connection tx, WorkflowModel workflow) {
        String payload = jsonSupport.toWorkflowPayload(workflow);
        executeUpdate(
                tx,
                withLargeWriteHint(
                        "INSERT INTO workflow (workflow_id, correlation_id, json_data) VALUES (?, ?, ?)",
                        payload),
                q ->
                        q.addParameter(workflow.getWorkflowId())
                                .addParameter(workflow.getCorrelationId())
                                .addParameter(payload));
    }

    private void updateWorkflowInstance(Connection tx, WorkflowModel workflow) {
        String payload = jsonSupport.toWorkflowPayload(workflow);
        executeUpdate(
                tx,
                withLargeWriteHint(
                        "UPDATE workflow SET correlation_id = ?, json_data = ?, modified_on = CURRENT_TIMESTAMP "
                                + "WHERE workflow_id = ?",
                        payload),
                q ->
                        q.addParameter(workflow.getCorrelationId())
                                .addParameter(payload)
                                .addParameter(workflow.getWorkflowId()));
    }

    private WorkflowModel selectWorkflowInstance(Connection tx, String workflowId) {
        return query(
                tx,
                "SELECT " + WORKFLOW_COLUMNS + " FROM workflow WHERE workflow_id = ?",
                q -> q.addParameter(workflowId).executeAndFetch(this::readWorkflowOrNull));
    }

    private List<WorkflowModel> selectAllWorkflows() {
        return queryWithTransaction(
                "SELECT " + WORKFLOW_COLUMNS + " FROM workflow",
                q -> q.executeAndFetch(this::readWorkflows));
    }

    private int deleteWorkflowInstance(Connection tx, String workflowId) {
        return executeUpdate(
                tx,
                "DELETE FROM workflow WHERE workflow_id = ?",
                q -> q.addParameter(workflowId));
    }

    private void insertTaskInstance(Connection tx, TaskModel task) {
        String payload = jsonSupport.toTaskPayload(task);
        executeUpdate(
                tx,
                withLargeWriteHint("INSERT INTO task (task_id, json_data) VALUES (?, ?)", payload),
                q -> q.addParameter(task.getTaskId()).addParameter(payload));
    }

    private void updateTaskInstance(Connection tx, TaskModel task) {
        String payload = jsonSupport.toTaskPayload(task);
        executeUpdate(
                tx,
                withLargeWriteHint(
                        "UPDATE task SET json_data = ?, modified_on = CURRENT_TIMESTAMP WHERE task_id = ?",
                        payload),
                q -> q.addParameter(payload).addParameter(task.getTaskId()));
    }

    static String withLargeWriteHint(String sql, String payload) {
        return withLargeWriteHint(sql, payload, ExecutionPayloadCodec.LARGE_WRITE_BYTES);
    }

    static String withLargeWriteHint(String sql, String payload, int thresholdBytes) {
        if (ExecutionPayloadCodec.utf8ByteLength(payload) < thresholdBytes) {
            return sql;
        }
        int splitAt = sql.startsWith("INSERT") ? "INSERT".length() : "UPDATE".length();
        return sql.substring(0, splitAt)
                + " /*+ QUERY_TIMEOUT("
                + LARGE_WRITE_TIMEOUT_MICROS
                + ") */"
                + sql.substring(splitAt);
    }

    private int deleteTaskInstance(Connection tx, String taskId) {
        return executeUpdate(
                tx, "DELETE FROM task WHERE task_id = ?", q -> q.addParameter(taskId));
    }

    private List<TaskModel> selectTasksForWorkflow(Connection tx, String workflowId) {
        return query(
                        tx,
                        "SELECT t.* FROM task t INNER JOIN workflow_to_task m ON m.task_id = t.task_id "
                                + "WHERE m.workflow_id = ?",
                        q -> q.addParameter(workflowId).executeAndFetch(this::readTasks))
                .stream()
                .sorted(Comparator.comparingInt(TaskModel::getSeq))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private List<TaskModel> selectTasksByIds(Connection tx, List<String> taskIds) {
        String placeholders = taskIds.stream().map(ignored -> "?").collect(Collectors.joining(", "));
        return query(
                tx,
                "SELECT " + TASK_COLUMNS + " FROM task WHERE task_id IN (" + placeholders + ")",
                q -> {
                    for (String taskId : taskIds) {
                        q.addParameter(taskId);
                    }
                    return q.executeAndFetch(this::readTasks);
                });
    }

    private int insertTaskScheduled(
            Connection tx, String workflowId, String taskKey, String taskId) {
        return executeUpdate(
                tx,
                "INSERT IGNORE INTO task_scheduled (workflow_id, task_key, task_id) VALUES (?, ?, ?)",
                q -> q.addParameter(workflowId).addParameter(taskKey).addParameter(taskId));
    }

    private void insertWorkflowToTask(Connection tx, String workflowId, String taskId) {
        executeUpdate(
                tx,
                "INSERT IGNORE INTO workflow_to_task (workflow_id, task_id) VALUES (?, ?)",
                q -> q.addParameter(workflowId).addParameter(taskId));
    }

    private void deleteWorkflowToTasks(Connection tx, String workflowId) {
        executeUpdate(
                tx,
                "DELETE FROM workflow_to_task WHERE workflow_id = ?",
                q -> q.addParameter(workflowId));
    }

    private void insertWorkflowPending(
            Connection tx, String workflowRoute, int version, String workflowId) {
        executeUpdate(
                tx,
                "INSERT IGNORE INTO workflow_pending (workflow_type, workflow_id) VALUES (?, ?)",
                q -> q.addParameter(workflowRoute).addParameter(workflowId));
    }

    private void deleteWorkflowPending(Connection tx, String workflowId) {
        executeUpdate(
                tx,
                "DELETE FROM workflow_pending WHERE workflow_id = ?",
                q -> q.addParameter(workflowId));
    }

    private void insertTaskInProgress(Connection tx, TaskModel task) {
        executeUpdate(
                tx,
                "INSERT IGNORE INTO task_in_progress (task_def_name, task_id, workflow_id) "
                        + "VALUES (?, ?, ?)",
                q ->
                        q.addParameter(task.getTaskDefName())
                                .addParameter(task.getTaskId())
                                .addParameter(task.getWorkflowInstanceId()));
    }

    private void deleteTaskInProgress(Connection tx, String taskId) {
        executeUpdate(
                tx,
                "DELETE FROM task_in_progress WHERE task_id = ?",
                q -> q.addParameter(taskId));
    }

    private void syncTaskInProgress(Connection tx, TaskModel task) {
        deleteTaskInProgress(tx, task.getTaskId());
        if (isPendingTask(task)) {
            insertTaskInProgress(tx, task);
        }
    }

    private static boolean isPendingTask(TaskModel task) {
        return task.getStatus() == TaskModel.Status.SCHEDULED
                || task.getStatus() == TaskModel.Status.IN_PROGRESS;
    }

    private int executeUpdate(Connection tx, String sql, Consumer<Query> binder) {
        return query(
                tx,
                sql,
                q -> {
                    binder.accept(q);
                    return q.executeUpdate();
                });
    }

    private long queryLong(String sql, Consumer<Query> binder) {
        return queryWithTransaction(
                sql,
                q -> {
                    binder.accept(q);
                    Object value = q.executeScalar();
                    if (value == null) {
                        return 0L;
                    }
                    if (value instanceof Number number) {
                        return number.longValue();
                    }
                    return Long.parseLong(value.toString());
                });
    }

    private boolean matchesWorkflowRoute(WorkflowModel workflow, String workflowRoute) {
        return Objects.equals(workflowRoute, workflow.getWorkflowRoute());
    }

    private boolean matchesWorkflowType(WorkflowModel workflow, String workflowRoute, int version) {
        return matchesWorkflowRoute(workflow, workflowRoute)
                && (version <= 0 || workflow.getWorkflowVersion() == version);
    }

    private WorkflowModel readWorkflowOrNull(ResultSet rs) throws SQLException {
        return rs.next() ? readWorkflow(rs) : null;
    }

    private List<WorkflowModel> readWorkflows(ResultSet rs) throws SQLException {
        List<WorkflowModel> workflows = new ArrayList<>();
        while (rs.next()) {
            workflows.add(readWorkflow(rs));
        }
        return workflows;
    }

    private WorkflowModel readWorkflow(ResultSet rs) throws SQLException {
        WorkflowModel workflow = jsonSupport.fromWorkflowPayload(rs.getString("json_data"));
        if (workflow == null) {
            workflow = new WorkflowModel();
        }
        workflow.setWorkflowId(rs.getString("workflow_id"));
        if (workflow.getCorrelationId() == null) {
            workflow.setCorrelationId(rs.getString("correlation_id"));
        }
        return workflow;
    }

    private TaskModel readTaskOrNull(ResultSet rs) throws SQLException {
        return rs.next() ? readTask(rs) : null;
    }

    private List<TaskModel> readTasks(ResultSet rs) throws SQLException {
        List<TaskModel> tasks = new ArrayList<>();
        while (rs.next()) {
            tasks.add(readTask(rs));
        }
        return tasks;
    }

    private TaskModel readTask(ResultSet rs) throws SQLException {
        TaskModel task = jsonSupport.fromTaskPayload(rs.getString("json_data"));
        if (task == null) {
            task = new TaskModel();
        }
        task.setTaskId(rs.getString("task_id"));
        return task;
    }

    private void refreshTerminalTaskEndTime(TaskModel task, long now) {
        if (task.getStatus() != null && task.getStatus().isTerminal()) {
            task.setEndTime(now);
        }
    }

    private void refreshTerminalWorkflowEndTime(WorkflowModel workflow, long now) {
        if (workflow.getStatus() != null && workflow.getStatus().isTerminal()) {
            workflow.setEndTime(now);
        }
    }
}
