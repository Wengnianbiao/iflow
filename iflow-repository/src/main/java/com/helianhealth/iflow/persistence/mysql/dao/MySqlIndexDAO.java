package com.helianhealth.iflow.persistence.mysql.dao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.iflow.common.domain.statistics.ExecutionStatistics;
import com.helianhealth.iflow.common.base.PageList;
import com.helianhealth.iflow.common.domain.run.ExecutionSearch;
import com.helianhealth.iflow.core.dao.IndexDAO;
import com.helianhealth.iflow.core.execution.tasks.SystemTaskRegistry;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import com.helianhealth.iflow.persistence.mysql.config.ConditionalOnMySqlRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Repository
@ConditionalOnMySqlRepository
public class MySqlIndexDAO extends MySqlBaseDAO implements IndexDAO {
    private static final String AGGREGATES = "COUNT(*) AS total_count, "
            + "COALESCE(SUM(CASE WHEN s.terminal = FALSE THEN 1 ELSE 0 END), 0) AS running_count, "
            + "COALESCE(SUM(CASE WHEN s.terminal = TRUE AND s.successful = TRUE THEN 1 ELSE 0 END), 0) AS success_count, "
            + "COALESCE(SUM(CASE WHEN s.terminal = TRUE AND s.successful = FALSE THEN 1 ELSE 0 END), 0) AS failure_count, "
            + "COUNT(s.duration_ms) AS duration_count, AVG(s.duration_ms) AS avg_duration_ms, "
            + "MIN(s.duration_ms) AS min_duration_ms, MAX(s.duration_ms) AS max_duration_ms";
    private static final Map<String, String> SORTS = Map.of(
            "totalCount", "total_count", "avgDurationMs", "avg_duration_ms",
            "minDurationMs", "min_duration_ms", "maxDurationMs", "max_duration_ms",
            "successRate", "(1.0 * success_count / NULLIF(success_count + failure_count, 0))");
    private final Supplier<SystemTaskRegistry> registrySupplier;

    @Autowired
    public MySqlIndexDAO(DataSource dataSource, ObjectMapper objectMapper,
            ObjectProvider<SystemTaskRegistry> registryProvider) {
        super(objectMapper, dataSource);
        // 注册中心中的 DO_WHILE 依赖执行 DAO；只在写入统计时获取，避免初始化形成闭环。
        this.registrySupplier = registryProvider::getObject;
    }

    public MySqlIndexDAO(DataSource dataSource, ObjectMapper objectMapper, SystemTaskRegistry registry) {
        super(objectMapper, dataSource);
        this.registrySupplier = () -> registry;
    }

    @Override
    public void indexWorkflow(WorkflowModel workflow) {
        withTransaction(tx -> indexWorkflow(tx, workflow));
    }

    @Override
    public void indexTask(TaskModel task) {
        withTransaction(tx -> indexTask(tx, task));
    }

    @Override
    public void removeWorkflow(String workflowId) {
        withTransaction(tx -> removeWorkflow(tx, workflowId));
    }

    @Override
    public void removeTask(String taskId) {
        withTransaction(tx -> removeTask(tx, taskId));
    }

    /** 使用调用方的连接，索引与执行记录同事务提交。 */
    void indexWorkflow(Connection tx, WorkflowModel workflow) {
        var status = workflow.getStatus();
        boolean terminal = status != null && status.isTerminal();
        String sql = "INSERT INTO workflow_index "
                + "(workflow_id, workflow_route, workflow_name, workflow_version, status, terminal, successful, "
                + "start_time, end_time, duration_ms, correlation_id, update_time, reason_for_incompletion, failed_task_id) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        sql += upsert("workflow_route", "workflow_name", "workflow_version", "status",
                "terminal", "successful", "start_time", "end_time", "duration_ms", "correlation_id",
                "update_time", "reason_for_incompletion", "failed_task_id");
        execute(tx, sql, workflow.getWorkflowId(), workflow.getWorkflowRoute(), workflow.getWorkflowName(),
                workflow.getWorkflowVersion(), status == null ? "UNKNOWN" : status.name(), terminal,
                terminal && status.isSuccessful(), workflow.getStartTime(), workflow.getEndTime(),
                duration(terminal, workflow.getStartTime(), workflow.getEndTime()), workflow.getCorrelationId(),
                workflow.getUpdateTime(), workflow.getReasonForInCompletion(), workflow.getFailedTaskId());
    }

    void indexTask(Connection tx, TaskModel task) {
        var status = task.getStatus();
        boolean terminal = status != null && status.isTerminal();
        long start = task.getStartTime() > 0 ? task.getStartTime() : task.getScheduledTime();
        String ref = task.getWorkflowTask() != null ? task.getWorkflowTask().getTaskReferenceName() : null;
        if (ref == null || ref.isBlank()) {
            ref = task.getReferenceTaskName();
            // 只移除已知的当前轮次后缀，保留合法引用名中的双下划线。
            String suffix = "__" + task.getIteration();
            if (task.getIteration() > 0 && ref.endsWith(suffix)) ref = ref.substring(0, ref.length() - suffix.length());
        }
        SystemTaskRegistry registry = registrySupplier.get();
        Boolean async = registry.isSystemTask(task.getTaskType()) ? registry.get(task.getTaskType()).isAsync() : null;
        String sql = "INSERT INTO task_index "
                + "(task_id, workflow_id, workflow_route, node_ref, reference_task_name, task_name, task_type, "
                + "async_task, status, terminal, successful, retry_count, start_time, end_time, duration_ms, "
                + "scheduled_time, actual_start_time, update_time) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        // async_task 是首次写入时的分类快照，后续任务更新不重分类。
        sql += upsert("workflow_id", "workflow_route", "node_ref", "reference_task_name",
                "task_name", "task_type", "status", "terminal", "successful", "retry_count",
                "start_time", "end_time", "duration_ms", "scheduled_time", "actual_start_time", "update_time");
        execute(tx, sql, task.getTaskId(), task.getWorkflowInstanceId(), task.getWorkflowType(), ref,
                task.getReferenceTaskName(), task.getTaskDefName(), task.getTaskType(), async,
                status == null ? "UNKNOWN" : status.name(), terminal, terminal && status.isSuccessful(),
                task.getRetryCount(), start, task.getEndTime(), duration(terminal, start, task.getEndTime()),
                task.getScheduledTime(), task.getStartTime(), task.getUpdateTime());
    }

    void removeTask(Connection tx, String taskId) {
        execute(tx, "DELETE FROM task_index WHERE task_id = ?", taskId);
    }

    void removeWorkflow(Connection tx, String workflowId) {
        execute(tx, "DELETE FROM task_index WHERE workflow_id = ?", workflowId);
        execute(tx, "DELETE FROM workflow_index WHERE workflow_id = ?", workflowId);
    }

    @Override
    public PageList<ExecutionSearch.WorkflowSummary> searchWorkflows(ExecutionSearch.WorkflowQuery filter) {
        return getWithTransaction(tx -> {
            var conditions = new Conditions();
            conditions.in("workflow_route", filter.workflowRoutes());
            conditions.equal("workflow_version", filter.version());
            conditions.in("status", filter.statuses());
            conditions.equal("workflow_id", filter.workflowId());
            conditions.equal("correlation_id", filter.correlationId());
            conditions.time("start_time", filter.startTime(), filter.endTime());
            String from = " FROM workflow_index" + conditions.sql;
            long total = select(tx, "SELECT COUNT(*)" + from, conditions.params, rs -> { rs.next(); return rs.getLong(1); });
            var params = new ArrayList<>(conditions.params);
            params.add(filter.pageSize()); params.add(filter.offset());
            List<ExecutionSearch.WorkflowSummary> rows = select(tx, "SELECT *" + from
                    + " ORDER BY start_time DESC, workflow_id LIMIT ? OFFSET ?", params, rs -> {
                List<ExecutionSearch.WorkflowSummary> result = new ArrayList<>();
                while (rs.next()) result.add(new ExecutionSearch.WorkflowSummary(rs.getString("workflow_id"),
                        rs.getString("workflow_route"), rs.getString("workflow_name"), rs.getInt("workflow_version"),
                        rs.getString("status"), rs.getString("correlation_id"), rs.getLong("start_time"),
                        rs.getLong("end_time"), rs.getLong("update_time"), rs.getString("reason_for_incompletion"),
                        rs.getString("failed_task_id")));
                return result;
            });
            return new PageList<>(total, filter.pageNum(), filter.pageSize(), rows);
        });
    }

    @Override
    public PageList<ExecutionSearch.TaskSummary> searchTasks(ExecutionSearch.TaskQuery filter) {
        return getWithTransaction(tx -> {
            var conditions = new Conditions();
            conditions.in("s.workflow_route", filter.workflowRoutes());
            conditions.equal("s.workflow_id", filter.workflowId());
            conditions.equal("w.correlation_id", filter.correlationId());
            conditions.equal("s.task_id", filter.taskId());
            conditions.equal("s.node_ref", filter.nodeRef());
            conditions.equal("s.task_type", filter.taskType());
            conditions.in("s.status", filter.statuses());
            if (filter.asyncOnly()) conditions.equal("s.async_task", true);
            conditions.time("s.start_time", filter.startTime(), filter.endTime());
            String from = " FROM task_index s LEFT JOIN workflow_index w ON w.workflow_id = s.workflow_id" + conditions.sql;
            long total = select(tx, "SELECT COUNT(*)" + from, conditions.params, rs -> { rs.next(); return rs.getLong(1); });
            var params = new ArrayList<>(conditions.params);
            params.add(filter.pageSize()); params.add(filter.offset());
            List<ExecutionSearch.TaskSummary> rows = select(tx, "SELECT s.*, w.workflow_name, w.correlation_id" + from
                    + " ORDER BY s.start_time DESC, s.task_id LIMIT ? OFFSET ?", params, rs -> {
                List<ExecutionSearch.TaskSummary> result = new ArrayList<>();
                while (rs.next()) result.add(new ExecutionSearch.TaskSummary(rs.getString("task_id"),
                        rs.getString("workflow_id"), rs.getString("workflow_route"), rs.getString("node_ref"),
                        rs.getString("reference_task_name"), rs.getString("task_name"), rs.getString("task_type"),
                        rs.getObject("async_task") == null ? null : rs.getBoolean("async_task"), rs.getString("status"),
                        rs.getInt("retry_count"), rs.getLong("scheduled_time"), rs.getLong("actual_start_time"),
                        rs.getLong("end_time"), rs.getLong("update_time"), nullableLong(rs, "duration_ms"),
                        rs.getString("workflow_name"), rs.getString("correlation_id")));
                return result;
            });
            return new PageList<>(total, filter.pageNum(), filter.pageSize(), rows);
        });
    }

    private static final class Conditions {
        private final StringBuilder sql = new StringBuilder(" WHERE 1 = 1");
        private final List<Object> params = new ArrayList<>();
        void equal(String column, Object value) {
            if (value == null || value instanceof String text && text.isEmpty()) return;
            sql.append(" AND ").append(column).append(" = ?"); params.add(value);
        }
        void in(String column, List<String> values) {
            if (values.isEmpty()) return;
            sql.append(" AND ").append(column).append(" IN (")
                    .append(String.join(",", java.util.Collections.nCopies(values.size(), "?"))).append(")");
            params.addAll(values);
        }
        void time(String column, Long start, Long end) {
            if (start != null) { sql.append(" AND ").append(column).append(" >= ?"); params.add(start); }
            if (end != null) { sql.append(" AND ").append(column).append(" <= ?"); params.add(end); }
        }
    }

    @Override
    public ExecutionStatistics.Page workflows(ExecutionStatistics.Query query) { return aggregate(query, false); }

    @Override
    public ExecutionStatistics.Page tasks(ExecutionStatistics.Query query) { return aggregate(query, true); }

    @Override
    public ExecutionStatistics.RuntimeOverview overview(ExecutionStatistics.OverviewQuery filter) {
        long span = filter.getEndTime() - filter.getStartTime();
        String granularity = span < Duration.ofDays(2).toMillis() ? "hour"
                : span < Duration.ofDays(90).toMillis() ? "day" : "month";
        ZonedDateTime start = Instant.ofEpochMilli(filter.getStartTime()).atZone(filter.getTimeZone());
        ZonedDateTime bucket = switch (granularity) {
            case "hour" -> start.truncatedTo(ChronoUnit.HOURS);
            case "day" -> start.toLocalDate().atStartOfDay(filter.getTimeZone());
            default -> start.toLocalDate().withDayOfMonth(1).atStartOfDay(filter.getTimeZone());
        };
        List<Long> boundaries = new ArrayList<>();
        while (bucket.toInstant().toEpochMilli() <= filter.getEndTime()) {
            boundaries.add(bucket.toInstant().toEpochMilli());
            bucket = switch (granularity) {
                case "hour" -> bucket.plusHours(1);
                case "day" -> bucket.toLocalDate().plusDays(1).atStartOfDay(filter.getTimeZone());
                default -> bucket.toLocalDate().plusMonths(1).atStartOfDay(filter.getTimeZone());
            };
        }

        return getWithTransaction(tx -> {
            List<Object> params = new ArrayList<>(List.of(filter.getStartTime(), filter.getEndTime()));
            String where = " WHERE s.start_time >= ? AND s.start_time <= ?";
            if (!filter.getWorkflowRoute().isEmpty()) {
                where += " AND s.workflow_route = ?";
                params.add(filter.getWorkflowRoute());
            }
            var workflows = select(tx, "SELECT " + AGGREGATES + " FROM workflow_index s" + where, params, rs -> {
                rs.next();
                return metrics(rs);
            });
            var tasks = select(tx, "SELECT " + AGGREGATES + " FROM task_index s" + where, params, rs -> {
                rs.next();
                return metrics(rs);
            });
            long[] workflowCounts = trendCounts(tx, "workflow_index", where, params, boundaries);
            long[] taskCounts = trendCounts(tx, "task_index", where, params, boundaries);
            List<ExecutionStatistics.TrendPoint> trend = new ArrayList<>();
            for (int i = 0; i < boundaries.size(); i++) {
                trend.add(new ExecutionStatistics.TrendPoint(boundaries.get(i), workflowCounts[i], taskCounts[i]));
            }
            return new ExecutionStatistics.RuntimeOverview(workflows, tasks, granularity, trend);
        });
    }

    private long[] trendCounts(Connection tx, String table, String where, List<Object> filterParams,
            List<Long> boundaries) throws SQLException {
        // Java 计算浏览器时区的日历边界，避免依赖 MySQL 时区表；SQL 仅聚合计数，不读取实例报文。
        StringBuilder bucket = new StringBuilder();
        List<Object> params = new ArrayList<>();
        if (boundaries.size() == 1) {
            bucket.append('0');
        } else {
            bucket.append("CASE");
            for (int i = 1; i < boundaries.size(); i++) {
                bucket.append(" WHEN s.start_time < ? THEN ").append(i - 1);
                params.add(boundaries.get(i));
            }
            bucket.append(" ELSE ").append(boundaries.size() - 1).append(" END");
        }
        params.addAll(filterParams);
        return select(tx, "SELECT " + bucket + " AS time_bucket, COUNT(*) AS calls FROM " + table
                + " s" + where + " GROUP BY time_bucket", params, rs -> {
            long[] counts = new long[boundaries.size()];
            while (rs.next()) counts[rs.getInt("time_bucket")] = rs.getLong("calls");
            return counts;
        });
    }

    private ExecutionStatistics.Page aggregate(ExecutionStatistics.Query filter, boolean tasks) {
        return getWithTransaction(tx -> {
            List<Object> params = parameters(filter);
            String from = tasks ? " FROM task_index s LEFT JOIN workflow_index w ON w.workflow_id = s.workflow_id"
                    : " FROM workflow_index s";
            String where = where(filter, tasks);
            String group = "s.workflow_route" + (tasks ? ", s.node_ref, s.task_type" : "");
            var summary = select(tx, "SELECT " + AGGREGATES + from + where, params, rs -> {
                rs.next();
                return metrics(rs);
            });
            long total = select(tx, "SELECT COUNT(*) FROM (SELECT " + group + from + where
                    + " GROUP BY " + group + ") grouped_stats", params, rs -> { rs.next(); return rs.getLong(1); });
            String identity = tasks ? "s.workflow_route, MAX(w.workflow_name) AS workflow_name, s.node_ref, "
                    + "MAX(s.task_name) AS task_name, s.task_type, "
                    : "s.workflow_route, MAX(s.workflow_name) AS workflow_name, ";
            List<Object> pageParams = new ArrayList<>(params);
            pageParams.add(filter.pageSize());
            pageParams.add(filter.offset());
            // 所有动态标识符来自固定白名单，空耗时排在最后。
            String order = SORTS.get(filter.sort());
            var rows = select(tx, "SELECT * FROM (SELECT " + identity + AGGREGATES + from + where + " GROUP BY " + group + ") ranked"
                    + " ORDER BY (" + order + " IS NULL), " + order + (filter.descending() ? " DESC" : " ASC")
                    + ", workflow_route" + (tasks ? ", node_ref, task_type" : "") + " LIMIT ? OFFSET ?", pageParams, rs -> {
                List<ExecutionStatistics.Row> result = new ArrayList<>();
                while (rs.next()) result.add(new ExecutionStatistics.Row(rs.getString("workflow_route"),
                        rs.getString("workflow_name"), tasks ? rs.getString("node_ref") : null,
                        tasks ? rs.getString("task_name") : null, tasks ? rs.getString("task_type") : null, metrics(rs)));
                return result;
            });
            return new ExecutionStatistics.Page(summary, rows, total, filter.pageNum(), filter.pageSize());
        });
    }

    @Override
    public ExecutionStatistics.Instances taskInstances(ExecutionStatistics.Query filter, String nodeRef, String taskType) {
        return getWithTransaction(tx -> {
            List<Object> params = parameters(filter);
            params.add(nodeRef);
            params.add(taskType);
            String from = " FROM task_index s" + where(filter, true) + " AND s.node_ref = ? AND s.task_type = ?";
            long total = select(tx, "SELECT COUNT(*)" + from, params, rs -> { rs.next(); return rs.getLong(1); });
            List<Object> pageParams = new ArrayList<>(params);
            pageParams.add(filter.pageSize());
            pageParams.add(filter.offset());
            var rows = select(tx, "SELECT s.*" + from + " ORDER BY s.start_time DESC, s.task_id LIMIT ? OFFSET ?",
                    pageParams, rs -> {
                List<ExecutionStatistics.TaskInstance> result = new ArrayList<>();
                while (rs.next()) result.add(new ExecutionStatistics.TaskInstance(rs.getString("task_id"),
                        rs.getString("workflow_id"), rs.getString("reference_task_name"), rs.getString("status"),
                        rs.getInt("retry_count"), rs.getLong("start_time"), rs.getLong("end_time"), nullableLong(rs, "duration_ms")));
                return result;
            });
            return new ExecutionStatistics.Instances(rows, total, filter.pageNum(), filter.pageSize());
        });
    }

    private static String where(ExecutionStatistics.Query filter, boolean tasks) {
        return " WHERE s.start_time >= ? AND s.start_time <= ?"
                + (filter.workflowRoute().isEmpty() ? "" : " AND s.workflow_route = ?")
                + (tasks && filter.asyncOnly() ? " AND s.async_task = TRUE" : "");
    }

    private static List<Object> parameters(ExecutionStatistics.Query filter) {
        List<Object> params = new ArrayList<>(List.of(filter.startTime(), filter.endTime()));
        if (!filter.workflowRoute().isEmpty()) params.add(filter.workflowRoute());
        return params;
    }

    private static ExecutionStatistics.Metrics metrics(ResultSet rs) throws SQLException {
        long successful = rs.getLong("success_count");
        long failed = rs.getLong("failure_count");
        Number avg = (Number) rs.getObject("avg_duration_ms");
        return new ExecutionStatistics.Metrics(rs.getLong("total_count"), rs.getLong("running_count"), successful,
                failed, rs.getLong("duration_count"), avg == null ? null : avg.doubleValue(),
                nullableLong(rs, "min_duration_ms"), nullableLong(rs, "max_duration_ms"),
                successful + failed == 0 ? null : (double) successful / (successful + failed));
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        Number value = (Number) rs.getObject(column);
        return value == null ? null : value.longValue();
    }

    private static Long duration(boolean terminal, long start, long end) {
        return terminal && start > 0 && end > 0 && end >= start ? end - start : null;
    }

    private static String upsert(String... columns) {
        // 对照 Conductor：拒绝较旧的摘要覆盖新状态，update_time 必须最后赋值。
        return " ON DUPLICATE KEY UPDATE " + java.util.Arrays.stream(columns)
                .filter(column -> !column.equals("update_time"))
                .map(column -> column + " = CASE WHEN VALUES(update_time) >= update_time THEN VALUES("
                        + column + ") ELSE " + column + " END")
                .collect(java.util.stream.Collectors.joining(", "))
                + ", update_time = CASE WHEN VALUES(update_time) >= update_time THEN VALUES(update_time) ELSE update_time END";
    }

    private void execute(Connection tx, String sql, Object... params) {
        try (PreparedStatement statement = tx.prepareStatement(sql)) {
            bind(statement, java.util.Arrays.asList(params));
            statement.executeUpdate();
        } catch (SQLException error) {
            throw new IllegalStateException("执行统计索引更新失败", error);
        }
    }

    private <T> T select(Connection tx, String sql, List<Object> params, Reader<T> reader) throws SQLException {
        try (PreparedStatement statement = tx.prepareStatement(sql)) {
            bind(statement, params);
            try (ResultSet rs = statement.executeQuery()) { return reader.read(rs); }
        }
    }

    private static void bind(PreparedStatement statement, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) statement.setObject(i + 1, params.get(i));
    }

    @FunctionalInterface
    private interface Reader<T> { T read(ResultSet rs) throws SQLException; }
}
