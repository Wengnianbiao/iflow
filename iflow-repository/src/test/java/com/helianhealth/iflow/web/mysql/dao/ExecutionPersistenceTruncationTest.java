package com.helianhealth.iflow.web.mysql.dao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowDef;
import com.helianhealth.iflow.core.dal.ExecutionDAOFacade;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExecutionPersistenceTruncationTest {

    private static final int MAX_CHARS = PayloadTruncator.MAX_CHARS;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String body = "b".repeat(MAX_CHARS + 1);
    private final Map<String, Object> input = new LinkedHashMap<>(
            Map.of("function", "JSON_PARSE", "nested", Map.of("request", body)));
    private final Map<String, Object> output = new LinkedHashMap<>(
            Map.of("statusCode", 200, "body", body));

    private final Connection connection = mock(Connection.class);
    private final PreparedStatement statement = mock(PreparedStatement.class);
    private ExecutionDAOFacade facade;

    @BeforeEach
    void setUp() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(1);
        facade = new ExecutionDAOFacade(new MySqlExecutionDAO(dataSource, mapper), mapper);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void taskWritesTruncateOnlyJdbcPayload(boolean create) throws Exception {
        TaskModel task = task("t-1");
        AtomicReference<JsonNode> persisted = inspectJdbcPayload(() -> assertRuntime(task), false);

        if (create) {
            assertSame(task, facade.createTasks(List.of(task)).get(0));
            assertTrue(task.getScheduledTime() > 0);
            assertEquals(task.getScheduledTime(), task.getFirstScheduledTime());
        } else {
            facade.updateTask(task);
        }

        assertRuntime(task);
        assertTruncated(persisted.get(), "inputData", "outputData");
        assertTrue(task.getUpdateTime() > 0);
        assertEquals(task.getUpdateTime(), persisted.get().path("updateTime").asLong());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void workflowWritesTruncateOnlyJdbcPayload(boolean create) throws Exception {
        WorkflowModel workflow = workflow();
        AtomicReference<JsonNode> persisted = inspectJdbcPayload(() -> assertRuntime(workflow), false);

        String workflowId = create ? facade.createWorkflow(workflow) : facade.updateWorkflow(workflow);

        assertEquals(workflow.getWorkflowId(), workflowId);
        assertRuntime(workflow);
        assertTruncated(persisted.get(), "input", "output");
        assertTrue(workflow.getUpdateTime() > 0);
        assertEquals(workflow.getUpdateTime(), persisted.get().path("updateTime").asLong());
        assertTrue(persisted.get().path("tasks").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void failedTaskWriteNeverExposesTruncatedRuntimeData(boolean create) throws Exception {
        TaskModel task = task("t-failed");
        inspectJdbcPayload(() -> assertRuntime(task), true);

        assertThrows(RuntimeException.class, () -> {
            if (create) {
                facade.createTasks(List.of(task));
            } else {
                facade.updateTask(task);
            }
        });

        assertRuntime(task);
        verify(connection).rollback();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void failedWorkflowWriteNeverExposesTruncatedRuntimeData(boolean create) throws Exception {
        WorkflowModel workflow = workflow();
        inspectJdbcPayload(() -> assertRuntime(workflow), true);

        assertThrows(RuntimeException.class, () -> {
            if (create) {
                facade.createWorkflow(workflow);
            } else {
                facade.updateWorkflow(workflow);
            }
        });

        assertRuntime(workflow);
        verify(connection).rollback();
    }

    @Test
    void laterTaskSerializationFailureLeavesEarlierRuntimePayloadsIntact() {
        TaskModel first = task("t-first");
        TaskModel second = task("t-second");
        Object badOutput = new Object() {
            public String getValue() {
                throw new IllegalStateException("Cannot serialize this value");
            }
        };
        second.setOutputData(badOutput);

        assertThrows(RuntimeException.class, () -> facade.createTasks(List.of(first, second)));

        assertRuntime(first);
        assertSame(input, second.getInputData());
        assertSame(badOutput, second.getOutputData());
    }

    @Test
    void taskPayloadChangeDuringWriteIsNotOverwritten() throws Exception {
        TaskModel task = task("t-changing");
        Map<String, Object> nextOutput = Map.of("next", "new runtime value");
        AtomicReference<JsonNode> persisted = inspectJdbcPayload(() -> {
            assertRuntime(task);
            task.setOutputData(nextOutput);
        }, false);

        facade.updateTask(task);

        assertSame(nextOutput, task.getOutputData());
        assertTruncated(persisted.get(), "inputData", "outputData");
    }

    @Test
    void workflowPayloadChangeDuringWriteIsNotOverwritten() throws Exception {
        WorkflowModel workflow = workflow();
        List<String> nextOutput = List.of("new runtime value");
        AtomicReference<JsonNode> persisted = inspectJdbcPayload(() -> {
            assertRuntime(workflow);
            workflow.setOutput(nextOutput);
        }, false);

        facade.updateWorkflow(workflow);

        assertSame(nextOutput, workflow.getOutput());
        assertTruncated(persisted.get(), "input", "output");
    }

    private AtomicReference<JsonNode> inspectJdbcPayload(Runnable checkRuntime, boolean fail) throws Exception {
        AtomicReference<JsonNode> persisted = new AtomicReference<>();
        doAnswer(invocation -> {
            String value = invocation.getArgument(1);
            if (value.startsWith("{")) {
                checkRuntime.run();
                persisted.set(mapper.readTree(value));
                if (fail) {
                    throw new SQLException("Simulated write failure");
                }
            }
            return null;
        }).when(statement).setString(anyInt(), anyString());
        return persisted;
    }

    private void assertTruncated(JsonNode persisted, String inputField, String outputField) throws Exception {
        assertNotNull(persisted);
        JsonNode storedInput = persisted.get(inputField);
        JsonNode storedOutput = persisted.get(outputField);
        assertTrue(mapper.writeValueAsString(storedInput).length() <= MAX_CHARS);
        assertTrue(mapper.writeValueAsString(storedOutput).length() <= MAX_CHARS);
        assertEquals("JSON_PARSE", storedInput.path("function").asText());
        assertTrue(storedInput.path("nested").path("request").asText().contains("已截断"));
        assertEquals(200, storedOutput.path("statusCode").asInt());
        assertTrue(storedOutput.path("body").asText().contains("已截断"));
    }

    private void assertRuntime(TaskModel task) {
        assertSame(input, task.getInputData());
        assertSame(output, task.getOutputData());
        assertEquals(body, ((Map<?, ?>) task.getInputData().get("nested")).get("request"));
        assertEquals(body, ((Map<?, ?>) task.getOutputData()).get("body"));
    }

    private void assertRuntime(WorkflowModel workflow) {
        assertSame(input, workflow.getInput());
        assertSame(output, workflow.getOutput());
        assertEquals(body, ((Map<?, ?>) workflow.getInput().get("nested")).get("request"));
        assertEquals(body, ((Map<?, ?>) workflow.getOutput()).get("body"));
        assertRuntime(workflow.getTasks().get(0));
    }

    private TaskModel task(String id) {
        TaskModel task = new TaskModel();
        task.setTaskId(id);
        task.setWorkflowInstanceId("wf-1");
        task.setTaskType("HTTP");
        task.setReferenceTaskName(id);
        task.setStatus(TaskModel.Status.IN_PROGRESS);
        task.setInputData(input);
        task.setOutputData(output);
        return task;
    }

    private WorkflowModel workflow() {
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("wf-1");
        workflow.setWorkflowDefinition(WorkflowDef.builder().route("demo").version(1).build());
        workflow.setInput(input);
        workflow.setOutput(output);
        workflow.getTasks().add(task("t-1"));
        return workflow;
    }
}
