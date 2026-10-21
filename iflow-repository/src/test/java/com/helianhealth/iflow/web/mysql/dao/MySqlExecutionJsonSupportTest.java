package com.helianhealth.iflow.web.mysql.dao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.iflow.common.domain.metadata.task.TaskType;
import com.helianhealth.iflow.common.domain.run.Task;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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
    void largeTaskAndWorkflowPayloadsRoundTripThroughCompression() {
        ExecutionPayloadCodec codec = new ExecutionPayloadCodec(32, 1024 * 1024);
        MySqlExecutionJsonSupport compressing = new MySqlExecutionJsonSupport(objectMapper, codec);
        String body = "fee-service-item-".repeat(40);

        TaskModel task = new TaskModel();
        task.setTaskId("t-large");
        task.setReferenceTaskName("call_his_ref");
        task.setStatus(TaskModel.Status.COMPLETED);
        task.setOutputData(body);
        String storedTask = compressing.toTaskPayload(task);
        assertTrue(storedTask.startsWith(ExecutionPayloadCodec.PREFIX));
        TaskModel restoredTask = compressing.fromTaskPayload(storedTask);
        assertEquals("t-large", restoredTask.getTaskId());
        assertEquals(body, restoredTask.getOutputData());

        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("wf-large");
        workflow.setStatus(WorkflowModel.Status.COMPLETED);
        workflow.setOutput(body);
        String storedWorkflow = compressing.toWorkflowPayload(workflow);
        assertTrue(storedWorkflow.startsWith(ExecutionPayloadCodec.PREFIX));
        WorkflowModel restoredWorkflow = compressing.fromWorkflowPayload(storedWorkflow);
        assertEquals("wf-large", restoredWorkflow.getWorkflowId());
        assertEquals(body, restoredWorkflow.getOutput());
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

    @ParameterizedTest
    @EnumSource(value = TaskModel.Status.class, names = {"SCHEDULED", "IN_PROGRESS"})
    void pendingTaskStatusSurvivesPublicModelAndJsonRoundTrip(TaskModel.Status status) throws Exception {
        TaskModel task = new TaskModel();
        task.setTaskId("queued-1");
        task.setStatus(status);
        task.setPreviousStatus(TaskModel.Status.SCHEDULED);
        task.setScheduledTime(12345);

        Task publicTask = task.toTask();
        assertEquals(Task.Status.valueOf(status.name()), publicTask.getStatus());
        assertEquals(Task.Status.SCHEDULED, publicTask.getPreviousStatus());
        TaskModel fromPublic = TaskModel.fromTask(publicTask);
        assertEquals(status, fromPublic.getStatus());
        assertEquals(TaskModel.Status.SCHEDULED, fromPublic.getPreviousStatus());

        String json = support.toTaskPayload(fromPublic);
        TaskModel restored = support.fromTaskPayload(json);

        assertEquals(status.name(), objectMapper.readTree(json).path("status").asText());
        assertEquals(status, restored.getStatus());
        assertEquals(TaskModel.Status.SCHEDULED, restored.getPreviousStatus());
        assertEquals(12345, restored.getScheduledTime());
        assertEquals(0, restored.getStartTime());
    }

    @ParameterizedTest(name = "task result survives JSON persistence: {0}")
    @MethodSource("taskResults")
    void taskResultPayloadRoundTrip(String description, Object result) throws Exception {
        TaskModel task = new TaskModel();
        task.setTaskId("inline-1");
        task.setTaskType(TaskType.TASK_TYPE_INLINE);
        task.setReferenceTaskName("parse_ref");
        task.setStatus(TaskModel.Status.COMPLETED);
        task.setOutputData(result);

        String json = support.toTaskPayload(task);
        JsonNode persistedOutput = objectMapper.readTree(json).path("outputData");
        TaskModel restored = support.fromTaskPayload(json);

        assertTrue(objectMapper.readTree(json).has("outputData"));
        assertEquals(objectMapper.valueToTree(result), persistedOutput);
        assertNotNull(restored);
        assertEquals(TaskType.TASK_TYPE_INLINE, restored.getTaskType());
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

    @ParameterizedTest
    @MethodSource("oversizedOutputs")
    void oversizedOutputKeepsItsJsonTypeAndRuntimeValue(Object output) throws Exception {
        TaskModel task = new TaskModel();
        task.setOutputData(output);
        WorkflowModel workflow = new WorkflowModel();
        workflow.setOutput(output);
        JsonNode original = objectMapper.valueToTree(output);

        JsonNode storedTaskOutput = objectMapper.readTree(support.toTaskPayload(task)).get("outputData");
        JsonNode storedWorkflowOutput = objectMapper.readTree(support.toWorkflowPayload(workflow)).get("output");

        assertEquals(original.getNodeType(), storedTaskOutput.getNodeType());
        assertEquals(original.getNodeType(), storedWorkflowOutput.getNodeType());
        assertTrue(objectMapper.writeValueAsString(storedTaskOutput).length() <= PayloadTruncator.MAX_CHARS);
        assertTrue(objectMapper.writeValueAsString(storedWorkflowOutput).length() <= PayloadTruncator.MAX_CHARS);
        assertSame(output, task.getOutputData());
        assertSame(output, workflow.getOutput());
        assertEquals(original, objectMapper.valueToTree(output));
    }

    private static Stream<Arguments> oversizedOutputs() {
        String text = "大报文".repeat(PayloadTruncator.MAX_CHARS / 3 + 1);
        return Stream.of(
                Arguments.of(text),
                Arguments.of(List.of(Map.of("body", text))),
                Arguments.of(Map.of("body", text)));
    }
}
