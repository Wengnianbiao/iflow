package com.helianhealth.iflow.web.mysql.dao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.iflow.common.domain.metadata.task.TaskDef;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowDef;
import com.helianhealth.iflow.common.exception.WorkFlowConfigException;
import com.helianhealth.iflow.common.exception.WorkFlowErrorCode;
import com.helianhealth.iflow.core.dao.MetadataDAO;
import com.helianhealth.iflow.web.mysql.config.ConditionalOnMySqlRepository;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * MySQL {@link MetadataDAO}（WorkflowDef CRUD）。
 *
 * <p>读写均以 {@code json_data} 序列化整份 {@link WorkflowDef}；{@code latest_version} 为索引投影列。
 */
@Repository
@ConditionalOnMySqlRepository
public class MySqlMetadataDAO extends MySqlBaseDAO implements MetadataDAO {

    public MySqlMetadataDAO(
            DataSource dataSource,
            ObjectMapper objectMapper) {
        super(objectMapper, dataSource);
    }

    @Override
    public TaskDef createTaskDef(TaskDef taskDef) {
        throw new UnsupportedOperationException("TaskDef registry not implemented yet");
    }

    @Override
    public TaskDef updateTaskDef(TaskDef taskDef) {
        throw new UnsupportedOperationException("TaskDef registry not implemented yet");
    }

    @Override
    public Optional<TaskDef> getTaskDef(String name) {
        return Optional.empty();
    }

    @Override
    public List<TaskDef> getAllTaskDefs() {
        return List.of();
    }

    @Override
    public void removeTaskDef(String name) {
        throw new UnsupportedOperationException("TaskDef registry not implemented yet");
    }

    @Override
    public void createWorkflowDef(WorkflowDef def) {
        validate(def);
        withTransaction(
                tx -> {
                    if (workflowExists(tx, def)) {
                        throw new WorkFlowConfigException(
                                WorkFlowErrorCode.REPOSITORY_SAVE_ERROR,
                                "工作流定义已存在: " + def.key());
                    }
                    insertWorkflowDef(tx, def);
                });
    }

    @Override
    public void updateWorkflowDef(WorkflowDef def) {
        validate(def);
        withTransaction(tx -> {
            if (!workflowExists(tx, def)) {
                throw new WorkFlowConfigException(
                        WorkFlowErrorCode.WORKFLOW_NOT_FOUND, "工作流定义不存在: " + def.key());
            }
            final String sql = "UPDATE meta_workflow_def "
                    + "SET json_data = ?, modified_on = CURRENT_TIMESTAMP "
                    + "WHERE route = ? AND version = ?";
            execute(tx, sql, q -> {
                if (q.addJsonParameter(def).addParameter(def.getRoute())
                        .addParameter(def.getVersion()).executeUpdate() == 0) {
                    throw new WorkFlowConfigException(
                            WorkFlowErrorCode.WORKFLOW_NOT_FOUND, "工作流定义不存在: " + def.key());
                }
            });
        });
    }

    @Override
    public Optional<WorkflowDef> getWorkflowDef(String route, int version) {
        final String sql =
                "SELECT json_data FROM meta_workflow_def WHERE route = ? AND version = ?";
        return Optional.ofNullable(
                queryWithTransaction(
                        sql,
                        q -> q.addParameter(route)
                                .addParameter(version)
                                .executeAndFetchFirst(WorkflowDef.class)));
    }

    @Override
    public Optional<WorkflowDef> getLatestWorkflowDef(String route) {
        final String sql =
                "SELECT json_data FROM meta_workflow_def WHERE route = ? AND version = latest_version";
        return Optional.ofNullable(
                queryWithTransaction(
                        sql, q -> q.addParameter(route).executeAndFetchFirst(WorkflowDef.class)));
    }

    @Override
    public List<WorkflowDef> getAllVersions(String route) {
        final String sql =
                "SELECT json_data FROM meta_workflow_def WHERE route = ? ORDER BY version";
        return queryWithTransaction(
                sql, q -> q.addParameter(route).executeAndFetch(WorkflowDef.class));
    }

    @Override
    public List<WorkflowDef> getAllLatest() {
        final String sql =
                "SELECT json_data, created_on, modified_on "
                        + "FROM meta_workflow_def WHERE version = latest_version ORDER BY route";
        return queryWithTransaction(
                sql,
                q ->
                        q.executeAndFetch(
                                rs -> {
                                    List<WorkflowDef> definitions = new ArrayList<>();
                                    while (rs.next()) {
                                        WorkflowDef definition =
                                                readValue(rs.getString("json_data"), WorkflowDef.class);
                                        Timestamp createdOn = rs.getTimestamp("created_on");
                                        Timestamp modifiedOn = rs.getTimestamp("modified_on");
                                        definition.setCreateTime(
                                                createdOn == null ? null : createdOn.getTime());
                                        definition.setUpdateTime(
                                                modifiedOn == null ? null : modifiedOn.getTime());
                                        definitions.add(definition);
                                    }
                                    return definitions;
                                }));
    }

    @Override
    public void removeWorkflowDef(String route, int version) {
        final String deleteSql =
                "DELETE FROM meta_workflow_def WHERE route = ? AND version = ?";
        withTransaction(
                tx -> {
                    execute(
                            tx,
                            deleteSql,
                            q -> {
                                if (!q.addParameter(route).addParameter(version).executeDelete()) {
                                    throw new WorkFlowConfigException(
                                            WorkFlowErrorCode.WORKFLOW_NOT_FOUND,
                                            "工作流定义不存在: "
                                                    + WorkflowDef.getKey(route, version));
                                }
                            });
                    getLatestVersion(tx, route)
                            .ifPresent(maxVersion -> updateLatestVersion(tx, route, maxVersion));
                });
    }

    private boolean workflowExists(Connection connection, WorkflowDef def) {
        final String sql =
                "SELECT COUNT(*) FROM meta_workflow_def WHERE route = ? AND version = ?";
        return query(
                connection,
                sql,
                q -> q.addParameter(def.getRoute()).addParameter(def.getVersion()).exists());
    }

    private Optional<Integer> getLatestVersion(Connection tx, String route) {
        final String sql = "SELECT MAX(version) AS version FROM meta_workflow_def WHERE route = ?";
        Integer value =
                query(
                        tx,
                        sql,
                        q -> {
                            q.addParameter(route);
                            return q.executeAndFetch(
                                    rs -> {
                                        if (!rs.next()) {
                                            return null;
                                        }
                                        int version = rs.getInt(1);
                                        return rs.wasNull() ? null : version;
                                    });
                        });
        return Optional.ofNullable(value);
    }

    private void updateLatestVersion(Connection tx, String route, int version) {
        final String sql = "UPDATE meta_workflow_def SET latest_version = ? WHERE route = ?";
        execute(
                tx,
                sql,
                q -> q.addParameter(version).addParameter(route).executeUpdate());
    }

    private void insertWorkflowDef(Connection tx, WorkflowDef def) {
        final String sql =
                "INSERT INTO meta_workflow_def (route, version, json_data) VALUES (?, ?, ?)";
        execute(tx, sql, q -> q.addParameter(def.getRoute())
                .addParameter(def.getVersion()).addJsonParameter(def).executeUpdate());
        int latestVersion = getLatestVersion(tx, def.getRoute()).orElse(def.getVersion());
        updateLatestVersion(tx, def.getRoute(), Math.max(latestVersion, def.getVersion()));
    }

    private void validate(WorkflowDef def) {
        if (def == null || def.getRoute() == null || def.getRoute().trim().isEmpty()) {
            throw new WorkFlowConfigException(
                    WorkFlowErrorCode.REPOSITORY_SAVE_ERROR, "工作流定义 route 不能为空");
        }
        def.setRoute(def.getRoute().trim());
        if (!WorkflowDef.isValidRoute(def.getRoute())) {
            throw new WorkFlowConfigException(
                    WorkFlowErrorCode.REPOSITORY_SAVE_ERROR,
                    "工作流定义 route 格式非法（字母或数字开头，仅含 A-Za-z0-9._/-）: "
                            + def.getRoute());
        }
        if (def.getName() == null || def.getName().isBlank()) {
            throw new WorkFlowConfigException(
                    WorkFlowErrorCode.REPOSITORY_SAVE_ERROR, "工作流名称 name 不能为空");
        }
        def.setName(def.getName().trim());
        if (def.getVersion() <= 0) {
            throw new WorkFlowConfigException(
                    WorkFlowErrorCode.REPOSITORY_SAVE_ERROR,
                    "工作流定义 version 必须为正整数: " + def.getRoute());
        }
    }
}
