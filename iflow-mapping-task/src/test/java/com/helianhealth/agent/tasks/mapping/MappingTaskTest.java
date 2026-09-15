package com.helianhealth.agent.tasks.mapping;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.agent.core.execution.evaluators.GraalJSEvaluator;
import com.helianhealth.agent.core.model.TaskModel;
import com.helianhealth.agent.core.model.WorkflowModel;
import com.helianhealth.agent.tasks.mapping.enums.MappingType;
import com.helianhealth.agent.tasks.mapping.enums.ParamType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class MappingTaskTest {

    private MappingTask mappingTask;

    @BeforeEach
    void setup() {
        mappingTask = new MappingTask(new ParamMapTransformer(new GraalJSEvaluator()), new ObjectMapper());
    }

    @Test
    void mapsFieldsFromPayloadObject() {
        ParamMappingConfig mapping =
                ParamMappingConfig.builder()
                        .targetParamKey("patientCode")
                        .targetParamType(ParamType.STRING)
                        .mappingType(MappingType.NAME)
                        .sourceParamKey("$.patientCode")
                        .build();

        Object result =
                executeMapping(
                        Map.of(
                                MappingTask.INPUT_PARAMS_CONFIG,
                                List.of(mapping),
                                MappingTask.INPUT_PAYLOAD,
                                Map.of("patientCode", "2508190082")));

        assertInstanceOf(Map.class, result);
        assertEquals("2508190082", ((Map<?, ?>) result).get("patientCode"));
    }

    @Test
    void mapsExpressionFromPayloadObject() {
        ParamMappingConfig mapping =
                ParamMappingConfig.builder()
                        .targetParamKey("code")
                        .targetParamType(ParamType.STRING)
                        .mappingType(MappingType.EXPRESSION)
                        .mappingRule("$.patientCode")
                        .build();

        Map<?, ?> result =
                executeMapping(
                        Map.of(
                                MappingTask.INPUT_PARAMS_CONFIG,
                                List.of(mapping),
                                MappingTask.INPUT_PAYLOAD,
                                Map.of("patientCode", "P001")));

        assertEquals("P001", result.get("code"));
    }

    @Test
    void mapsNamedParametersStoredOnPayload() {
        ParamMappingConfig patientMapping =
                ParamMappingConfig.builder()
                        .targetParamKey("patientCode")
                        .targetParamType(ParamType.STRING)
                        .mappingType(MappingType.NAME)
                        .sourceParamKey("$.patientCode")
                        .build();
        ParamMappingConfig hospitalMapping =
                ParamMappingConfig.builder()
                        .targetParamKey("hospitalCode")
                        .targetParamType(ParamType.STRING)
                        .mappingType(MappingType.NAME)
                        .sourceParamKey("$.hospital.code")
                        .build();

        Map<?, ?> result =
                executeMapping(
                        Map.of(
                                MappingTask.INPUT_PARAMS_CONFIG,
                                List.of(patientMapping, hospitalMapping),
                                MappingTask.INPUT_PAYLOAD,
                                Map.of(
                                        "patientCode",
                                        "P001",
                                        "hospital",
                                        Map.of("code", "H001"))));

        assertEquals(Map.of("patientCode", "P001", "hospitalCode", "H001"), result);
    }

    @Test
    void convertsMapParamsConfigToDomain() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("targetParamKey", "status");
        config.put("targetParamType", "STRING");
        config.put("mappingType", "CONSTANT");
        config.put("mappingRule", "OK");

        Map<?, ?> result =
                executeMapping(
                        Map.of(
                                MappingTask.INPUT_PARAMS_CONFIG,
                                List.of(config),
                                MappingTask.INPUT_PAYLOAD,
                                Map.of()));

        assertEquals("OK", result.get("status"));
    }

    @Test
    void mapsConstantValue() {
        ParamMappingConfig mapping =
                ParamMappingConfig.builder()
                        .targetParamKey("status")
                        .targetParamType(ParamType.STRING)
                        .mappingType(MappingType.CONSTANT)
                        .mappingRule("OK")
                        .build();

        Map<?, ?> result =
                executeMapping(
                        Map.of(
                                MappingTask.INPUT_PARAMS_CONFIG,
                                List.of(mapping),
                                MappingTask.INPUT_PAYLOAD,
                                Map.of()));

        assertEquals("OK", result.get("status"));
    }

    @Test
    void mapsNestedObjectFromPayload() {
        ParamMappingConfig child =
                ParamMappingConfig.builder()
                        .targetParamKey("name")
                        .targetParamType(ParamType.STRING)
                        .mappingType(MappingType.NAME)
                        .sourceParamKey("$.data.patientName")
                        .build();
        ParamMappingConfig root =
                ParamMappingConfig.builder()
                        .targetParamKey("patient")
                        .targetParamType(ParamType.OBJECT)
                        .mappingType(MappingType.OBJECT)
                        .childParams(List.of(child))
                        .build();

        Map<?, ?> result =
                executeMapping(
                        Map.of(
                                MappingTask.INPUT_PARAMS_CONFIG,
                                List.of(root),
                                MappingTask.INPUT_PAYLOAD,
                                Map.of("data", Map.of("patientName", "张三"))));
        Map<?, ?> patient = (Map<?, ?>) result.get("patient");
        assertEquals("张三", patient.get("name"));
    }

    @Test
    void readsScalarPayloadDirectly() {
        ParamMappingConfig mapping =
                ParamMappingConfig.builder()
                        .targetParamKey("payload")
                        .targetParamType(ParamType.STRING)
                        .mappingType(MappingType.NAME)
                        .sourceParamKey("$")
                        .build();

        Map<?, ?> result =
                executeMapping(
                        Map.of(
                                MappingTask.INPUT_PARAMS_CONFIG,
                                List.of(mapping),
                                MappingTask.INPUT_PAYLOAD,
                                "plain-value"));

        assertEquals("plain-value", result.get("payload"));
    }

    @Test
    void rejectsMissingPayload() {
        ParamMappingConfig mapping =
                ParamMappingConfig.builder()
                        .targetParamKey("value")
                        .targetParamType(ParamType.STRING)
                        .mappingType(MappingType.NAME)
                        .sourceParamKey("$.value")
                        .build();
        TaskModel task =
                execute(
                        Map.of(
                                MappingTask.INPUT_PARAMS_CONFIG,
                                List.of(mapping)));

        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertEquals("payload is required", task.getReasonForInCompletion());
    }

    @Test
    void writesMappedFieldsToOutputRoot() {
        ParamMappingConfig mapping =
                ParamMappingConfig.builder()
                        .targetParamKey("patientCode")
                        .targetParamType(ParamType.STRING)
                        .mappingType(MappingType.NAME)
                        .sourceParamKey("$.patientCode")
                        .build();
        TaskModel task =
                execute(
                        Map.of(
                                MappingTask.INPUT_PARAMS_CONFIG,
                                List.of(mapping),
                                MappingTask.INPUT_PAYLOAD,
                                Map.of("patientCode", "2508190082")));
        task.addOutput("stale", true);
        mappingTask.execute(newWorkflow(), task, null);

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals(Map.of("patientCode", "2508190082"), task.getOutputData());
        assertFalse(task.outputAsMap().containsKey("result"));
        assertFalse(task.outputAsMap().containsKey("output"));
        assertFalse(task.outputAsMap().containsKey("stale"));
    }

    private Map<?, ?> executeMapping(Map<String, Object> input) {
        TaskModel task = execute(input);
        assertEquals(TaskModel.Status.COMPLETED, task.getStatus(), task.getReasonForInCompletion());
        return (Map<?, ?>) task.getOutputData();
    }

    private TaskModel execute(Map<String, Object> input) {
        TaskModel task = new TaskModel();
        task.setTaskId("mapping-task");
        task.setInputData(input);
        mappingTask.execute(newWorkflow(), task, null);
        return task;
    }

    private static WorkflowModel newWorkflow() {
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("workflow-1");
        return workflow;
    }
}
