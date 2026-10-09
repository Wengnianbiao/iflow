package com.helianhealth.iflow.core.execution.mapper;

import com.helianhealth.iflow.common.domain.metadata.task.TaskType;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowTask;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import com.helianhealth.iflow.core.utils.ParametersUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParamMappingTaskMapperTest {

    private final ParamMappingTaskMapper mapper = new ParamMappingTaskMapper(new ParametersUtils());

    @Test
    void resolvesPayloadExpression() {
        List<Map<String, Object>> configs =
                List.of(Map.of("sourceParamKey", "$.patientCode", "targetParamKey", "patientCode",
                        "targetParamType", "STRING", "mappingType", "NAME"));
        WorkflowTask workflowTask =
                WorkflowTask.builder()
                        .name("map parameters")
                        .taskReferenceName("map_parameters_ref")
                        .type(TaskType.TASK_TYPE_MAPPING)
                        .inputParameters(
                                Map.of(
                                        ParamMappingTaskMapper.PAYLOAD, "${workflow.input.data}",
                                        ParamMappingTaskMapper.PARAMS_CONFIG, configs))
                        .build();
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("workflow-1");
        workflow.setInput(Map.of("data", Map.of("patientCode", "P001")));
        TaskMapperContext context =
                TaskMapperContext.builder()
                        .workflowModel(workflow)
                        .workflowTask(workflowTask)
                        .taskId("task-1")
                        .build();

        TaskModel task = mapper.getMappedTasks(context).get(0);

        assertEquals(TaskType.TASK_TYPE_MAPPING, mapper.getTaskType());
        assertEquals(Map.of("patientCode", "P001"), task.getInputData().get(ParamMappingTaskMapper.PAYLOAD));
        assertEquals(configs, task.getInputData().get(ParamMappingTaskMapper.PARAMS_CONFIG));
    }

    @Test
    void resolvesPayloadParameterObject() {
        List<Map<String, Object>> configs =
                List.of(Map.of("sourceParamKey", "$.patientCode", "targetParamKey", "patientCode",
                        "targetParamType", "STRING", "mappingType", "NAME"));
        WorkflowTask workflowTask =
                WorkflowTask.builder()
                        .name("map parameters")
                        .taskReferenceName("map_parameters_ref")
                        .type(TaskType.TASK_TYPE_MAPPING)
                        .inputParameters(
                                Map.of(
                                        ParamMappingTaskMapper.PAYLOAD,
                                        Map.of(
                                                "patientCode", "${workflow.input.patientCode}",
                                                "hospitalCode", "H001"),
                                        ParamMappingTaskMapper.PARAMS_CONFIG, configs))
                        .build();
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("workflow-1");
        workflow.setInput(Map.of("patientCode", "P001"));
        TaskMapperContext context =
                TaskMapperContext.builder()
                        .workflowModel(workflow)
                        .workflowTask(workflowTask)
                        .taskId("task-1")
                        .build();

        TaskModel task = mapper.getMappedTasks(context).get(0);

        assertEquals("P001", ((Map<?, ?>) task.getInputData().get(ParamMappingTaskMapper.PAYLOAD)).get("patientCode"));
        assertEquals("H001", ((Map<?, ?>) task.getInputData().get(ParamMappingTaskMapper.PAYLOAD)).get("hospitalCode"));
        assertEquals(configs, task.getInputData().get(ParamMappingTaskMapper.PARAMS_CONFIG));
    }

    @Test
    void rejectsDefinitionWithoutPayload() {
        WorkflowTask workflowTask =
                WorkflowTask.builder()
                        .name("map parameters")
                        .taskReferenceName("map_parameters_ref")
                        .type(TaskType.TASK_TYPE_MAPPING)
                        .inputParameters(
                                Map.of(
                                        ParamMappingTaskMapper.PARAMS_CONFIG,
                                        List.of(Map.of("targetParamKey", "value"))))
                        .build();
        TaskMapperContext context =
                TaskMapperContext.builder()
                        .workflowModel(new WorkflowModel())
                        .workflowTask(workflowTask)
                        .taskId("task-1")
                        .build();

        IllegalArgumentException error =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> mapper.getMappedTasks(context));

        assertEquals(
                "MAPPING inputParameters must contain payload and paramsConfig",
                error.getMessage());
    }

    @ParameterizedTest
    @NullSource
    @MethodSource("missingRequiredInputs")
    void rejectsNullOrIncompleteDefinitionInput(Map<String, Object> input) {
        WorkflowTask definition = WorkflowTask.builder().name("mapping").type(TaskType.TASK_TYPE_MAPPING)
                .taskReferenceName("mapping_ref").inputParameters(input).build();
        TaskMapperContext context = TaskMapperContext.builder().workflowModel(new WorkflowModel())
                .workflowTask(definition).taskId("mapping-1").build();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> mapper.getMappedTasks(context));

        assertEquals("MAPPING inputParameters must contain payload and paramsConfig", error.getMessage());
    }

    private static Stream<Map<String, Object>> missingRequiredInputs() {
        return Stream.of(Map.of(), Map.of("payload", Map.of()));
    }

    @Test
    void consumesPreparedInputWithoutReparsingBusinessTextAndCopiesConfigList() {
        List<Object> configs = new ArrayList<>(List.of(Map.of("targetParamKey", "name")));
        Map<String, Object> prepared = Map.of("payload", Map.of("name", "${literal.name}"),
                "paramsConfig", configs);
        TaskMapperContext context = preparedContext(prepared);

        TaskModel task = mapper.getMappedTasks(context).get(0);

        assertEquals(prepared, task.getInputData());
        assertNotSame(prepared, task.getInputData());
        assertNotSame(configs, task.getInputData().get("paramsConfig"));
        assertEquals(TaskModel.Status.IN_PROGRESS, task.getStatus());
        configs.clear();
        assertEquals(1, ((List<?>) task.getInputData().get("paramsConfig")).size());
    }

    @ParameterizedTest
    @NullSource
    @MethodSource("invalidConfigs")
    void rejectsInvalidResolvedConfig(Object config) {
        Map<String, Object> prepared = new HashMap<>();
        prepared.put("payload", Map.of());
        prepared.put("paramsConfig", config);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> mapper.getMappedTasks(preparedContext(prepared)));

        assertEquals("MAPPING inputParameters.paramsConfig must be a non-empty JSON array", error.getMessage());
    }

    private static Stream<Object> invalidConfigs() {
        return Stream.of(List.of(), Map.of("targetParamKey", "name"), "[]");
    }

    @Test
    void permitsExplicitNullPayloadForConstantMappings() {
        Map<String, Object> prepared = new HashMap<>();
        prepared.put("payload", null);
        prepared.put("paramsConfig", List.of(Map.of("targetParamKey", "name", "targetParamType", "STRING",
                "mappingType", "CONSTANT", "mappingRule", "constant")));

        TaskModel task = mapper.getMappedTasks(preparedContext(prepared)).get(0);

        assertTrue(task.getInputData().containsKey("payload"));
        assertNull(task.getInputData().get("payload"));
        assertEquals(TaskModel.Status.IN_PROGRESS, task.getStatus());
    }

    @ParameterizedTest
    @MethodSource("upstreamOutputs")
    void resolvesUpstreamOutputsWithoutWrappingOrChangingTheirJsonType(Object output) {
        var upstream = new TaskModel();
        upstream.setReferenceTaskName("source_ref");
        upstream.setStatus(TaskModel.Status.COMPLETED);
        upstream.setOutputData(output);
        var workflow = new WorkflowModel();
        workflow.setTasks(List.of(upstream));
        var configs = List.of(Map.of("targetParamKey", "value", "targetParamType", "STRING",
                "mappingType", "CONSTANT", "mappingRule", "OK"));

        TaskModel task = mapper.getMappedTasks(context(workflow, Map.of(
                "payload", "${source_ref.output}", "paramsConfig", configs))).get(0);

        assertTrue(task.getInputData().containsKey("payload"));
        assertEquals(output, task.getInputData().get("payload"));
        if (output != null && !(output instanceof Map<?, ?>) && !(output instanceof List<?>)) {
            assertEquals(output.getClass(), task.getInputData().get("payload").getClass());
        }
        assertEquals(output, upstream.getOutputData());
        assertEquals(TaskModel.Status.IN_PROGRESS, task.getStatus());
    }

    private static Stream<Object> upstreamOutputs() {
        return Stream.of(Map.of("code", "A"), List.of(Map.of("code", "A")), "plain", 17, true, null);
    }

    @ParameterizedTest
    @MethodSource("dynamicConfigReferences")
    void resolvesDynamicConfigArraysAndPreservesLocalPathsAndScripts(String reference) {
        var template = Map.of("targetParamType", "OBJECT", "mappingType", "OBJECT", "childParams", List.of(
                Map.of("targetParamKey", "code", "targetParamType", "STRING", "mappingType", "NAME", "sourceParamKey", "item.code"),
                Map.of("targetParamKey", "description", "targetParamType", "STRING", "mappingType", "EXPRESSION",
                        "mappingRule", "$.patientId + ':' + item.code")));
        var configs = new ArrayList<>(List.of(Map.of("targetParamKey", "rows", "targetParamType", "ARRAY",
                "mappingType", "ARRAY_MAP", "sourceParamKey", "$.items", "itemAlias", "item", "itemTemplate", template)));
        var configSource = new TaskModel();
        configSource.setReferenceTaskName("config_ref");
        configSource.setStatus(TaskModel.Status.COMPLETED);
        configSource.setOutputData(configs);
        var payload = Map.of("patientId", "P1", "items", List.of(Map.of("code", "A")));
        var workflow = new WorkflowModel();
        workflow.setInput(Map.of("config", configs, "payload", payload));
        workflow.setTasks(List.of(configSource));
        var input = Map.<String, Object>of("payload", "${workflow.input.payload}", "paramsConfig", reference);

        TaskModel task = mapper.getMappedTasks(context(workflow, input)).get(0);

        assertEquals(payload, task.getInputData().get("payload"));
        assertEquals(configs, task.getInputData().get("paramsConfig"));
        assertNotSame(configs, task.getInputData().get("paramsConfig"));
        ((List<?>) task.getInputData().get("paramsConfig")).clear();
        assertEquals(1, configs.size());
        assertEquals(reference, input.get("paramsConfig"));
    }

    private static Stream<String> dynamicConfigReferences() {
        return Stream.of("${workflow.input.config}", "${config_ref.output}");
    }

    @ParameterizedTest
    @NullSource
    @MethodSource("invalidConfigs")
    void rejectsDynamicConfigReferencesResolvingToInvalidValues(Object value) {
        var workflowInput = new HashMap<String, Object>();
        workflowInput.put("config", value);
        var workflow = new WorkflowModel();
        workflow.setInput(workflowInput);

        var failure = assertThrows(IllegalArgumentException.class,
                () -> mapper.getMappedTasks(context(workflow,
                        Map.of("payload", Map.of(), "paramsConfig", "${workflow.input.config}"))));

        assertEquals("MAPPING inputParameters.paramsConfig must be a non-empty JSON array", failure.getMessage());
    }

    @Test
    void resolvesOrchestrationReferencesInsideNestedMappingTemplates() {
        var fields = List.of(Map.of("targetParamKey", "hospital", "targetParamType", "STRING", "mappingType", "CONSTANT",
                "mappingRule", "${workflow.input.hospital}"),
                Map.of("targetParamKey", "code", "targetParamType", "STRING", "mappingType", "NAME", "sourceParamKey", "item.code"));
        var configs = List.of(Map.of("targetParamKey", "rows", "targetParamType", "ARRAY", "mappingType", "ARRAY_MAP",
                "sourceParamKey", "$.items", "itemAlias", "item", "itemTemplate", Map.of(
                        "targetParamType", "OBJECT", "mappingType", "OBJECT", "childParams", fields)));
        var workflow = new WorkflowModel();
        workflow.setInput(Map.of("hospital", "H001"));

        TaskModel task = mapper.getMappedTasks(context(workflow,
                Map.of("payload", Map.of(), "paramsConfig", configs))).get(0);

        var loop = (Map<?, ?>) ((List<?>) task.getInputData().get("paramsConfig")).get(0);
        var template = (Map<?, ?>) loop.get("itemTemplate");
        var resolvedFields = (List<?>) template.get("childParams");
        assertEquals("H001", ((Map<?, ?>) resolvedFields.get(0)).get("mappingRule"));
        assertEquals("item.code", ((Map<?, ?>) resolvedFields.get(1)).get("sourceParamKey"));
        assertEquals("$.items", loop.get("sourceParamKey"));
        assertEquals("${workflow.input.hospital}", fields.get(0).get("mappingRule"));
    }

    private TaskMapperContext context(WorkflowModel workflow, Map<String, Object> input) {
        var definition = WorkflowTask.builder().name("mapping").type(TaskType.TASK_TYPE_MAPPING)
                .taskReferenceName("mapping_ref").inputParameters(input).build();
        return TaskMapperContext.builder().workflowModel(workflow).workflowTask(definition)
                .taskId("mapping-1").build();
    }

    private TaskMapperContext preparedContext(Map<String, Object> input) {
        WorkflowTask definition = WorkflowTask.builder().name("mapping").type(TaskType.TASK_TYPE_MAPPING)
                .taskReferenceName("mapping_ref").inputParameters(Map.of(
                        "payload", "${missing.value}", "paramsConfig", "${missing.config}"))
                .build();
        return TaskMapperContext.builder().workflowModel(new WorkflowModel()).workflowTask(definition)
                .taskId("mapping-1").taskInput(input).build();
    }
}
