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

    private TaskMapperContext preparedContext(Map<String, Object> input) {
        WorkflowTask definition = WorkflowTask.builder().name("mapping").type(TaskType.TASK_TYPE_MAPPING)
                .taskReferenceName("mapping_ref").inputParameters(Map.of(
                        "payload", "${missing.value}", "paramsConfig", "${missing.config}"))
                .build();
        return TaskMapperContext.builder().workflowModel(new WorkflowModel()).workflowTask(definition)
                .taskId("mapping-1").taskInput(input).build();
    }
}
