package com.helianhealth.iflow.tasks.jdbc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import com.helianhealth.iflow.tasks.jdbc.providers.JdbcConnectionProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** JDBC 任务自身的执行和资源释放，使用内存数据库，无工作流运行时。 */
class JdbcTaskExecutionTest {

    private final WorkflowModel workflow = new WorkflowModel();

    @ParameterizedTest
    @ValueSource(strings = {"omitted", "", " query "})
    void queryIsDefaultAndAcceptsTrimmedCaseInsensitiveOperation(String operation) throws Exception {
        RecordingProvider provider = new RecordingProvider();
        TaskModel task = task(provider.url, "SELECT $.id AS id", Map.of("id", 42));
        if (!operation.equals("omitted")) {
            task.getInputData().put("operation", operation);
        }

        new JdbcTask(provider, new ObjectMapper()).start(workflow, task, null);

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus(), task.getReasonForInCompletion());
        assertEquals(List.of(Map.of("id", 42)), task.outputAsMap().get("rows"));
        assertEquals(1, task.outputAsMap().get("rowCount"));
        assertEquals(0, task.outputAsMap().get("updateCount"));
        assertTrue(provider.opened.isClosed());
    }

    @Test
    void emptyQueryCompletesWithEmptyRowsAndZeroCount() throws Exception {
        RecordingProvider provider = new RecordingProvider();
        TaskModel task = task(provider.url, "SELECT 1 AS id WHERE 1=0", Map.of());

        new JdbcTask(provider, new ObjectMapper()).start(workflow, task, null);

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus(), task.getReasonForInCompletion());
        assertEquals(List.of(), task.outputAsMap().get("rows"));
        assertEquals(0, task.outputAsMap().get("rowCount"));
        assertTrue(provider.opened.isClosed());
    }

    @Test
    void nullPayloadValueRemainsNullInQueryResult() throws Exception {
        RecordingProvider provider = new RecordingProvider();
        Map<String, Object> payload = new HashMap<>();
        payload.put("value", null);
        TaskModel task = task(provider.url, "SELECT CAST($.value AS VARCHAR) AS result", payload);

        new JdbcTask(provider, new ObjectMapper()).start(workflow, task, null);

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus(), task.getReasonForInCompletion());
        List<?> rows = (List<?>) task.outputAsMap().get("rows");
        Map<?, ?> row = (Map<?, ?>) rows.get(0);
        assertTrue(row.containsKey("result"));
        assertNull(row.get("result"));
        assertTrue(provider.opened.isClosed());
    }

    @Test
    void updateWithNoMatchingRowsCompletesAndClosesConnection() throws Exception {
        RecordingProvider provider = new RecordingProvider();
        try (Connection keeper = DriverManager.getConnection(provider.url)) {
            keeper.createStatement().execute("CREATE TABLE items (id INT, name VARCHAR(100))");
            keeper.createStatement().execute("INSERT INTO items VALUES (1, 'original')");
            TaskModel task = task(provider.url, "UPDATE items SET name=$.name WHERE id=$.id",
                    Map.of("name", "changed", "id", 99));
            task.getInputData().put("operation", " update ");

            new JdbcTask(provider, new ObjectMapper()).start(workflow, task, null);

            assertEquals(TaskModel.Status.COMPLETED, task.getStatus(), task.getReasonForInCompletion());
            assertEquals(0, task.outputAsMap().get("updateCount"));
            assertEquals(0, task.outputAsMap().get("rowCount"));
            assertNull(task.outputAsMap().get("rows"));
            assertTrue(provider.opened.isClosed());
            try (var rows = keeper.createStatement().executeQuery("SELECT name FROM items WHERE id=1")) {
                assertTrue(rows.next());
                assertEquals("original", rows.getString(1));
            }
        }
    }

    @Test
    void sqlExecutionFailureClosesConnectionAndPreservesBusinessOutput() throws Exception {
        RecordingProvider provider = new RecordingProvider();
        TaskModel task = task(provider.url, "SELECT * FROM missing_table", Map.of());
        task.setOutputData(Map.of("retained", "previous output"));

        new JdbcTask(provider, new ObjectMapper()).start(workflow, task, null);

        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertTrue(task.getReasonForInCompletion().contains("missing_table"));
        assertEquals(Map.of("retained", "previous output"), task.getOutputData());
        assertNotNull(provider.opened);
        assertTrue(provider.opened.isClosed());
    }

    @ParameterizedTest
    @CsvSource({"operation, DELETE", "vendor, unsupported"})
    void invalidEnumInputFailsBeforeOpeningConnection(String field, String value) {
        RecordingProvider provider = new RecordingProvider();
        TaskModel task = task(provider.url, "SELECT 1", Map.of());
        task.getInputData().put(field, value);

        new JdbcTask(provider, new ObjectMapper()).start(workflow, task, null);

        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertTrue(task.getReasonForInCompletion().contains("Invalid JDBC inputParameters"));
        assertNull(provider.opened);
        assertTrue(task.outputAsMap().isEmpty());
    }

    @Test
    void cancelMarksTaskCanceledWithoutOpeningConnection() {
        RecordingProvider provider = new RecordingProvider();
        TaskModel task = task(provider.url, "SELECT 1", Map.of());

        new JdbcTask(provider, new ObjectMapper()).cancel(workflow, task, null);

        assertEquals(TaskModel.Status.CANCELED, task.getStatus());
        assertNull(provider.opened);
    }

    private TaskModel task(String url, String sql, Map<String, Object> payload) {
        TaskModel task = new TaskModel();
        task.setInputData(new HashMap<>(Map.of("url", url, "vendor", "MYSQL", "sqlTemplate", sql, "payload", payload)));
        return task;
    }

    private static class RecordingProvider implements JdbcConnectionProvider {
        private final String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE";
        private Connection opened;

        @Override
        public Connection openConnection(JdbcTask.Input input) throws SQLException {
            opened = DriverManager.getConnection(input.getUrl());
            return opened;
        }

        @Override
        public int resolveConnectTimeoutMs(JdbcTask.Input input) {
            return 1000;
        }

        @Override
        public int resolveQueryTimeoutMs(JdbcTask.Input input) {
            return 2500;
        }
    }
}
