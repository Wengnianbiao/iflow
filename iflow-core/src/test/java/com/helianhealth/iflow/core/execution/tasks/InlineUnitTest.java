package com.helianhealth.iflow.core.execution.tasks;

import com.helianhealth.iflow.core.execution.evaluators.Evaluator;
import com.helianhealth.iflow.core.execution.evaluators.GraalJSEvaluator;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InlineUnitTest {

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
        Inline inline = new Inline(Map.of(GraalJSEvaluator.NAME, new GraalJSEvaluator()));
        TaskModel task = new TaskModel();
        task.setInputData(Map.of(Inline.INPUT_EXPRESSION, expression));

        inline.execute(new WorkflowModel(), task, null);

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals(expected, task.getOutputData());
    }

    private static Stream<Arguments> scriptResults() {
        return Stream.of(
                Arguments.of("[{id: 1}]", List.of(Map.of("id", 1))),
                Arguments.of("[]", List.of()),
                Arguments.of("42", 42),
                Arguments.of("false", false),
                Arguments.of("null", null));
    }

    private TaskModel executeInline(Object result) {
        Evaluator evaluator = (expression, input) -> result;
        Inline inline = new Inline(Map.of(GraalJSEvaluator.NAME, evaluator));
        TaskModel task = new TaskModel();
        task.setInputData(Map.of(Inline.INPUT_EXPRESSION, "ignored"));

        inline.execute(new WorkflowModel(), task, null);
        return task;
    }
}
