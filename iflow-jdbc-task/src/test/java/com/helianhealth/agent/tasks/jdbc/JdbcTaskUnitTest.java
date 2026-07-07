package com.helianhealth.agent.tasks.jdbc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.agent.core.model.TaskModel;
import com.helianhealth.agent.core.model.WorkflowModel;
import com.helianhealth.agent.tasks.jdbc.providers.DefaultJdbcConnectionProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JdbcTask 单元测试（对齐 HttpTask：inputParameters → Input 反序列化）。
 *
 * <p>覆盖 Input 反序列化、jdbc_request 键 / 平铺 input 两种形态。
 */
class JdbcTaskUnitTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private JdbcTask jdbcTask;
    private final WorkflowModel workflow = new WorkflowModel();

    @BeforeEach
    void setup() {
        jdbcTask = new JdbcTask(new DefaultJdbcConnectionProvider(3000, 20000), OBJECT_MAPPER);
    }

    @Test
    void parseFlatInput() {
        Map<String, Object> inputMap = new HashMap<>();
        inputMap.put("url", "jdbc:mysql://localhost:3306/demo");
        inputMap.put("username", "root");
        inputMap.put("password", "secret");
        inputMap.put("vendor", "MYSQL");
        inputMap.put("sql", "SELECT :id");
        inputMap.put("operation", "query");
        inputMap.put("connectionTimeOut", 1000);
        inputMap.put("queryTimeOut", 5000);
        inputMap.put("parameters", Map.of("id", 1));

        JdbcTask.Input parsed = OBJECT_MAPPER.convertValue(inputMap, JdbcTask.Input.class);

        assertNotNull(parsed);
        assertEquals("jdbc:mysql://localhost:3306/demo", parsed.getUrl());
        assertEquals("root", parsed.getUsername());
        assertEquals("secret", parsed.getPassword());
        assertEquals(JdbcVendor.MYSQL, parsed.getVendor());
        assertEquals("SELECT :id", parsed.getSql());
        assertEquals(JdbcTask.Operation.QUERY, parsed.getOperation());
        assertEquals(1000, parsed.getConnectionTimeOut());
        assertEquals(5000, parsed.getQueryTimeOut());
        assertEquals(1, parsed.getParameters().get("id"));
    }

    @Test
    void parseNestedJdbcRequest() {
        Map<String, Object> nested = new HashMap<>();
        nested.put("url", "jdbc:postgresql://localhost/demo");
        nested.put("driverClassName", "org.postgresql.Driver");
        nested.put("sql", "UPDATE t SET v = :v");
        nested.put("operation", "update");

        TaskModel task = new TaskModel();
        task.setInputData(Map.of(JdbcTask.REQUEST_PARAMETER_NAME, nested));

        Object request = task.getInputData().get(JdbcTask.REQUEST_PARAMETER_NAME);
        JdbcTask.Input parsed = OBJECT_MAPPER.convertValue(request, JdbcTask.Input.class);

        assertEquals("jdbc:postgresql://localhost/demo", parsed.getUrl());
        assertEquals("org.postgresql.Driver", parsed.getDriverClassName());
        assertEquals(JdbcTask.Operation.UPDATE, parsed.getOperation());
    }

    @Test
    void startFailsWhenMissingUrl() {
        TaskModel task = new TaskModel();
        task.setInputData(Map.of("sql", "SELECT 1", "vendor", "MYSQL"));

        jdbcTask.execute(workflow, task, null);

        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertTrue(task.getReasonForInCompletion().contains("Missing JDBC URL"));
    }

    @Test
    void startFailsWhenEmptyInput() {
        TaskModel task = new TaskModel();
        task.setInputData(Map.of());

        jdbcTask.execute(workflow, task, null);

        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertTrue(task.getReasonForInCompletion().contains("Missing JDBC URL"));
    }

    @Test
    void startFailsWhenMissingDriver() {
        TaskModel task = new TaskModel();
        Map<String, Object> input = new HashMap<>();
        input.put("url", "jdbc:mysql://localhost:3306/demo");
        input.put("sql", "SELECT 1");
        task.setInputData(input);

        jdbcTask.execute(workflow, task, null);

        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertTrue(task.getReasonForInCompletion().contains("Missing driverClassName or vendor"));
    }

    @Test
    void vendorResolvesDriverClassName() {
        assertEquals("com.mysql.cj.jdbc.Driver",
                JdbcVendor.resolveDriverClassName(null, JdbcVendor.MYSQL));
        assertEquals("custom.Driver",
                JdbcVendor.resolveDriverClassName("custom.Driver", JdbcVendor.MYSQL));
    }

    @Test
    void queryTimeoutSecondsRoundsUp() {
        assertEquals(1, JdbcConnectionUtils.toQueryTimeoutSeconds(1));
        assertEquals(3, JdbcConnectionUtils.toQueryTimeoutSeconds(2500));
    }

    @Test
    void convertEmptyMapYieldsNullFields() {
        JdbcTask.Input parsed = OBJECT_MAPPER.convertValue(Map.of(), JdbcTask.Input.class);
        assertNotNull(parsed);
        assertNull(parsed.getUrl());
        assertNull(parsed.getSql());
    }
}
