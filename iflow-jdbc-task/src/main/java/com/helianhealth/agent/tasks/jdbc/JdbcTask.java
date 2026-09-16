package com.helianhealth.agent.tasks.jdbc;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.DeserializationFeature;
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
 * <p>从平铺的 {@link TaskModel#getInputData()} 解析 {@link Input}，通过
 * {@link JdbcConnectionProvider} 每次独立建连执行 SQL（不做连接池化）。
 *
 * <p>表达式分两层：编排层 {@code ${...}} 由 {@link com.helianhealth.agent.core.execution.mapper.JdbcTaskMapper}
 * + {@link com.helianhealth.agent.core.utils.ParametersUtils} 在调度阶段解析，把上游业务数据搬进
 * {@code payload}（固定的 Key / Value 对象）；执行阶段由 {@link SqlTemplate} 解析
 * {@code sqlTemplate} 中的 {@code $.字段}，从 payload 取值并改写成绑定参数。
 *
 * <p>支持 {@link Operation#QUERY} 与 {@link Operation#UPDATE}。
 * 执行结果写入 {@code outputData} 根级（{@code rows}/{@code rowCount}/{@code updateCount}）。
 */
@Component(TaskType.TASK_TYPE_JDBC)
public class JdbcTask extends WorkflowSystemTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(JdbcTask.class);

    protected ObjectMapper objectMapper;
    protected JdbcConnectionProvider jdbcConnectionProvider;

    @Autowired
    public JdbcTask(JdbcConnectionProvider jdbcConnectionProvider, ObjectMapper objectMapper) {
        this(TaskType.TASK_TYPE_JDBC, jdbcConnectionProvider, objectMapper);
    }

    protected JdbcTask(String name, JdbcConnectionProvider jdbcConnectionProvider, ObjectMapper objectMapper) {
        super(name);
        this.jdbcConnectionProvider = jdbcConnectionProvider;
        this.objectMapper = objectMapper.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        LOGGER.info("{} initialized...", getTaskType());
    }

    @Override
    public void execute(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
        Input input;
        try {
            input = objectMapper.convertValue(task.getInputData(), Input.class);
        } catch (IllegalArgumentException e) {
            task.setReasonForInCompletion("Invalid JDBC inputParameters: " + e.getMessage());
            task.setStatus(TaskModel.Status.FAILED);
            return;
        }
        if (input == null || input.getUrl() == null || input.getUrl().isBlank()) {
            task.setReasonForInCompletion(
                    "Missing JDBC URL. See documentation for JdbcTask for required input parameters");
            task.setStatus(TaskModel.Status.FAILED);
            return;
        }
        if (input.getSqlTemplate() == null || input.getSqlTemplate().isBlank()) {
            task.setReasonForInCompletion("Missing sqlTemplate");
            task.setStatus(TaskModel.Status.FAILED);
            return;
        }
        if (input.getVendor() == null) {
            task.setReasonForInCompletion("Missing vendor");
            task.setStatus(TaskModel.Status.FAILED);
            return;
        }
        if (input.getPayload() == null) {
            task.setReasonForInCompletion("Missing payload");
            task.setStatus(TaskModel.Status.FAILED);
            return;
        }

        SqlTemplate.Bound bound;
        try {
            bound = SqlTemplate.bind(input.getSqlTemplate(), input.getVendor(), input.getPayload());
        } catch (IllegalArgumentException e) {
            task.setReasonForInCompletion(e.getMessage());
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

            MapSqlParameterSource params = new MapSqlParameterSource(bound.parameters());

            Operation op = input.getOperation() == null ? Operation.QUERY : input.getOperation();
            JdbcResponse response = new JdbcResponse();
            switch (op) {
                case QUERY -> {
                    List<Map<String, Object>> rows = jdbc.queryForList(bound.sql(), params);
                    response.rows = rows;
                    response.rowCount = rows.size();
                }
                case UPDATE -> response.updateCount = jdbc.update(bound.sql(), params);
            }
            task.setOutputData(response.asMap());
            task.setStatus(TaskModel.Status.COMPLETED);
        } catch (Exception e) {
            LOGGER.error(
                    "Failed to invoke {} task: {} - sql: {} in workflow: {}",
                    getTaskType(),
                    task.getTaskId(),
                    input.getSqlTemplate(),
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

    public static class Input {

        private String url;

        private String username;
        private String password;

        /** 数据库厂商；驱动类名由 {@link JdbcVendor} 内部映射 */
        private JdbcVendor vendor;

        /** 建连超时（毫秒），未指定时使用系统默认 3000ms */
        private Integer connectionTimeOut;

        /** SQL 执行超时（毫秒），未指定时使用系统默认 20000ms */
        private Integer queryTimeOut;

        /** SQL 模板；{@code $.字段} 读取 payload，{@code IN $.codes} 自动补括号并展开数组。 */
        private String sqlTemplate;

        /** 操作类型，默认 QUERY */
        private Operation operation;

        /** SQL 模板参数；键对应 {@code $.字段}，值由编排层解析并保持原始 JSON 类型。 */
        private Map<String, Object> payload;

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

        public String getSqlTemplate() {
            return sqlTemplate;
        }

        public void setSqlTemplate(String sqlTemplate) {
            this.sqlTemplate = sqlTemplate;
        }

        public Operation getOperation() {
            return operation;
        }

        public void setOperation(Operation operation) {
            this.operation = operation;
        }

        public Map<String, Object> getPayload() {
            return payload;
        }

        public void setPayload(Map<String, Object> payload) {
            this.payload = payload;
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
