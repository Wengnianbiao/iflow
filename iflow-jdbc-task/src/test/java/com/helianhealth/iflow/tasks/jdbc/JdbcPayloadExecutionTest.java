package com.helianhealth.iflow.tasks.jdbc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowTask;
import com.helianhealth.iflow.core.execution.mapper.JdbcTaskMapper;
import com.helianhealth.iflow.core.execution.mapper.TaskMapperContext;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import com.helianhealth.iflow.core.utils.ParametersUtils;
import com.helianhealth.iflow.tasks.jdbc.providers.JdbcConnectionProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 真实 JDBC 执行：Key / Value 表达式 → payload Map → SQL 列表展开与参数绑定。 */
class JdbcPayloadExecutionTest {

    @ParameterizedTest
    @ValueSource(strings = {"IN $.barcodes", "IN ($.barcodes)", "NOT IN $.barcodes", "NOT IN ($.barcodes)"})
    void queryWithKeyValuePayload(String predicate) throws Exception {
        String url = databaseUrl();
        try (Connection keeper = DriverManager.getConnection(url)) {
            keeper.createStatement().execute("CREATE TABLE v_lis_apply (barcode VARCHAR(100), active BOOLEAN)");
            keeper.createStatement().execute("INSERT INTO v_lis_apply VALUES ('A001',true),('A002',true),('B003',true)");
            Map<String, Object> definitionInput = Map.of(
                    "url", "${workflow.input.dbUrl}", "vendor", "MYSQL", "operation", "QUERY",
                    "payload", Map.of("barcodes", "${workflow.input.data.barcodes}",
                            "active", "${workflow.input.data.active}"),
                    "sqlTemplate", "SELECT barcode FROM v_lis_apply WHERE active=$.active AND barcode " + predicate + " ORDER BY barcode");
            TaskModel task = run(url, definitionInput, Map.of("barcodes", List.of("A001", "A002"), "active", true));
            assertEquals(TaskModel.Status.COMPLETED, task.getStatus(), task.getReasonForInCompletion());
            assertEquals(predicate.startsWith("NOT") ? 1 : 2, task.outputAsMap().get("rowCount"));
            assertEquals(predicate.startsWith("NOT") ? List.of(Map.of("barcode", "B003"))
                    : List.of(Map.of("barcode", "A001"), Map.of("barcode", "A002")), task.outputAsMap().get("rows"));
            assertEquals(List.of("A001", "A002"), ((Map<?, ?>) task.getInputData().get("payload")).get("barcodes"));
        }
    }

    @Test
    void updateAndQueryBindQuotedTextAsData() throws Exception {
        String url = databaseUrl();
        String barcode = "A001' OR 1=1 --";
        try (Connection keeper = DriverManager.getConnection(url)) {
            keeper.createStatement().execute("CREATE TABLE v_lis_apply (barcode VARCHAR(100))");
            keeper.createStatement().execute("INSERT INTO v_lis_apply VALUES ('other')");
            TaskModel inserted = run(url, Map.of("url", url, "vendor", "MYSQL", "operation", "UPDATE",
                    "payload", Map.of("barcode", "${workflow.input.data.barcode}"),
                    "sqlTemplate", "INSERT INTO v_lis_apply (barcode) VALUES ($.barcode)"), Map.of("barcode", barcode));
            assertEquals(TaskModel.Status.COMPLETED, inserted.getStatus(), inserted.getReasonForInCompletion());
            assertEquals(1, inserted.outputAsMap().get("updateCount"));
            TaskModel selected = run(url, Map.of("url", url, "vendor", "MYSQL",
                    "payload", Map.of("barcode", "${workflow.input.data.barcode}"),
                    "sqlTemplate", "SELECT barcode FROM v_lis_apply WHERE barcode=$.barcode"), Map.of("barcode", barcode));
            assertEquals(TaskModel.Status.COMPLETED, selected.getStatus(), selected.getReasonForInCompletion());
            assertEquals(List.of(Map.of("barcode", barcode)), selected.outputAsMap().get("rows"));
        }
    }

    private TaskModel run(String url, Map<String, Object> input, Object data) throws Exception {
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("jdbc-test");
        workflow.setInput(Map.of("dbUrl", url, "data", data));
        WorkflowTask definition = WorkflowTask.builder().name("query apply").taskReferenceName("query_ref")
                .type("JDBC").inputParameters(input).build();
        TaskModel task = new JdbcTaskMapper(new ParametersUtils()).getMappedTasks(TaskMapperContext.builder()
                .workflowModel(workflow).workflowTask(definition).taskId("jdbc-task").build()).get(0);
        Connection[] opened = new Connection[1];
        JdbcConnectionProvider provider = new JdbcConnectionProvider() {
            public Connection openConnection(JdbcTask.Input resolved) throws SQLException {
                opened[0] = DriverManager.getConnection(resolved.getUrl());
                return opened[0];
            }
            public int resolveConnectTimeoutMs(JdbcTask.Input resolved) { return 1000; }
            public int resolveQueryTimeoutMs(JdbcTask.Input resolved) { return 2500; }
        };
        new JdbcTask(provider, new ObjectMapper()).execute(workflow, task, null);
        if (opened[0] != null) assertTrue(opened[0].isClosed());
        return task;
    }

    private static String databaseUrl() {
        return "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE";
    }
}
