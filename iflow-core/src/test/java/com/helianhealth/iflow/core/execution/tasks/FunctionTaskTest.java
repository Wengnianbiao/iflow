package com.helianhealth.iflow.core.execution.tasks;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.helianhealth.iflow.core.execution.function.FunctionExecutor;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import com.helianhealth.iflow.core.utils.ParametersUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FunctionTaskTest {

    private final Function functionTask = new Function(new FunctionExecutor(1_000));
    private final Function defaultLimitFunction =
            new Function(new FunctionExecutor(FunctionExecutor.DEFAULT_MAX_INPUT_CHARS));

    @Test
    void writesParsedMapDirectlyToOutput() {
        TaskModel task = execute(Map.of(
                Function.INPUT_FUNCTION,
                "JSON_PARSE",
                Function.INPUT_VALUE,
                "{\"patientCode\":\"82601230009\",\"applyStatus\":\"3\"}"));

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals(
                Map.of("patientCode", "82601230009", "applyStatus", "3"),
                task.getOutputData());
    }

    @Test
    void writesStringifiedJsonDirectlyToOutput() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("patientCode", "82601230009");
        payload.put("applyStatus", "3");
        TaskModel task = execute(Map.of(
                Function.INPUT_FUNCTION,
                "JSON_STRINGIFY",
                Function.INPUT_VALUE,
                payload));

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals("{\"patientCode\":\"82601230009\",\"applyStatus\":\"3\"}", task.getOutputData());
    }

    @Test
    void writesStringifiedXmlDirectlyToOutput() {
        TaskModel task = execute(Map.of(
                Function.INPUT_FUNCTION,
                "XML_STRINGIFY",
                Function.INPUT_ROOT_NAME,
                "patient",
                Function.INPUT_VALUE,
                Map.of("code", "82601230009")));

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals("<patient><code>82601230009</code></patient>", task.getOutputData());
        assertEquals(
                "<patient><code>82601230009</code></patient>",
                bind(task, "${parse_request_ref.output}").get("value"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"JSON_PARSE", "XML_PARSE"})
    void nullInputCompletesWithNullOutput(String function) {
        Map<String, Object> input = new HashMap<>();
        input.put(Function.INPUT_FUNCTION, function);
        input.put(Function.INPUT_VALUE, null);

        TaskModel task = execute(input);

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals(null, task.getOutputData());
    }

    @ParameterizedTest
    @MethodSource("jsonResults")
    void bindsCompleteResultWithoutChangingItsType(String value, Object expected) {
        TaskModel task = execute(Map.of(
                Function.INPUT_FUNCTION, "JSON_PARSE",
                Function.INPUT_VALUE, value));

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals(expected, task.getOutputData());
        Map<String, Object> input = bind(task, "${parse_request_ref.output}");
        assertTrue(input.containsKey("value"));
        assertEquals(expected, input.get("value"));
    }

    private static Stream<Arguments> jsonResults() {
        return Stream.of(
                Arguments.of("{\"error\":\"business\",\"result\":7}",
                        Map.of("error", "business", "result", 7)),
                Arguments.of("{}", Map.of()),
                Arguments.of("[]", List.of()),
                Arguments.of("[{\"id\":1}]", List.of(Map.of("id", 1))),
                Arguments.of("\"hello\"", "hello"),
                Arguments.of("\"\"", ""),
                Arguments.of("42", 42),
                Arguments.of("true", true),
                Arguments.of("false", false),
                Arguments.of("null", null));
    }

    @Test
    void parsedArrayElementsCanBeBoundByDownstreamTasks() {
        TaskModel task = execute(Map.of(
                Function.INPUT_FUNCTION, "JSON_PARSE",
                Function.INPUT_VALUE, "[{\"patientCode\":\"82601230009\"}]"));

        assertEquals("82601230009", bind(task,
                "${parse_request_ref.output[0].patientCode}").get("value"));
    }

    @Test
    void xmlOutputRetainsRootElement() {
        TaskModel task = execute(Map.of(
                Function.INPUT_FUNCTION, "XML_PARSE",
                Function.INPUT_VALUE, "<patient><code>82601230009</code></patient>"));

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals(Map.of("patient", Map.of("code", "82601230009")),
                task.getOutputData());
        assertEquals("82601230009", bind(task,
                "${parse_request_ref.output.patient.code}").get("value"));
    }

    @Test
    void failedParsingRemovesPreviousResult() {
        TaskModel task = execute(Map.of(
                Function.INPUT_FUNCTION, "JSON_PARSE",
                Function.INPUT_VALUE, "[1]"));
        task.setInputData(Map.of(
                Function.INPUT_FUNCTION, "JSON_PARSE",
                Function.INPUT_VALUE, "[invalid"));

        functionTask.execute(new WorkflowModel(), task, null);

        assertFailure(task, "FUNCTION JSON_PARSE failed: invalid JSON");
    }

    @Test
    void missingValueFailsTask() {
        TaskModel task = execute(Map.of(Function.INPUT_FUNCTION, "JSON_PARSE"));

        assertFailure(task, "FUNCTION input 'value' must be provided");
    }

    @Test
    void missingFunctionFailsTask() {
        TaskModel task = execute(Map.of(Function.INPUT_VALUE, "{}"));

        assertFailure(task, "FUNCTION input 'function' must be provided");
    }

    @Test
    void jsonStringifyNullCompletesWithNullLiteral() {
        Map<String, Object> input = new HashMap<>();
        input.put(Function.INPUT_FUNCTION, "JSON_STRINGIFY");
        input.put(Function.INPUT_VALUE, null);

        TaskModel task = execute(input);

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals("null", task.getOutputData());
    }

    @Test
    void xmlStringifyNullWithoutRootCompletesWithNullOutput() {
        Map<String, Object> input = new HashMap<>();
        input.put(Function.INPUT_FUNCTION, "XML_STRINGIFY");
        input.put(Function.INPUT_VALUE, null);

        TaskModel task = execute(input);

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals(null, task.getOutputData());
    }

    @Test
    void nonStringValueFailsParseTask() {
        TaskModel task = execute(Map.of(
                Function.INPUT_FUNCTION,
                "JSON_PARSE",
                Function.INPUT_VALUE,
                Map.of("already", "parsed")));

        assertFailure(task, "FUNCTION input 'value' must be a String or null");
    }

    @Test
    void nonStringRootNameFailsXmlStringify() {
        TaskModel task = execute(Map.of(
                Function.INPUT_FUNCTION,
                "XML_STRINGIFY",
                Function.INPUT_ROOT_NAME,
                Map.of("name", "patient"),
                Function.INPUT_VALUE,
                Map.of("code", "1")));

        assertFailure(task, "FUNCTION input 'rootName' must be a String");
    }

    @Test
    void unknownFunctionFailsTask() {
        TaskModel task = execute(Map.of(
                Function.INPUT_FUNCTION,
                "YAML_PARSE",
                Function.INPUT_VALUE,
                "value"));

        assertFailure(task, "Unsupported FUNCTION function: YAML_PARSE");
    }

    @Test
    void parsesUnpagedCatalogWithinDefaultLimit() {
        long begin = System.currentTimeMillis();
        int itemCount = 20_000;
        String json = unpagedCatalogJson(itemCount);
        assertTrue(json.length() > 5_000_000);
        assertTrue(json.length() < FunctionExecutor.DEFAULT_MAX_INPUT_CHARS);

        TaskModel task = executeWithDefaultLimit(Map.of(
                Function.INPUT_FUNCTION, "JSON_PARSE",
                Function.INPUT_VALUE, json));

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        Map<?, ?> payload = assertInstanceOf(Map.class, task.getOutputData());
        assertEquals(itemCount, payload.get("totalCount"));
        List<?> data = assertInstanceOf(List.class, payload.get("data"));
        assertEquals(itemCount, data.size());
        assertEquals("6006006", assertInstanceOf(Map.class, data.get(0)).get("serviceItemCode"));
        System.out.println(System.currentTimeMillis() - begin);

    }

    @Test
    void stringifiesUnpagedCatalogWithinDefaultLimit() {
        int itemCount = 20_000;
        String json = unpagedCatalogJson(itemCount);
        TaskModel parsed = executeWithDefaultLimit(Map.of(
                Function.INPUT_FUNCTION, "JSON_PARSE",
                Function.INPUT_VALUE, json));
        assertEquals(TaskModel.Status.COMPLETED, parsed.getStatus());

        TaskModel stringified = executeWithDefaultLimit(Map.of(
                Function.INPUT_FUNCTION, "JSON_STRINGIFY",
                Function.INPUT_VALUE, parsed.getOutputData()));

        assertEquals(TaskModel.Status.COMPLETED, stringified.getStatus());
        String output = assertInstanceOf(String.class, stringified.getOutputData());
        assertTrue(output.length() > 5_000_000);
        assertTrue(output.length() < FunctionExecutor.DEFAULT_MAX_INPUT_CHARS);
        assertEquals(json, output);
    }

    private static String unpagedCatalogJson(int itemCount) {
        String item = "{\"serviceItemCode\":\"6006006\",\"serviceItemName\":\"" + "x".repeat(280) + "\"}";
        StringBuilder json = new StringBuilder(itemCount * (item.length() + 1) + 64);
        json.append("{\"totalCount\":").append(itemCount).append(",\"data\":[");
        for (int i = 0; i < itemCount; i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append(item);
        }
        json.append("]}");
        return json.toString();
    }

    @Test
    void parsedFieldsCanBeBoundByDownstreamTasks() {
        TaskModel task = execute(Map.of(
                Function.INPUT_FUNCTION,
                "JSON_PARSE",
                Function.INPUT_VALUE,
                "{\"patientCode\":\"82601230009\"}"));
        assertEquals("82601230009", bind(task,
                "${parse_request_ref.output.patientCode}").get("value"));
    }

    private Map<String, Object> bind(TaskModel task, String expression) {
        task.setReferenceTaskName("parse_request_ref");
        WorkflowModel workflow = new WorkflowModel();
        workflow.setInput(Map.of());
        workflow.setTasks(List.of(task));

        return new ParametersUtils(new ObjectMapper())
                        .getTaskInput(
                                Map.of("value", expression),
                                workflow,
                                "downstream-task");
    }

    private TaskModel execute(Map<String, Object> input) {
        return execute(functionTask, input);
    }

    private TaskModel executeWithDefaultLimit(Map<String, Object> input) {
        return execute(defaultLimitFunction, input);
    }

    private TaskModel execute(Function function, Map<String, Object> input) {
        TaskModel task = new TaskModel();
        task.setTaskId("function-task");
        task.setInputData(input);
        task.addOutput("stale", true);
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("workflow-1");

        function.execute(workflow, task, null);
        return task;
    }

    private void assertFailure(TaskModel task, String expectedMessage) {
        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertEquals(expectedMessage, task.getReasonForInCompletion());
        assertEquals(null, task.getOutputData());
    }
}
