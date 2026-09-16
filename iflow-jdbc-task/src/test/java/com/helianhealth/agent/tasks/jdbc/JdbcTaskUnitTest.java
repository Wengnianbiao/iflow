package com.helianhealth.agent.tasks.jdbc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.agent.core.model.TaskModel;
import com.helianhealth.agent.core.model.WorkflowModel;
import com.helianhealth.agent.tasks.jdbc.providers.DefaultJdbcConnectionProvider;
import com.helianhealth.agent.tasks.jdbc.providers.JdbcConnectionProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JdbcTask 单元测试：平铺输入、payload 类型、校验与任务诊断。
 */
class JdbcTaskUnitTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private JdbcTask jdbcTask;
    private final WorkflowModel workflow = new WorkflowModel();

    @BeforeEach
    void setup() {
        jdbcTask = new JdbcTask(new DefaultJdbcConnectionProvider(), OBJECT_MAPPER);
    }

    @Test
    void parseFlatInput() {
        Map<String, Object> inputMap = new HashMap<>();
        inputMap.put("url", "jdbc:mysql://localhost:3306/demo");
        inputMap.put("username", "root");
        inputMap.put("password", "secret");
        inputMap.put("vendor", "MYSQL");
        inputMap.put("sqlTemplate", "SELECT $.id");
        inputMap.put("operation", "query");
        inputMap.put("connectionTimeOut", 1000);
        inputMap.put("queryTimeOut", 5000);
        inputMap.put("payload", Map.of("id", 1));

        JdbcTask.Input parsed = OBJECT_MAPPER.convertValue(inputMap, JdbcTask.Input.class);

        assertNotNull(parsed);
        assertEquals("jdbc:mysql://localhost:3306/demo", parsed.getUrl());
        assertEquals("root", parsed.getUsername());
        assertEquals("secret", parsed.getPassword());
        assertEquals(JdbcVendor.MYSQL, parsed.getVendor());
        assertEquals("SELECT $.id", parsed.getSqlTemplate());
        assertEquals(JdbcTask.Operation.QUERY, parsed.getOperation());
        assertEquals(1000, parsed.getConnectionTimeOut());
        assertEquals(5000, parsed.getQueryTimeOut());
        assertEquals(1, ((Map<?, ?>) parsed.getPayload()).get("id"));
    }

    @Test
    void rejectsOldContractEvenWhenApplicationMapperIgnoresUnknownProperties() {
        ObjectMapper lenient = OBJECT_MAPPER.copy().disable(
                com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        JdbcTask strictTask = new JdbcTask(new DefaultJdbcConnectionProvider(), lenient);
        for (String field : List.of("jdbc_request", "sql", "parameters")) {
            TaskModel task = new TaskModel();
            Map<String, Object> input = new HashMap<>();
            input.put("url", "jdbc:mysql://localhost:3306/demo");
            input.put("vendor", "MYSQL");
            input.put("sqlTemplate", "SELECT 1");
            input.put(field, Map.of());
            task.setInputData(input);
            strictTask.execute(workflow, task, null);
            assertEquals(TaskModel.Status.FAILED, task.getStatus());
            assertTrue(task.getReasonForInCompletion().contains(field));
        }
    }

    @Test
    void rejectsPayloadThatIsNotAMap() {
        for (Object payload : List.of("${workflow.input.data}", List.of(1, 2))) {
            TaskModel task = new TaskModel();
            task.setInputData(Map.of(
                    "url", "jdbc:mysql://localhost:3306/demo",
                    "vendor", "MYSQL",
                    "sqlTemplate", "SELECT 1",
                    "payload", payload));

            jdbcTask.execute(workflow, task, null);

            assertEquals(TaskModel.Status.FAILED, task.getStatus());
            assertTrue(task.getReasonForInCompletion().contains("Invalid JDBC inputParameters"));
        }
    }

    @Test
    void startFailsWhenMissingSqlTemplate() {
        TaskModel task = new TaskModel();
        task.setInputData(Map.of("url", "jdbc:mysql://localhost/demo", "vendor", "MYSQL"));
        jdbcTask.execute(workflow, task, null);
        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertTrue(task.getReasonForInCompletion().contains("Missing sqlTemplate"));
    }

    @Test
    void startFailsWhenMissingUrl() {
        TaskModel task = new TaskModel();
        task.setInputData(Map.of("sqlTemplate", "SELECT 1", "vendor", "MYSQL"));

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
    void startFailsWhenMissingVendor() {
        TaskModel task = new TaskModel();
        Map<String, Object> input = new HashMap<>();
        input.put("url", "jdbc:mysql://localhost:3306/demo");
        input.put("sqlTemplate", "SELECT 1");
        task.setInputData(input);

        jdbcTask.execute(workflow, task, null);

        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertTrue(task.getReasonForInCompletion().contains("Missing vendor"));
    }

    @Test
    void startFailsWhenMissingPayload() {
        TaskModel task = new TaskModel();
        task.setInputData(Map.of(
                "url", "jdbc:mysql://localhost:3306/demo",
                "vendor", "MYSQL",
                "sqlTemplate", "SELECT 1"));

        jdbcTask.execute(workflow, task, null);

        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertTrue(task.getReasonForInCompletion().contains("Missing payload"));
    }

    @Test
    void startFailsBeforeConnectingWhenArrayIsEmpty() {
        JdbcConnectionProvider refusing = new JdbcConnectionProvider() {
            @Override
            public Connection openConnection(JdbcTask.Input input) throws SQLException {
                throw new AssertionError("must not open a connection when SQL binding fails");
            }

            @Override
            public int resolveConnectTimeoutMs(JdbcTask.Input input) {
                return 1000;
            }

            @Override
            public int resolveQueryTimeoutMs(JdbcTask.Input input) {
                return 1000;
            }
        };
        TaskModel task = new TaskModel();
        task.setInputData(
                Map.of(
                        "url", "jdbc:mysql://localhost:3306/demo",
                        "vendor", "MYSQL",
                        "sqlTemplate", "SELECT * FROM t WHERE id IN $.ids",
                        "payload", Map.of("ids", List.of())));

        new JdbcTask(refusing, OBJECT_MAPPER).execute(workflow, task, null);

        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertTrue(task.getReasonForInCompletion().contains("empty arrays"));
    }

    @Test
    void vendorResolvesDriverClassName() {
        assertEquals("com.mysql.cj.jdbc.Driver", JdbcVendor.resolveDriverClassName(JdbcVendor.MYSQL));
        assertEquals("org.postgresql.Driver", JdbcVendor.resolveDriverClassName(JdbcVendor.POSTGRES));
        assertEquals("com.microsoft.sqlserver.jdbc.SQLServerDriver",
                JdbcVendor.resolveDriverClassName(JdbcVendor.SQLSERVER));
        assertEquals("oracle.jdbc.OracleDriver", JdbcVendor.resolveDriverClassName(JdbcVendor.ORACLE));
        assertEquals("org.opengauss.Driver", JdbcVendor.resolveDriverClassName(JdbcVendor.OPENGAUSS));
        assertEquals("com.kingbase8.Driver", JdbcVendor.resolveDriverClassName(JdbcVendor.KINGBASE));
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
        assertNull(parsed.getSqlTemplate());
    }

    @Test
    void jdbcResponseDefinesRootOutputFields() {
        JdbcTask.JdbcResponse response = new JdbcTask.JdbcResponse();
        response.rows = List.of(Map.of("id", 1));
        response.rowCount = 1;

        Map<String, Object> output = response.asMap();

        assertEquals(response.rows, output.get("rows"));
        assertEquals(1, output.get("rowCount"));
        assertEquals(0, output.get("updateCount"));
        assertFalse(output.containsKey("result"));
    }

    @Test
    void executionFailureWritesDiagnostic() {
        JdbcConnectionProvider provider = new JdbcConnectionProvider() {
            @Override
            public Connection openConnection(JdbcTask.Input input) throws SQLException {
                throw new SQLException("database unavailable");
            }

            @Override
            public int resolveConnectTimeoutMs(JdbcTask.Input input) {
                return 1000;
            }

            @Override
            public int resolveQueryTimeoutMs(JdbcTask.Input input) {
                return 1000;
            }
        };
        JdbcTask failingTask = new JdbcTask(provider, OBJECT_MAPPER);
        TaskModel task = new TaskModel();
        task.setInputData(
                Map.of(
                        "url", "jdbc:mysql://localhost:3306/demo",
                        "vendor", "MYSQL",
                        "sqlTemplate", "SELECT 1",
                        "payload", Map.of()));

        failingTask.execute(workflow, task, null);

        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertTrue(task.getReasonForInCompletion().contains("database unavailable"));
        assertFalse(task.outputAsMap().containsKey("response"));
    }
}
