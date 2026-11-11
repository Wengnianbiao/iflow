package com.helianhealth.iflow.persistence.mysql.dao;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowSchedule;
import com.helianhealth.iflow.common.domain.run.StartWorkflowRequest;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class MySqlWorkflowScheduleDAO extends MySqlBaseDAO {

    private static final String SELECT_SCHEDULE =
            "SELECT name, display_name, cron_expression, zone_id, enabled, workflow_route, workflow_version, "
                    + "input_json, next_run_at FROM workflow_schedule";
    private static final TypeReference<Map<String, Object>> INPUT_TYPE = new TypeReference<>() {};

    public MySqlWorkflowScheduleDAO(DataSource dataSource, ObjectMapper objectMapper) {
        super(objectMapper, dataSource);
    }

    public WorkflowSchedule create(WorkflowSchedule schedule) {
        String sql = """
                INSERT INTO workflow_schedule
                    (name, display_name, cron_expression, zone_id, enabled, workflow_route, workflow_version,
                     input_json, next_run_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        return getWithTransaction(tx -> {
            try (PreparedStatement statement = tx.prepareStatement(sql)) {
                bindConfiguration(statement, schedule);
                statement.executeUpdate();
                return schedule;
            } catch (SQLException ex) {
                throw duplicateKeyOrRethrow(ex);
            }
        });
    }

    public Optional<WorkflowSchedule> findByName(String name) {
        WorkflowSchedule schedule = queryWithTransaction(
                SELECT_SCHEDULE + " WHERE name = ?",
                q -> q.addParameter(name).executeAndFetch(rs -> rs.next() ? readSchedule(rs) : null));
        return Optional.ofNullable(schedule);
    }

    public List<WorkflowSchedule> findAll() {
        return queryWithTransaction(
                SELECT_SCHEDULE + " ORDER BY name", q -> q.executeAndFetch(this::readSchedules));
    }

    public List<WorkflowSchedule> findDue(Instant now, int limit) {
        return queryWithTransaction(
                SELECT_SCHEDULE + " WHERE enabled = TRUE AND next_run_at <= ? ORDER BY next_run_at, name LIMIT ?",
                q -> q.addParameter(now.toEpochMilli()).addParameter(limit).executeAndFetch(this::readSchedules));
    }

    public Optional<WorkflowSchedule> update(WorkflowSchedule current, WorkflowSchedule replacement) {
        String sql = """
                UPDATE workflow_schedule
                SET name = ?, display_name = ?, cron_expression = ?, zone_id = ?, enabled = ?, workflow_route = ?,
                    workflow_version = ?, input_json = ?, next_run_at = ?
                WHERE name = ?
                    AND (next_run_at = ? OR (next_run_at IS NULL AND ? IS NULL))
                """;
        return getWithTransaction(tx -> {
            try (PreparedStatement statement = tx.prepareStatement(sql)) {
                bindConfiguration(statement, replacement);
                statement.setString(10, current.getName());
                Long expectedRunAt = epochMillis(current.getNextRunAt());
                statement.setObject(11, expectedRunAt, Types.BIGINT);
                statement.setObject(12, expectedRunAt, Types.BIGINT);
                if (statement.executeUpdate() == 0) {
                    return Optional.empty();
                }
                return Optional.of(replacement);
            } catch (SQLException ex) {
                throw duplicateKeyOrRethrow(ex);
            }
        });
    }

    public void delete(String name) {
        executeWithTransaction(
                "DELETE FROM workflow_schedule WHERE name = ?", q -> q.addParameter(name).executeUpdate());
    }

    public boolean advance(WorkflowSchedule current, Instant nextRunAt) {
        String sql = """
                UPDATE workflow_schedule SET next_run_at = ?
                WHERE name = ? AND enabled = TRUE AND next_run_at = ?
                    AND cron_expression = ? AND zone_id = ? AND workflow_route = ?
                    AND (workflow_version = ? OR (workflow_version IS NULL AND ? IS NULL))
                    AND input_json = ?
                """;
        StartWorkflowRequest request = current.getStartWorkflowRequest();
        return queryWithTransaction(sql, q -> q.addParameter(nextRunAt.toEpochMilli())
                .addParameter(current.getName())
                .addParameter(current.getNextRunAt().toEpochMilli())
                .addParameter(current.getCronExpression())
                .addParameter(current.getZoneId())
                .addParameter(request.getRoute())
                .addParameter(request.getVersion())
                .addParameter(request.getVersion())
                .addParameter(toJson(request.getInput()))
                .executeUpdate() == 1);
    }

    private void bindConfiguration(PreparedStatement statement, WorkflowSchedule schedule) throws SQLException {
        StartWorkflowRequest request = schedule.getStartWorkflowRequest();
        statement.setString(1, schedule.getName());
        statement.setString(2, schedule.getDisplayName());
        statement.setString(3, schedule.getCronExpression());
        statement.setString(4, schedule.getZoneId());
        statement.setBoolean(5, schedule.getEnabled());
        statement.setString(6, request.getRoute());
        statement.setObject(7, request.getVersion(), Types.INTEGER);
        statement.setString(8, toJson(request.getInput()));
        statement.setObject(9, epochMillis(schedule.getNextRunAt()), Types.BIGINT);
    }

    private List<WorkflowSchedule> readSchedules(ResultSet rs) throws SQLException {
        List<WorkflowSchedule> schedules = new ArrayList<>();
        while (rs.next()) {
            schedules.add(readSchedule(rs));
        }
        return schedules;
    }

    private WorkflowSchedule readSchedule(ResultSet rs) throws SQLException {
        Long nextRunAt = rs.getObject("next_run_at", Long.class);
        return new WorkflowSchedule(
                rs.getString("name"), rs.getString("display_name"), rs.getString("cron_expression"),
                rs.getString("zone_id"), rs.getBoolean("enabled"),
                new StartWorkflowRequest(
                        rs.getString("workflow_route"), rs.getObject("workflow_version", Integer.class),
                        readInput(rs.getString("input_json"))),
                nextRunAt == null ? null : Instant.ofEpochMilli(nextRunAt));
    }

    private Map<String, Object> readInput(String json) {
        try {
            return objectMapper.readValue(json, INPUT_TYPE);
        } catch (IOException ex) {
            throw new IllegalStateException("定时计划入参读取失败", ex);
        }
    }

    private static SQLException duplicateKeyOrRethrow(SQLException ex) {
        if (isDuplicateKey(ex)) {
            throw new IllegalArgumentException("计划名称已存在", ex);
        }
        return ex;
    }

    private static boolean isDuplicateKey(SQLException ex) {
        for (SQLException current = ex; current != null; current = current.getNextException()) {
            if (duplicateKey(current)) {
                return true;
            }
        }
        for (Throwable current = ex; current != null; current = current.getCause()) {
            if (current instanceof SQLException sql && duplicateKey(sql)) {
                return true;
            }
        }
        return false;
    }

    private static boolean duplicateKey(SQLException ex) {
        String state = ex.getSQLState();
        if (ex.getErrorCode() == 1062 || "23000".equals(state) || "23505".equals(state)) {
            return true;
        }
        String message = ex.getMessage();
        return message != null && (message.contains("Duplicate entry")
                || message.contains("Unique index or primary key violation"));
    }

    private static Long epochMillis(Instant time) {
        return time == null ? null : time.toEpochMilli();
    }
}
