package com.helianhealth.agent.tasks.jdbc;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.agent.common.domain.metadata.task.TaskType;
import com.helianhealth.agent.core.execution.WorkflowExecutor;
import com.helianhealth.agent.core.execution.tasks.WorkflowSystemTask;
import com.helianhealth.agent.core.model.TaskModel;
import com.helianhealth.agent.core.model.WorkflowModel;
import com.helianhealth.agent.tasks.jdbc.providers.JdbcConnectionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JDBC 系统任务
 *
 * <p>结构与 {@link com.helianhealth.agent.tasks.http.HttpTask} 一致：从 {@link TaskModel#getInputData()}
 * 解析 {@link Input}（可嵌套在 {@code jdbc_request} 下，也可平铺在 inputData 根级），通过
 * {@link JdbcConnectionProvider} 每次独立建连执行 SQL（不做连接池化）。
 *
 * <p>{@code ${...}} 占位符由 {@link com.helianhealth.agent.core.execution.mapper.JdbcTaskMapper}
 * + {@link com.helianhealth.agent.core.utils.ParametersUtils} 在调度阶段解析，Task 只消费已解析入参。
 *
 * <p>支持 {@link Operation#QUERY} 与 {@link Operation#UPDATE}；SQL 使用 {@code :name} 命名参数。
 * 执行结果写入 {@code outputData} 根级（{@code rows}/{@code rowCount}/{@code updateCount}）。
 */
@Component(TaskType.TASK_TYPE_JDBC)
public class JdbcTask extends WorkflowSystemTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(JdbcTask.class);

    public static final String REQUEST_PARAMETER_NAME = "jdbc_request";

    static final String MISSING_REQUEST =
            "Missing JDBC request. Task input MUST have a '"
                    + REQUEST_PARAMETER_NAME
                    + "' key with JdbcTask.Input as value OR provide the input parameters directly. "
                    + "See documentation for JdbcTask for required input parameters";

    protected ObjectMapper objectMapper;
    protected JdbcConnectionProvider jdbcConnectionProvider;
    private final String requestParameter;

    @Autowired
    public JdbcTask(JdbcConnectionProvider jdbcConnectionProvider, ObjectMapper objectMapper) {
        this(TaskType.TASK_TYPE_JDBC, jdbcConnectionProvider, objectMapper);
    }

    protected JdbcTask(String name, JdbcConnectionProvider jdbcConnectionProvider, ObjectMapper objectMapper) {
        super(name);
        this.jdbcConnectionProvider = jdbcConnectionProvider;
        this.objectMapper = objectMapper;
        this.requestParameter = REQUEST_PARAMETER_NAME;
        LOGGER.info("{} initialized...", getTaskType());
    }

    @Override
    public void execute(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
        Object request = task.getInputData().get(requestParameter);
        if (request == null) {
            request = task.getInputData();
        }

        Input input = objectMapper.convertValue(request, Input.class);
        if (input.getUrl() == null || input.getUrl().isBlank()) {
            task.setReasonForInCompletion(
                    "Missing JDBC URL. See documentation for JdbcTask for required input parameters");
            task.setStatus(TaskModel.Status.FAILED);
            return;
        }
        if (input.getSql() == null || input.getSql().isBlank()) {
            task.setReasonForInCompletion("Missing SQL");
            task.setStatus(TaskModel.Status.FAILED);
            return;
        }
        if ((input.getDriverClassName() == null || input.getDriverClassName().isBlank())
                && input.getVendor() == null) {
            task.setReasonForInCompletion("Missing driverClassName or vendor");
            task.setStatus(TaskModel.Status.FAILED);
            return;
        }

        int queryTimeoutMs = jdbcConnectionProvider.resolveQueryTimeoutMs(input);
        int queryTimeoutSeconds = JdbcConnectionUtils.toQueryTimeoutSeconds(queryTimeoutMs);

        try (Connection connection = jdbcConnectionProvider.openConnection(input)) {
            // SingleConnectionDataSource 仅包装本次 Connection，供 Spring JDBC 使用；不做池化。
            SingleConnectionDataSource dataSource = new SingleConnectionDataSource(connection, true);
            NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(dataSource);
            jdbc.getJdbcTemplate().setQueryTimeout(queryTimeoutSeconds);

            MapSqlParameterSource params = new MapSqlParameterSource(
                    input.getParameters() == null ? Map.of() : input.getParameters());

            Operation op = input.getOperation() == null ? Operation.QUERY : input.getOperation();
            JdbcResponse response = new JdbcResponse();
            switch (op) {
                case QUERY -> {
                    List<Map<String, Object>> rows = jdbc.queryForList(input.getSql(), params);
                    response.rows = rows;
                    response.rowCount = rows.size();
                }
                case UPDATE -> {
                    int affected = jdbc.update(input.getSql(), params);
                    response.updateCount = affected;
                }
            }
            task.setOutputData(response.asMap());
            task.setStatus(TaskModel.Status.COMPLETED);
        } catch (Exception e) {
            LOGGER.error(
                    "Failed to invoke {} task: {} - sql: {} in workflow: {}",
                    getTaskType(),
                    task.getTaskId(),
                    input.getSql(),
                    task.getWorkflowInstanceId(),
                    e);
            task.setStatus(TaskModel.Status.FAILED);
            task.setReasonForInCompletion("Failed to invoke " + getTaskType() + " task due to: " + e);
            addError(task, e.toString());
        }
    }

    @Override
    public void cancel(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
        task.setStatus(TaskModel.Status.CANCELED);
    }

    public enum Operation {
        QUERY,
        UPDATE;

        @JsonCreator
        public static Operation from(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            return Operation.valueOf(value.trim().toUpperCase());
        }
    }

    /**
     * JDBC 任务入参（连接信息 + SQL）。由 workflow 定义的 {@code inputParameters} 经占位符解析后映射而来；
     * 每次调用独立建连，不做池化。
     */
    public static class Input {

        /** JDBC URL */
        private String url;

        private String username;
        private String password;

        /** 显式驱动类名；与 {@link #vendor} 二选一 */
        private String driverClassName;

        /** 厂商枚举，用于解析默认驱动类名 */
        private JdbcVendor vendor;

        /** 建连超时（毫秒），未指定时使用 {@code iflow.tasks.jdbc.connectTimeout} */
        private Integer connectionTimeOut;

        /** SQL 执行超时（毫秒），未指定时使用 {@code iflow.tasks.jdbc.queryTimeout} */
        private Integer queryTimeOut;

        /** SQL（支持 {@code :name} 命名参数） */
        private String sql;

        /** 操作类型，默认 QUERY */
        private Operation operation;

        private Map<String, Object> parameters = new HashMap<>();

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getDriverClassName() {
            return driverClassName;
        }

        public void setDriverClassName(String driverClassName) {
            this.driverClassName = driverClassName;
        }

        public JdbcVendor getVendor() {
            return vendor;
        }

        public void setVendor(JdbcVendor vendor) {
            this.vendor = vendor;
        }

        public Integer getConnectionTimeOut() {
            return connectionTimeOut;
        }

        public void setConnectionTimeOut(Integer connectionTimeOut) {
            this.connectionTimeOut = connectionTimeOut;
        }

        public Integer getQueryTimeOut() {
            return queryTimeOut;
        }

        public void setQueryTimeOut(Integer queryTimeOut) {
            this.queryTimeOut = queryTimeOut;
        }

        public String getSql() {
            return sql;
        }

        public void setSql(String sql) {
            this.sql = sql;
        }

        public Operation getOperation() {
            return operation;
        }

        public void setOperation(Operation operation) {
            this.operation = operation;
        }

        public Map<String, Object> getParameters() {
            return parameters;
        }

        public void setParameters(Map<String, Object> parameters) {
            this.parameters = parameters;
        }
    }

    public static class JdbcResponse {

        public List<Map<String, Object>> rows;
        public int rowCount;
        public int updateCount;

        public Map<String, Object> asMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("rows", rows);
            map.put("rowCount", rowCount);
            map.put("updateCount", updateCount);
            return map;
        }
    }
}
