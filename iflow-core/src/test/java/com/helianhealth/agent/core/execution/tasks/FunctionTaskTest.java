package com.helianhealth.agent.core.execution.tasks;

import com.helianhealth.agent.core.execution.function.FunctionExecutor;
import com.helianhealth.agent.core.model.TaskModel;
import com.helianhealth.agent.core.model.WorkflowModel;
import com.helianhealth.agent.core.utils.ParametersUtils;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class FunctionTaskTest {

    private final FunctionTask functionTask = new FunctionTask(new FunctionExecutor(1_000));

    @Test
    void writesParsedMapDirectlyToOutputRoot() {
        TaskModel task = execute(Map.of(
                FunctionTask.INPUT_FUNCTION,
                "JSON_PARSE",
                FunctionTask.INPUT_VALUE,
                "{\"patientCode\":\"82601230009\",\"applyStatus\":\"3\"}"));

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals(
                Map.of("patientCode", "82601230009", "applyStatus", "3"),
                task.getOutputData());
        assertFalse(task.getOutputData().containsKey("result"));
    }

    @Test
    void nullInputCompletesWithEmptyMap() {
        Map<String, Object> input = new HashMap<>();
        input.put(FunctionTask.INPUT_FUNCTION, "JSON_PARSE");
        input.put(FunctionTask.INPUT_VALUE, null);

        TaskModel task = execute(input);

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals(Map.of(), task.getOutputData());
    }

    @Test
    void missingValueFailsTask() {
        TaskModel task = execute(Map.of(FunctionTask.INPUT_FUNCTION, "JSON_PARSE"));

        assertFailure(task, "FUNCTION input 'value' must be provided");
    }

    @Test
    void missingFunctionFailsTask() {
        TaskModel task = execute(Map.of(FunctionTask.INPUT_VALUE, "{}"));

        assertFailure(task, "FUNCTION input 'function' must be provided");
    }

    @Test
    void nonStringValueFailsTask() {
        TaskModel task = execute(Map.of(
                FunctionTask.INPUT_FUNCTION,
                "JSON_PARSE",
                FunctionTask.INPUT_VALUE,
                Map.of("already", "parsed")));

        assertFailure(task, "FUNCTION input 'value' must be a String or null");
    }

    @Test
    void unknownFunctionFailsTask() {
        TaskModel task = execute(Map.of(
                FunctionTask.INPUT_FUNCTION,
                "YAML_PARSE",
                FunctionTask.INPUT_VALUE,
                "value"));

        assertFailure(task, "Unsupported FUNCTION function: YAML_PARSE");
    }

    @Test
    void parsedFieldsCanBeBoundByDownstreamTasks() {
        TaskModel task = execute(Map.of(
                FunctionTask.INPUT_FUNCTION,
                "JSON_PARSE",
                FunctionTask.INPUT_VALUE,
                "{\"patientCode\":\"82601230009\"}"));
        task.setReferenceTaskName("parse_request_ref");
        WorkflowModel workflow = new WorkflowModel();
        workflow.setInput(Map.of());
        workflow.setTasks(java.util.List.of(task));

        Map<String, Object> downstreamInput =
                new ParametersUtils()
                        .getTaskInput(
                                Map.of("patientCode", "${parse_request_ref.output.patientCode}"),
                                workflow,
                                "downstream-task");

        assertEquals("82601230009", downstreamInput.get("patientCode"));
    }

    private TaskModel execute(Map<String, Object> input) {
        TaskModel task = new TaskModel();
        task.setTaskId("function-task");
        task.setInputData(input);
        task.addOutput("stale", true);
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("workflow-1");

        functionTask.execute(workflow, task, null);
        return task;
    }

    private void assertFailure(TaskModel task, String expectedMessage) {
        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertEquals(expectedMessage, task.getReasonForInCompletion());
        assertEquals(Map.of("error", expectedMessage), task.getOutputData());
    }
}
