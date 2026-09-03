package com.helianhealth.agent.core.execution.tasks;

import com.helianhealth.agent.core.execution.evaluators.Evaluator;
import com.helianhealth.agent.core.execution.evaluators.GraalJSEvaluator;
import com.helianhealth.agent.core.model.TaskModel;
import com.helianhealth.agent.core.model.WorkflowModel;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InlineUnitTest {

    @Test
    void wrapsScalarResultInStableResultField() {
        TaskModel task = executeInline("ok");

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals(Map.of("result", "ok"), task.getOutputData());
    }

    @Test
    void keepsMapResultUnderStableResultField() {
        Map<String, Object> scriptResult = Map.of("code", "OK");

        TaskModel task = executeInline(scriptResult);

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals(Map.of("result", scriptResult), task.getOutputData());
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
