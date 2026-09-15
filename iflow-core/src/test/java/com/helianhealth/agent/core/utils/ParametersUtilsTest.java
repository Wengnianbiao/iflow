package com.helianhealth.agent.core.utils;

import com.helianhealth.agent.core.exception.TerminateWorkflowException;
import com.helianhealth.agent.core.model.TaskModel;
import com.helianhealth.agent.core.model.WorkflowModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ParametersUtilsTest {

    private final ParametersUtils parameters = new ParametersUtils();

    @ParameterizedTest
    @ValueSource(strings = {"${parse.output[0]}", "${parse.output.id}",
            "${parse.output['id']}", "${parse.output[\"id\"]}", "${parse.output.}"})
    void incompatibleOutputPathsReportExpression(String expression) {
        WorkflowModel workflow = withOutput("scalar");

        TerminateWorkflowException error = assertThrows(TerminateWorkflowException.class,
                () -> parameters.getTaskInput(Map.of("value", expression), workflow, "consumer"));

        assertTrue(error.getMessage().contains("parse.output"));
        assertTrue(error.getMessage().contains("consumer"));
    }

    @Test
    void explicitNullInputSurvivesTemplateClone() {
        Map<String, Object> resolved = parameters.getTaskInput(
                Collections.singletonMap("value", null), new WorkflowModel(), "consumer");
        assertTrue(resolved.containsKey("value"));
        assertNull(resolved.get("value"));
    }

    @Test
    void arrayWildcardAndFunctionPathsRemainAvailable() {
        WorkflowModel workflow = withOutput(List.of(Map.of("id", 1), Map.of("id", 2)));
        Map<String, Object> resolved = parameters.getTaskInput(Map.of(
                "ids", "${parse.output[*].id}",
                "first", "${parse.output[0]['id']}",
                "size", "${parse.output.length()}"), workflow, "consumer");
        assertEquals(List.of(1, 2), resolved.get("ids"));
        assertEquals(1, resolved.get("first"));
        assertEquals(2, resolved.get("size"));
    }

    @Test
    void scalarInNestedObjectCannotBeReadAsObject() {
        assertThrows(TerminateWorkflowException.class, () -> parameters.getTaskInput(
                Map.of("id", "${parse.output.patient.id}"),
                withOutput(Map.of("patient", 42)), "consumer"));
    }

    @Test
    void nullOutputMissingLeafAndUnexecutedBranchRemainNull() {
        assertEquals(Collections.singletonMap("value", null), parameters.getTaskInput(
                Map.of("value", "${parse.output}"), withOutput(null), "consumer"));
        assertEquals(Collections.singletonMap("value", null), parameters.getTaskInput(
                Map.of("value", "${parse.output.missing}"), withOutput(Map.of()), "consumer"));
        assertEquals(Collections.singletonMap("value", null), parameters.getTaskInput(
                Map.of("value", "${not_selected.output.body.id}"), withOutput(Map.of()), "consumer"));
    }

    private WorkflowModel withOutput(Object output) {
        TaskModel task = new TaskModel();
        task.setReferenceTaskName("parse");
        task.setOutputData(output);
        task.setStatus(TaskModel.Status.COMPLETED);
        WorkflowModel workflow = new WorkflowModel();
        workflow.setTasks(List.of(task));
        return workflow;
    }
}
