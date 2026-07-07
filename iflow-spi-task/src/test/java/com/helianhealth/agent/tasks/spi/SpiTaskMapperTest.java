package com.helianhealth.agent.tasks.spi;

import com.helianhealth.agent.common.domain.metadata.task.TaskType;
import com.helianhealth.agent.common.domain.metadata.workflow.WorkflowTask;
import com.helianhealth.agent.core.execution.mapper.TaskMapperContext;
import com.helianhealth.agent.core.model.TaskModel;
import com.helianhealth.agent.core.model.WorkflowModel;
import com.helianhealth.agent.core.utils.ParametersUtils;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
