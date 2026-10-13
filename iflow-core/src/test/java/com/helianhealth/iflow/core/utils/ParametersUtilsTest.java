package com.helianhealth.iflow.core.utils;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ParametersUtilsTest {

    private final ParametersUtils parameters = new ParametersUtils(new ObjectMapper());

    @ParameterizedTest
    @ValueSource(strings = {"${parse.output[0]}", "${parse.output.id}",
            "${parse.output['id']}", "${parse.output[\"id\"]}", "${parse.output.}"})
    void incompatibleAndMalformedOutputPathsResolveToNull(String expression) {
        WorkflowModel workflow = withOutput("scalar");

        assertEquals(Collections.singletonMap("value", null),
                parameters.getTaskInput(Map.of("value", expression), workflow, "consumer"));
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
    void scalarInNestedObjectResolvesToNull() {
        assertEquals(Collections.singletonMap("id", null), parameters.getTaskInput(
                Map.of("id", "${parse.output.patient.id}"),
                withOutput(Map.of("patient", 42)), "consumer"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"${parse.output.id}", "${parse.output.missing.id}",
            "${parse.output.}", "${unknown.output.body.id}"})
    void replacementEntryPointUsesTheSameLenientReads(String expression) {
        assertEquals(Collections.singletonMap("value", null), parameters.replace(
                Map.of("value", expression), Map.of("parse", Map.of("output", 42))));
        assertEquals(Collections.singletonMap("value", null), parameters.replace(
                Map.of("value", expression), "{\"parse\":{\"output\":42}}"));
    }

    @Test
    void embeddedNullUsesConductorStringConversionAndOtherExpressionsStillResolve() {
        Map<String, Object> input = Map.of(
                "text", "before-${parse.output.missing}-after-${parse.output.id}",
                "nested", Map.of("values", List.of("${parse.output.id}", "${parse.output.}")),
                "literal", "$${parse.output.id}",
                "taskLocal", "$.id");
        Map<String, Object> resolved = parameters.getTaskInput(input, withOutput(Map.of("id", 7)), "consumer");

        assertEquals("before-null-after-7", resolved.get("text"));
        assertEquals(java.util.Arrays.asList(7, null), ((Map<?, ?>) resolved.get("nested")).get("values"));
        assertEquals("${parse.output.id}", resolved.get("literal"));
        assertEquals("$.id", resolved.get("taskLocal"));
        assertEquals("before-${parse.output.missing}-after-${parse.output.id}", input.get("text"));
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
