package com.helianhealth.iflow.core.execution.tasks;

import com.helianhealth.iflow.core.execution.evaluators.Evaluator;
import com.helianhealth.iflow.core.execution.evaluators.GraalJSEvaluator;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import com.helianhealth.iflow.core.execution.script.ScriptEvaluator;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class InlineUnitTest {

    @AutoClose
    private final ScriptEvaluator scriptEvaluator = new ScriptEvaluator(List.of());

    @Test
    void usesScalarAsOutput() {
        TaskModel task = executeInline("ok");

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals("ok", task.getOutputData());
    }

    @Test
    void usesBusinessMapAsOutput() {
        Map<String, Object> scriptResult = Map.of("code", "OK");

        TaskModel task = executeInline(scriptResult);

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals(scriptResult, task.getOutputData());
    }

    @ParameterizedTest
    @MethodSource("scriptResults")
    void graalScriptResultsRetainTheirJsonShape(String expression, Object expected) {
        Inline inline = new Inline(Map.of(GraalJSEvaluator.NAME, new GraalJSEvaluator(scriptEvaluator)));
        TaskModel task = new TaskModel();
        task.setInputData(Map.of(Inline.INPUT_EXPRESSION, expression));

        inline.start(new WorkflowModel(), task, null);

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals(expected, task.getOutputData());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void missingExpressionFailsWithDiagnosticAndClearsStaleOutput(String expression) {
        Map<String, Object> input = new HashMap<>();
        input.put(Inline.INPUT_EXPRESSION, expression);
        TaskModel task = executeWithInput(input, Map.of(GraalJSEvaluator.NAME, (script, values) -> "unused"));

        assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, task.getStatus());
        assertFalse(task.getStatus().isRetriable());
        assertTrue(task.getReasonForInCompletion().contains("expression"));
        assertNull(task.getOutputData());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "unknown"})
    void missingOrUnknownEvaluatorFailsTask(String evaluatorType) {
        Map<String, Object> input = new HashMap<>();
        input.put(Inline.INPUT_EXPRESSION, "42");
        input.put(Inline.INPUT_EVALUATOR_TYPE, evaluatorType);
        TaskModel task = executeWithInput(input, Map.of(GraalJSEvaluator.NAME, (script, values) -> 42));

        assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, task.getStatus());
        assertFalse(task.getStatus().isRetriable());
        assertNotNull(task.getReasonForInCompletion());
        assertNull(task.getOutputData());
    }

    @Test
    void customEvaluatorReceivesResolvedInputAndReplacesStaleOutput() {
        Map<String, Object> input = Map.of(Inline.INPUT_EXPRESSION, "value", Inline.INPUT_EVALUATOR_TYPE,
                "custom", "value", List.of(1, 2));
        Evaluator evaluator = (expression, values) -> {
            assertEquals("value", expression);
            assertEquals(input, values);
            return values.get(expression);
        };

        TaskModel task = executeWithInput(input, Map.of("custom", evaluator));

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals(List.of(1, 2), task.getOutputData());
    }

    @Test
    void evaluatorExceptionReportsCauseWithoutWrappingBusinessOutput() {
        Evaluator failure = (expression, input) -> {
            throw new IllegalArgumentException("wrapper", new IllegalStateException("script failed"));
        };

        TaskModel task = executeWithInput(Map.of(Inline.INPUT_EXPRESSION, "ignored"),
                Map.of(GraalJSEvaluator.NAME, failure));

        assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, task.getStatus());
        assertFalse(task.getStatus().isRetriable());
        assertEquals("script failed", task.getReasonForInCompletion());
        assertNull(task.getOutputData());
    }

    private TaskModel executeWithInput(Map<String, Object> input, Map<String, Evaluator> evaluators) {
        TaskModel task = new TaskModel();
        task.setTaskId("inline-1");
        task.setInputData(input);
        task.setOutputData(Map.of("stale", true));
        new Inline(evaluators).start(new WorkflowModel(), task, null);
        return task;
    }

    private static Stream<Arguments> scriptResults() {
        return Stream.of(
                Arguments.of("[{id: 1}]", List.of(Map.of("id", 1))),
                Arguments.of("[]", List.of()),
                Arguments.of("42", 42),
                Arguments.of("false", false),
                Arguments.of("null", null),
                Arguments.of("function scriptFun() { throw new Error('must be called explicitly'); }", null),
                Arguments.of("let calls = 0; function scriptFun() { return ++calls; }\nscriptFun();", 1));
    }

    private TaskModel executeInline(Object result) {
        Evaluator evaluator = (expression, input) -> result;
        Inline inline = new Inline(Map.of(GraalJSEvaluator.NAME, evaluator));
        TaskModel task = new TaskModel();
        task.setInputData(Map.of(Inline.INPUT_EXPRESSION, "ignored"));

        inline.start(new WorkflowModel(), task, null);
        return task;
    }
}
