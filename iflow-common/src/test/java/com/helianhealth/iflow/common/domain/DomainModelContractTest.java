package com.helianhealth.iflow.common.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.helianhealth.iflow.common.domain.metadata.task.TaskType;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowDef;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowTask;
import com.helianhealth.iflow.common.domain.run.Task;
import com.helianhealth.iflow.common.domain.run.Workflow;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DomainModelContractTest {

    @Test
    void builtInClassificationExcludesBusinessTasksRegardlessOfExecutionMode() {
        for (String type : List.of("DECISION", "SWITCH", "FORK", "JOIN", "EXCLUSIVE_JOIN", "DO_WHILE")) {
            assertTrue(TaskType.isBuiltIn(type), type);
        }
        for (String type : List.of("HTTP", "JDBC", "WEBSERVICE", "SPI", "INLINE", "MAPPING", "CUSTOM")) {
            assertFalse(TaskType.isBuiltIn(type), type);
        }
    }

    @Test
    void obsoleteNodeTimeoutFieldsDoNotChangeWorkflowTimeoutOrRemainInSavedDefinitions() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        WorkflowDef definition = mapper.readValue("""
                {"timeoutSeconds":120,"timeoutPolicy":"ALERT_ONLY","tasks":[
                  {"name":"request","taskReferenceName":"request","type":"HTTP",
                   "timeoutSeconds":30,"timeoutPolicy":"RETRY","retryCount":2,"startDelay":0}
                ]}
                """, WorkflowDef.class);
        assertEquals(120, definition.getTimeoutSeconds());
        assertEquals(WorkflowDef.TimeoutPolicy.ALERT_ONLY, definition.getTimeoutPolicy());
        var json = mapper.valueToTree(definition);
        assertFalse(json.get("tasks").get(0).has("timeoutSeconds"));
        assertFalse(json.get("tasks").get(0).has("timeoutPolicy"));
        assertEquals(2, definition.getTasks().getFirst().getRetryCount());
        assertEquals(0, definition.getTasks().getFirst().getStartDelay());
    }

    @Test
    void taskPoliciesUseNodeDefaultsAndKeepExplicitValuesAcrossJsonAndBuilder() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        WorkflowTask defaults = mapper.readValue("{}", WorkflowTask.class);
        assertEquals(0, defaults.getRetryCount());
        assertEquals(0, defaults.getRetryDelaySeconds());
        assertEquals(WorkflowTask.RetryLogic.FIXED, defaults.getRetryLogic());
        assertEquals(0, defaults.getStartDelay());
        assertEquals(new WorkflowTask(), defaults);
        assertEquals(defaults, WorkflowTask.builder().build());
        assertEquals(defaults, mapper.readValue("""
                {"retryCount":null,"retryDelaySeconds":null,"retryLogic":null,"startDelay":null}
                """, WorkflowTask.class));
        assertEquals(defaults, WorkflowTask.builder().retryCount(null).retryDelaySeconds(null)
                .retryLogic(null).startDelay(null).build());

        WorkflowTask configured = WorkflowTask.builder().retryCount(0).retryDelaySeconds(2)
                .retryLogic(WorkflowTask.RetryLogic.EXPONENTIAL_BACKOFF).startDelay(5).build();
        WorkflowTask restored = mapper.readValue(mapper.writeValueAsString(configured), WorkflowTask.class);
        assertEquals(configured, restored);
        assertEquals(configured.hashCode(), restored.hashCode());
        assertEquals(0, restored.getRetryCount());
        assertTrue(restored.toString().contains("startDelay=5"));
        restored.setStartDelay(0);
        assertNotEquals(configured, restored);
        assertEquals(defaults, mapper.readValue(mapper.writeValueAsString(defaults), WorkflowTask.class));
    }

    @Test
    void taskPoliciesRejectOutOfRangeNumbersAndInvalidEnumValues() {
        ObjectMapper mapper = new ObjectMapper();
        for (String field : List.of("retryCount", "retryDelaySeconds", "startDelay")) {
            for (String value : List.of("true", "[]", "2147483648")) {
                assertThrows(JsonMappingException.class,
                        () -> mapper.readValue("{\"" + field + "\":" + value + "}", WorkflowTask.class));
            }
        }
        for (String field : List.of("retryLogic")) {
            for (String value : List.of("\"UNKNOWN\"", "1", "\"0\"", "true")) {
                assertThrows(JsonMappingException.class,
                        () -> mapper.readValue("{\"" + field + "\":" + value + "}", WorkflowTask.class));
            }
        }
    }

    @Test
    void reusedDefinitionBuilderCreatesIndependentDefaultCollections() {
        WorkflowDef.WorkflowDefBuilder builder = WorkflowDef.builder().route("patient-sync");
        WorkflowDef first = builder.build();
        WorkflowDef second = builder.build();
        first.getTags().add("HIS");
        first.getTasks().add(WorkflowTask.builder().taskReferenceName("request").build());
        first.getInputParameters().add("patientId");
        first.getOutputParameters().put("result", "${request.output}");

        assertEquals(1, second.getVersion());
        assertEquals(WorkflowDef.TimeoutPolicy.TIME_OUT_WF, second.getTimeoutPolicy());
        assertTrue(second.getTags().isEmpty());
        assertTrue(second.getTasks().isEmpty());
        assertTrue(second.getInputParameters().isEmpty());
        assertTrue(second.getOutputParameters().isEmpty());
        assertEquals(new WorkflowDef(), WorkflowDef.builder().build());
        assertEquals(new WorkflowTask(), WorkflowTask.builder().build());
        assertEquals(new Workflow(), Workflow.builder().build());
        assertEquals(new Task(), Task.builder().build());
    }

    @Test
    void builderDistinguishesExplicitNullFromOmittedDefaults() {
        WorkflowDef definition = WorkflowDef.builder().tasks(null).tags(null).timeoutPolicy(null).build();
        WorkflowTask task = WorkflowTask.builder().inputParameters(null).loopOver(null).build();

        assertNull(definition.getTasks());
        assertNull(definition.getTags());
        assertNull(definition.getTimeoutPolicy());
        assertNull(task.getInputParameters());
        assertNull(task.getLoopOver());
        assertNull(Task.builder().outputData(null).build().getOutputData());
        assertNull(Workflow.builder().output(null).build().getOutput());
    }

    @Test
    void taskEqualityUsesEffectiveDefinitionName() {
        Task fallbackName = Task.builder().taskType("HTTP").build();
        Task explicitName = Task.builder().taskType("HTTP").taskDefName("HTTP").build();

        assertEquals(fallbackName, explicitName);
        assertEquals(fallbackName.hashCode(), explicitName.hashCode());
        assertTrue(fallbackName.toString().contains("taskDefName=HTTP"));
        explicitName.setTaskDefName("custom-request");
        assertNotEquals(fallbackName, explicitName);
    }

    @Test
    void runtimeViewsKeepRawJsonOutputsAndBooleanProperties() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Task task = Task.builder().taskType("HTTP").outputData(List.of("a", "b"))
                .retried(true).executed(true).build();
        Task restored = mapper.readValue(mapper.writeValueAsString(task), Task.class);

        assertEquals(task, restored);
        assertEquals(List.of("a", "b"), restored.getOutputData());
        assertTrue(restored.isRetried());
        assertTrue(restored.isExecuted());
        for (Object output : List.of("text", 7, false, List.of(1, 2), Map.of("value", 1))) {
            Workflow workflow = Workflow.builder().output(output).build();
            var json = mapper.readTree(mapper.writeValueAsString(workflow));
            assertEquals(mapper.valueToTree(output), json.get("output"));
        }
        var json = mapper.readTree(mapper.writeValueAsString(Workflow.builder().output(null).build()));
        assertTrue(json.get("output").isNull());
    }
}
