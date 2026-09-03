package com.helianhealth.agent.tasks.spi;

import com.helianhealth.agent.core.model.TaskModel;
import com.helianhealth.agent.core.model.WorkflowModel;
import com.helianhealth.agent.common.extension.AgentExtensionLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        AgentExtensionLoader<InterfaceRemoteInvoker> loader =
                AgentExtensionLoader.getExtensionLoader(InterfaceRemoteInvoker.class);
        if (loader.hasExtension(ONLINE_EXTENSION_NAME)) {
            loader.removeDynamicExtension(ONLINE_EXTENSION_NAME);
        }
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
        Map<String, Object> result = router.invoke(null, Map.of("patientName", "Alice"));
        assertTrue(result.isEmpty());
    }

    @Test
    void executeRoutesByRemoteExtension() {
        extensionRouter.result = Map.of("code", "OK");

        TaskModel task = new TaskModel();
        Map<String, Object> input = new HashMap<>();
        input.put(SpiTaskInput.REMOTE_EXTENSION, "my-invoker");
        input.put(SpiTaskInput.SPI_INPUT, Map.of("patientName", "Alice"));
        task.setInputData(input);

        spiTask.execute(workflow, task, null);

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

        spiTask.execute(workflow, task, null);

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertTrue(task.getOutputData().isEmpty());
    }

    @Test
    void compilesEmbeddedJava17ExtensionAtInvocationTime() {
        String sourceCode =
                "package com.helianhealth.agent.extension;\n"
                        + "import com.helianhealth.agent.tasks.spi.InterfaceRemoteInvoker;\n"
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

        Map<String, Object> result =
                new SpiExtensionRouter()
                        .invoke(ONLINE_EXTENSION_NAME, definition, Map.of("value", "java17"));

        assertEquals("java17", result.get("value"));
    }

    private static final class RecordingSpiExtensionRouter extends SpiExtensionRouter {
        private String remoteExtension;
        private Map<String, Object> spiInput;
        private Map<String, Object> result = Map.of();

        @Override
        public Map<String, Object> invoke(
                String remoteExtension,
                SpiTaskInput.ExtensionDefinition definition,
                Map<String, Object> spiInput) {
            this.remoteExtension = remoteExtension;
            this.spiInput = spiInput;
            return result;
        }
    }
}
