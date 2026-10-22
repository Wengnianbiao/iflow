package com.helianhealth.iflow.tasks.spi;

import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import com.helianhealth.iflow.common.extension.ExtensionLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
class SpiTaskUnitTest {

    private static final String ONLINE_EXTENSION_NAME = "node-online-java17-test";

    private RecordingSpiExtensionRouter extensionRouter;
    private SpiTask spiTask;
    private final WorkflowModel workflow = new WorkflowModel();

    @BeforeEach
    void setup() {
        extensionRouter = new RecordingSpiExtensionRouter();
        spiTask = new SpiTask(extensionRouter);
    }

    @AfterEach
    void cleanupLoader() {
        ExtensionLoader<InterfaceRemoteInvoker> loader =
                ExtensionLoader.getExtensionLoader(InterfaceRemoteInvoker.class);
        if (loader.hasExtension(ONLINE_EXTENSION_NAME)) {
            loader.removeDynamicExtension(ONLINE_EXTENSION_NAME);
        }
    }

    @Test
    void executesAsynchronously() {
        assertTrue(spiTask.isAsync());
    }

    @Test
    void extractSpiInputReadsOnlyNestedMap() {
        Map<String, Object> input = Map.of(
                SpiTaskInput.REMOTE_EXTENSION, "my-invoker",
                SpiTaskInput.SPI_INPUT,
                Map.of("patientName", "Alice"));

        assertEquals("my-invoker", SpiTaskInput.extractRemoteExtension(input));
        assertEquals("Alice", SpiTaskInput.extractSpiInput(input).get("patientName"));
        assertEquals(1, SpiTaskInput.extractSpiInput(input).size());
    }

    @Test
    void blankRemoteExtensionUsesDefaultExtension() {
        SpiExtensionRouter router = new SpiExtensionRouter();
        Object result = router.invoke(null, Map.of("patientName", "Alice"));
        assertEquals(Map.of(), result);
    }

    @Test
    void executeRoutesByRemoteExtension() {
        extensionRouter.result = Map.of("code", "OK");

        TaskModel task = new TaskModel();
        Map<String, Object> input = new HashMap<>();
        input.put(SpiTaskInput.REMOTE_EXTENSION, "my-invoker");
        input.put(SpiTaskInput.SPI_INPUT, Map.of("patientName", "Alice"));
        task.setInputData(input);

        spiTask.start(workflow, task, null);

        assertEquals("my-invoker", extensionRouter.remoteExtension);
        assertEquals(Map.of("patientName", "Alice"), extensionRouter.spiInput);
        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals(Map.of("code", "OK"), task.getOutputData());
    }

    @Test
    void executeWithEmptyRemoteExtensionCompletesWithEmptyResult() {
        extensionRouter.result = Map.of();

        TaskModel task = new TaskModel();
        task.setInputData(Map.of(SpiTaskInput.SPI_INPUT, Map.of()));

        spiTask.start(workflow, task, null);

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals(Map.of(), task.getOutputData());
    }

    @Test
    void compilesEmbeddedJava17ExtensionAtInvocationTime() {
        String sourceCode =
                "package com.helianhealth.agent.extension;\n"
                        + "import com.helianhealth.iflow.tasks.spi.InterfaceRemoteInvoker;\n"
                        + "import java.util.Map;\n"
                        + "public class NodeOnlineJava17Invoker implements InterfaceRemoteInvoker {\n"
                        + "  record Result(String value) {}\n"
                        + "  public Map<String,Object> remoteInvoke(Map<String,Object> input) {\n"
                        + "    var result = new Result(String.valueOf(input.get(\"value\")));\n"
                        + "    return Map.of(\"value\", result.value());\n"
                        + "  }\n"
                        + "}\n";
        SpiTaskInput.ExtensionDefinition definition =
                new SpiTaskInput.ExtensionDefinition(
                        ONLINE_EXTENSION_NAME,
                        SpiTaskInput.MODE_ONLINE,
                        "com.helianhealth.agent.extension.NodeOnlineJava17Invoker",
                        sourceCode);

        Object result =
                new SpiExtensionRouter()
                        .invoke(ONLINE_EXTENSION_NAME, definition, Map.of("value", "java17"));

        assertEquals(Map.of("value", "java17"), result);
    }

    private static final class RecordingSpiExtensionRouter extends SpiExtensionRouter {
        private String remoteExtension;
        private SpiTaskInput.ExtensionDefinition definition;
        private Map<String, Object> spiInput;
        private Object result = Map.of();
        private RuntimeException failure;
        private int invocations;

        @Override
        public Object invoke(
                String remoteExtension,
                SpiTaskInput.ExtensionDefinition definition,
                Map<String, Object> spiInput) {
            this.remoteExtension = remoteExtension;
            this.definition = definition;
            this.spiInput = spiInput;
            invocations++;
            if (failure != null) {
                throw failure;
            }
            return result;
        }
    }

    @Test
    void trimsExtensionAndPassesOnlyBusinessInputToRouter() {
        Map<String, Object> businessInput = new HashMap<>(Map.of("patientId", "P001"));
        TaskModel task = new TaskModel();
        task.setInputData(Map.of(
                SpiTaskInput.REMOTE_EXTENSION, "  patient-invoker  ",
                SpiTaskInput.SPI_INPUT, businessInput));

        spiTask.start(workflow, task, null);

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals("patient-invoker", extensionRouter.remoteExtension);
        assertEquals(Map.of("patientId", "P001"), extensionRouter.spiInput);
        extensionRouter.spiInput.put("newField", true);
        assertFalse(businessInput.containsKey("newField"));
        assertNull(extensionRouter.definition);
    }

    @Test
    void passesNormalizedEmbeddedProjectDefinitionAtExecutionTime() {
        TaskModel task = new TaskModel();
        task.setInputData(Map.of(
                SpiTaskInput.REMOTE_EXTENSION, "  project-invoker  ",
                SpiTaskInput.EXTENSION_MODE, "project",
                SpiTaskInput.CLASS_NAME, "  com.example.PatientInvoker  ",
                SpiTaskInput.SPI_INPUT, Map.of("patientId", "P001")));

        spiTask.start(workflow, task, null);

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals(new SpiTaskInput.ExtensionDefinition(
                "project-invoker", SpiTaskInput.MODE_PROJECT,
                "com.example.PatientInvoker", null), extensionRouter.definition);
        assertFalse(extensionRouter.definition.online());
        assertEquals(1, extensionRouter.invocations);
    }

    @Test
    void invalidBusinessInputFailsBeforeInvokingExtension() {
        TaskModel task = new TaskModel();
        task.setInputData(Map.of(SpiTaskInput.SPI_INPUT, Map.of(7, "invalid-key")));

        spiTask.start(workflow, task, null);

        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertEquals("SPI inputParameters.spiInput keys must be strings",
                task.getReasonForInCompletion());
        assertEquals(0, extensionRouter.invocations);
        assertEquals(Map.of(), task.getOutputData());
    }

    @Test
    void missingBusinessInputFailsBeforeInvokingExtension() {
        TaskModel task = new TaskModel();
        task.setInputData(Map.of(SpiTaskInput.REMOTE_EXTENSION, "patient-invoker"));

        spiTask.start(workflow, task, null);

        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertEquals("SPI inputParameters.spiInput must resolve to a Map",
                task.getReasonForInCompletion());
        assertEquals(0, extensionRouter.invocations);
    }

    @Test
    void incompleteOnlineDefinitionFailsBeforeInvokingExtension() {
        TaskModel task = new TaskModel();
        task.setInputData(Map.of(
                SpiTaskInput.REMOTE_EXTENSION, "online-invoker",
                SpiTaskInput.EXTENSION_MODE, SpiTaskInput.MODE_ONLINE,
                SpiTaskInput.CLASS_NAME, "com.example.PatientInvoker",
                SpiTaskInput.SPI_INPUT, Map.of()));

        spiTask.start(workflow, task, null);

        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertEquals("SPI inputParameters.sourceCode is required in ONLINE mode",
                task.getReasonForInCompletion());
        assertEquals(0, extensionRouter.invocations);
    }

    @ParameterizedTest
    @MethodSource("invocationFailures")
    void invocationFailureRecordsDiagnosticWithoutWrappingBusinessOutput(
            RuntimeException failure, String expectedReason) {
        extensionRouter.failure = failure;
        TaskModel task = new TaskModel();
        task.setInputData(Map.of(SpiTaskInput.SPI_INPUT, Map.of()));

        spiTask.start(workflow, task, null);

        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertEquals(expectedReason, task.getReasonForInCompletion());
        assertEquals(Map.of(), task.getOutputData());
        assertEquals(1, extensionRouter.invocations);
    }

    private static Stream<Arguments> invocationFailures() {
        return Stream.of(
                Arguments.of(new IllegalArgumentException("remote endpoint unavailable"),
                        "remote endpoint unavailable"),
                Arguments.of(new IllegalStateException("extension invocation failed",
                        new IllegalArgumentException("patient rejected")), "patient rejected"));
    }

    @Test
    void unknownExtensionFailsTaskWithDiagnostic() {
        TaskModel task = new TaskModel();
        task.setInputData(Map.of(
                SpiTaskInput.REMOTE_EXTENSION, "missing-spi-unit-test-extension",
                SpiTaskInput.SPI_INPUT, Map.of()));

        new SpiTask(new SpiExtensionRouter()).start(workflow, task, null);

        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertTrue(task.getReasonForInCompletion().contains("missing-spi-unit-test-extension"));
        assertEquals(Map.of(), task.getOutputData());
    }

    @ParameterizedTest
    @MethodSource("rawOutputs")
    void extensionCanReturnAnyJsonValue(Object output) {
        extensionRouter.result = output;
        TaskModel task = new TaskModel();
        task.setInputData(Map.of(SpiTaskInput.SPI_INPUT, Map.of()));

        spiTask.start(workflow, task, null);

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals(output, task.getOutputData());
    }

    private static Stream<Arguments> rawOutputs() {
        return Stream.of(
                Arguments.of(List.of(1, 2)), Arguments.of(List.of()),
                Arguments.of("response"), Arguments.of(42), Arguments.of(false),
                Arguments.of((Object) null));
    }

    @Test
    void compilesEmbeddedExtensionReturningObjectArrayValue() {
        String sourceCode = """
                package com.helianhealth.agent.extension;
                import com.helianhealth.iflow.tasks.spi.InterfaceRemoteInvoker;
                import java.util.Map;
                import java.util.List;
                public class NodeObjectOutputInvoker implements InterfaceRemoteInvoker {
                    public Object remoteInvoke(Map<String,Object> input) {
                        return List.of(input.get("value"));
                    }
                }
                """;
        SpiTaskInput.ExtensionDefinition definition = new SpiTaskInput.ExtensionDefinition(
                ONLINE_EXTENSION_NAME, SpiTaskInput.MODE_ONLINE,
                "com.helianhealth.agent.extension.NodeObjectOutputInvoker", sourceCode);

        Object output = new SpiExtensionRouter().invoke(
                ONLINE_EXTENSION_NAME, definition, Map.of("value", "array-item"));

        assertEquals(List.of("array-item"), output);
    }
}
