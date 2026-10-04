package com.helianhealth.iflow.tasks.spi;

import com.helianhealth.iflow.common.domain.metadata.task.TaskType;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowTask;
import com.helianhealth.iflow.core.execution.mapper.TaskMapperContext;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import com.helianhealth.iflow.core.utils.ParametersUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpiTaskMapperTest {

    private final SpiTaskMapper mapper = new SpiTaskMapper(new ParametersUtils());

    @Test
    void resolvesRemoteExtensionAndWholeSpiInputMapExpressions() {
        WorkflowTask workflowTask =
                spiTask(
                        Map.of(
                                SpiTaskInput.REMOTE_EXTENSION,
                                "${workflow.input.extension}",
                                SpiTaskInput.SPI_INPUT,
                                "${workflow.input.payload}"));

        TaskModel mapped =
                map(
                        workflowTask,
                        workflow(
                                Map.of(
                                        "extension", "patient-invoker",
                                        "payload", Map.of("patientId", "P001"))));

        assertEquals("patient-invoker", mapped.getInputData().get(SpiTaskInput.REMOTE_EXTENSION));
        assertEquals(
                "P001",
                ((Map<?, ?>) mapped.getInputData().get(SpiTaskInput.SPI_INPUT)).get("patientId"));
    }

    @Test
    void resolvesExpressionsNestedInsideSpiInput() {
        WorkflowTask workflowTask =
                spiTask(
                        Map.of(
                                SpiTaskInput.REMOTE_EXTENSION,
                                "patient-invoker",
                                SpiTaskInput.SPI_INPUT,
                                Map.of("patientId", "${workflow.input.patientId}")));

        TaskModel mapped = map(workflowTask, workflow(Map.of("patientId", "P002")));

        assertEquals(
                "P002",
                ((Map<?, ?>) mapped.getInputData().get(SpiTaskInput.SPI_INPUT)).get("patientId"));
    }

    @Test
    void keepsEmbeddedJavaSourceLiteralWhileResolvingRuntimeInput() {
        String sourceCode =
                "public class LiteralSource { String value = \"${do.not.resolve}\"; }";
        WorkflowTask workflowTask =
                spiTask(
                        Map.of(
                                SpiTaskInput.REMOTE_EXTENSION,
                                "node-invoker",
                                SpiTaskInput.EXTENSION_MODE,
                                SpiTaskInput.MODE_ONLINE,
                                SpiTaskInput.CLASS_NAME,
                                "com.example.NodeInvoker",
                                SpiTaskInput.SOURCE_CODE,
                                sourceCode,
                                SpiTaskInput.SPI_INPUT,
                                Map.of("patientId", "${workflow.input.patientId}")));

        TaskModel mapped = map(workflowTask, workflow(Map.of("patientId", "P017")));

        assertEquals(sourceCode, mapped.getInputData().get(SpiTaskInput.SOURCE_CODE));
        assertEquals(
                "P017",
                ((Map<?, ?>) mapped.getInputData().get(SpiTaskInput.SPI_INPUT)).get("patientId"));
    }

    @Test
    void rejectsSpiInputThatDoesNotResolveToMap() {
        WorkflowTask workflowTask =
                spiTask(
                        Map.of(
                                SpiTaskInput.REMOTE_EXTENSION,
                                "patient-invoker",
                                SpiTaskInput.SPI_INPUT,
                                "${workflow.input.patientId}"));

        IllegalArgumentException error =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> map(workflowTask, workflow(Map.of("patientId", "P003"))));

        assertEquals(
                "SPI inputParameters.spiInput must resolve to a Map", error.getMessage());
    }

    @Test
    void rejectsUnexpectedTopLevelInputParameters() {
        Map<String, Object> inputParameters = new HashMap<>();
        inputParameters.put(SpiTaskInput.REMOTE_EXTENSION, "patient-invoker");
        inputParameters.put(SpiTaskInput.SPI_INPUT, Map.of());
        inputParameters.put("patientId", "P004");

        IllegalArgumentException error =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> map(spiTask(inputParameters), workflow(Map.of())));

        assertEquals(
                "SPI inputParameters contains unsupported fields",
                error.getMessage());
    }

    @Test
    void preservesSchedulingRetryAndForkMetadata() {
        WorkflowTask definition = spiTask(Map.of(SpiTaskInput.SPI_INPUT, Map.of()));
        WorkflowModel workflow = workflow(Map.of());
        workflow.setWorkflowDefinition(com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowDef
                .builder().name("patient-workflow").build());
        workflow.setCorrelationId("patient-P001");
        long before = System.currentTimeMillis();

        List<TaskModel> tasks = mapper.getMappedTasks(TaskMapperContext.builder()
                .workflowModel(workflow).workflowTask(definition)
                .taskId("retry-task").retryCount(2).retryTaskId("previous-task")
                .forkTaskId("fork-task").build());

        assertEquals(TaskType.TASK_TYPE_SPI, mapper.getTaskType());
        assertEquals(1, tasks.size());
        TaskModel task = tasks.get(0);
        assertEquals(TaskType.TASK_TYPE_SPI, task.getTaskType());
        assertEquals(TaskModel.Status.SCHEDULED, task.getStatus());
        assertEquals("retry-task", task.getTaskId());
        assertEquals("invoke_spi", task.getTaskDefName());
        assertEquals("invoke_spi_ref", task.getReferenceTaskName());
        assertEquals("wf-1", task.getWorkflowInstanceId());
        assertEquals("patient-workflow", task.getWorkflowType());
        assertEquals("patient-P001", task.getCorrelationId());
        assertEquals(2, task.getRetryCount());
        assertEquals("previous-task", task.getRetriedTaskId());
        assertEquals("fork-task", task.getForkTaskId());
        assertSame(definition, task.getWorkflowTask());
        assertTrue(task.getScheduledTime() >= before);
        assertFalse(task.isExecuted());
    }

    @Test
    void rejectsMissingBusinessInput() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> map(spiTask(Map.of()), workflow(Map.of())));

        assertEquals("SPI inputParameters.spiInput is required", error.getMessage());
    }

    @Test
    void rejectsResolvedMapWithNonStringKeys() {
        WorkflowTask definition = spiTask(Map.of(
                SpiTaskInput.SPI_INPUT, "${workflow.input.payload}"));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> map(definition, workflow(Map.of("payload", Map.of(17, "value")))));

        assertEquals("SPI inputParameters.spiInput keys must be strings", error.getMessage());
    }

    @Test
    void acceptsProjectDefinitionWithoutOnlineSourceCode() {
        WorkflowTask definition = spiTask(Map.of(
                SpiTaskInput.REMOTE_EXTENSION, "project-invoker",
                SpiTaskInput.EXTENSION_MODE, "project",
                SpiTaskInput.CLASS_NAME, "com.example.PatientInvoker",
                SpiTaskInput.SPI_INPUT, Map.of("patientId", "${workflow.input.patientId}")));

        TaskModel task = map(definition, workflow(Map.of("patientId", "P018")));

        assertEquals("project", task.getInputData().get(SpiTaskInput.EXTENSION_MODE));
        assertEquals("com.example.PatientInvoker", task.getInputData().get(SpiTaskInput.CLASS_NAME));
        assertFalse(task.getInputData().containsKey(SpiTaskInput.SOURCE_CODE));
        assertEquals(Map.of("patientId", "P018"), task.getInputData().get(SpiTaskInput.SPI_INPUT));
    }

    @Test
    void resolvesUpstreamObjectOutputWithoutMutatingDefinition() {
        Map<String, Object> rawBusinessInput = new HashMap<>(Map.of(
                "patientId", "${load_patient_ref.output.id}",
                "visits", "${load_patient_ref.output.visits}"));
        WorkflowTask definition = spiTask(Map.of(SpiTaskInput.SPI_INPUT, rawBusinessInput));
        WorkflowModel workflow = workflow(Map.of());
        TaskModel previous = new TaskModel();
        previous.setReferenceTaskName("load_patient_ref");
        previous.setStatus(TaskModel.Status.COMPLETED);
        previous.setOutputData(Map.of("id", "P019", "visits", List.of("V1", "V2")));
        workflow.setTasks(List.of(previous));

        TaskModel task = map(definition, workflow);

        assertEquals(Map.of("patientId", "P019", "visits", List.of("V1", "V2")),
                task.getInputData().get(SpiTaskInput.SPI_INPUT));
        assertEquals("${load_patient_ref.output.id}", rawBusinessInput.get("patientId"));
        assertNull(SpiTaskInput.extractRemoteExtension(task.getInputData()));
    }

    @ParameterizedTest
    @MethodSource("invalidEmbeddedDefinitions")
    void rejectsInvalidEmbeddedDefinitions(Map<String, Object> definition, String expectedMessage) {
        Map<String, Object> input = new HashMap<>(definition);
        input.put(SpiTaskInput.SPI_INPUT, Map.of());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> map(spiTask(input), workflow(Map.of())));

        assertEquals(expectedMessage, error.getMessage());
    }

    private static Stream<Arguments> invalidEmbeddedDefinitions() {
        return Stream.of(
                Arguments.of(Map.of(SpiTaskInput.EXTENSION_MODE, "ONLINE"),
                        "SPI ONLINE implementation requires className and sourceCode"),
                Arguments.of(Map.of(SpiTaskInput.CLASS_NAME, "com.example.Invoker"),
                        "SPI inputParameters.remoteExtension is required for an embedded implementation"),
                Arguments.of(Map.of(SpiTaskInput.REMOTE_EXTENSION, "default",
                                SpiTaskInput.CLASS_NAME, "com.example.Invoker"),
                        "SPI inputParameters.remoteExtension 'default' is reserved"),
                Arguments.of(Map.of(SpiTaskInput.REMOTE_EXTENSION, "invoker",
                                SpiTaskInput.CLASS_NAME, "com.example.Invoker",
                                SpiTaskInput.EXTENSION_MODE, "UNKNOWN"),
                        "SPI inputParameters.extensionMode must be ONLINE or PROJECT"),
                Arguments.of(Map.of(SpiTaskInput.REMOTE_EXTENSION, "invoker",
                                SpiTaskInput.SOURCE_CODE, "public class Invoker {}"),
                        "SPI inputParameters.className is required"),
                Arguments.of(Map.of(SpiTaskInput.REMOTE_EXTENSION, "invoker",
                                SpiTaskInput.CLASS_NAME, "com.example.Invoker"),
                        "SPI inputParameters.sourceCode is required in ONLINE mode"));
    }

    private WorkflowTask spiTask(Map<String, Object> inputParameters) {
        return WorkflowTask.builder()
                .name("invoke_spi")
                .taskReferenceName("invoke_spi_ref")
                .type(TaskType.TASK_TYPE_SPI)
                .inputParameters(inputParameters)
                .build();
    }

    private WorkflowModel workflow(Map<String, Object> input) {
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("wf-1");
        workflow.setInput(input);
        return workflow;
    }

    private TaskModel map(WorkflowTask workflowTask, WorkflowModel workflow) {
        return mapper.getMappedTasks(
                        TaskMapperContext.builder()
                                .workflowModel(workflow)
                                .workflowTask(workflowTask)
                                .taskId("task-1")
                                .build())
                .get(0);
    }
}
