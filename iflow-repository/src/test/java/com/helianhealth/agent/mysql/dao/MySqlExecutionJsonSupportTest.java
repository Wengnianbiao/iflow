package com.helianhealth.agent.mysql.dao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.agent.common.domain.metadata.task.TaskType;
import com.helianhealth.agent.core.model.TaskModel;
import com.helianhealth.agent.core.model.WorkflowModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MySqlExecutionJsonSupportTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MySqlExecutionJsonSupport support =
            new MySqlExecutionJsonSupport(objectMapper);

    @Test
    void workflowPayloadOmitsTasks() {
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("wf-1");
        workflow.setStatus(WorkflowModel.Status.RUNNING);
        workflow.setCorrelationId("corr-1");
        workflow.getTasks().add(new TaskModel());

        String json = support.toWorkflowPayload(workflow);
        WorkflowModel restored = support.fromWorkflowPayload(json);

        assertNotNull(restored);
        assertEquals("wf-1", restored.getWorkflowId());
        assertEquals(WorkflowModel.Status.RUNNING, restored.getStatus());
        assertTrue(restored.getTasks().isEmpty());
        assertTrue(json.contains("\"workflowId\":\"wf-1\""));
    }

    @Test
    void legacyWorkflowPayloadWithStartTimeIsRestored() {
        String legacyJson =
                """
                {"workflowId":"wf-legacy","status":"RUNNING","startTime":12345}
                """;

        WorkflowModel restored = support.fromWorkflowPayload(legacyJson);

        assertNotNull(restored);
        assertEquals("wf-legacy", restored.getWorkflowId());
        assertEquals(WorkflowModel.Status.RUNNING, restored.getStatus());
        assertEquals(12345L, restored.getStartTime());
    }

    @Test
    void taskKeyUsesReferenceNameAndRetryCount() {
        TaskModel task = new TaskModel();
        task.setReferenceTaskName("noop");
        task.setRetryCount(2);

        assertEquals("noop_2", MySqlExecutionJsonSupport.taskKey(task));
    }

    @Test
    void taskPayloadRoundTrip() {
        TaskModel task = new TaskModel();
        task.setTaskId("t-1");
        task.setReferenceTaskName("http");
        task.setRetryCount(0);
        task.setStatus(TaskModel.Status.COMPLETED);
        task.setForkTaskId("fork-1");

        TaskModel restored = support.fromTaskPayload(support.toTaskPayload(task));

        assertEquals("t-1", restored.getTaskId());
        assertEquals("http", restored.getReferenceTaskName());
        assertEquals(TaskModel.Status.COMPLETED, restored.getStatus());
        assertEquals("fork-1", restored.getForkTaskId());
    }

    @ParameterizedTest(name = "task result survives JSON persistence: {0}")
    @MethodSource("taskResults")
    void taskResultPayloadRoundTrip(String description, Object result) throws Exception {
        TaskModel task = new TaskModel();
        task.setTaskId("function-1");
        task.setTaskType(TaskType.TASK_TYPE_FUNCTION);
        task.setReferenceTaskName("parse_ref");
        task.setStatus(TaskModel.Status.COMPLETED);
        task.setOutputData(result);

        String json = support.toTaskPayload(task);
        JsonNode persistedOutput = objectMapper.readTree(json).path("outputData");
        TaskModel restored = support.fromTaskPayload(json);

        assertTrue(objectMapper.readTree(json).has("outputData"));
        assertEquals(objectMapper.valueToTree(result), persistedOutput);
        assertNotNull(restored);
        assertEquals(TaskType.TASK_TYPE_FUNCTION, restored.getTaskType());
        assertEquals(TaskModel.Status.COMPLETED, restored.getStatus());
        assertEquals(result, restored.getOutputData());
    }

    private static Stream<Arguments> taskResults() {
        Map<String, Object> objectResult = new LinkedHashMap<>();
        objectResult.put("error", "business error");
        objectResult.put("result", Map.of("patientCode", "P001"));
        objectResult.put("nullable", null);

        return Stream.of(
                Arguments.of("object with business error/result fields", objectResult),
                Arguments.of("empty object", Map.of()),
                Arguments.of("array", List.of(Map.of("code", "A"), Map.of("code", "B"))),
                Arguments.of("empty array", List.of()),
                Arguments.of("string", "parsed text"),
                Arguments.of("empty string", ""),
                Arguments.of("integer", 42),
                Arguments.of("long integer", 9_000_000_000L),
                Arguments.of("decimal number", 1.25),
                Arguments.of("true", true),
                Arguments.of("false", false),
                Arguments.of("null", (Object) null));
    }

    @ParameterizedTest(name = "workflow output survives JSON persistence: {0}")
    @MethodSource("taskResults")
    void workflowOutputPayloadRoundTrip(String description, Object output) throws Exception {
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("raw-output");
        workflow.setStatus(WorkflowModel.Status.COMPLETED);
        workflow.setOutput(output);

        String json = support.toWorkflowPayload(workflow);
        WorkflowModel restored = support.fromWorkflowPayload(json);

        assertTrue(objectMapper.readTree(json).has("output"));
        assertEquals(objectMapper.valueToTree(output), objectMapper.readTree(json).get("output"));
        assertEquals(output, restored.getOutput());
    }
}
