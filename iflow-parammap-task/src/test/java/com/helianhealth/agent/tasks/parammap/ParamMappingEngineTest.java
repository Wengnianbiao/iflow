package com.helianhealth.agent.tasks.parammap;

import com.helianhealth.agent.common.enums.MappingType;
import com.helianhealth.agent.common.enums.ParamType;
import com.helianhealth.agent.core.model.TaskModel;
import com.helianhealth.agent.core.model.WorkflowModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParamMappingEngineTest {

    private ParamMappingEngine engine;

    @BeforeEach
    void setup() {
        engine = new ParamMappingEngine(new ParamMapTransformer());
    }

    @Test
    void mapsInputFieldsFromPayloadScriptParameter() {
        ParamMappingDef mapping =
                ParamMappingDef.builder()
                        .targetParamKey("patientCode")
                        .targetParamType(ParamType.STRING)
                        .mappingType(MappingType.NAME)
                        .sourceParamKey("patientCode")
                        .build();

        Map<String, Object> input =
                Map.of(
                        ParamMappingEngine.INPUT_PARAMS_CONFIG,
                        List.of(mapping),
                        ParamMappingEngine.INPUT_PAYLOAD,
                        Map.of("patientCode", "2508190082"));

        Object result = engine.map(input);

        assertInstanceOf(Map.class, result);
        assertEquals("2508190082", ((Map<?, ?>) result).get("patientCode"));
    }

    @Test
    void mapsConstantValue() {
        ParamMappingDef mapping =
                ParamMappingDef.builder()
                        .targetParamKey("status")
                        .targetParamType(ParamType.STRING)
                        .mappingType(MappingType.CONSTANT)
                        .mappingRule("OK")
                        .build();

        Map<String, Object> input =
                Map.of(
                        ParamMappingEngine.INPUT_PARAMS_CONFIG,
                        List.of(mapping),
                        ParamMappingEngine.INPUT_PAYLOAD,
                        Map.of());

        Object result = engine.map(input);

        assertEquals("OK", ((Map<?, ?>) result).get("status"));
    }

    @Test
    void mapsNestedObject() {
        ParamMappingDef child =
                ParamMappingDef.builder()
                        .targetParamKey("name")
                        .targetParamType(ParamType.STRING)
                        .mappingType(MappingType.NAME)
                        .sourceParamKey("patientName")
                        .build();
        ParamMappingDef root =
                ParamMappingDef.builder()
                        .targetParamKey("patient")
                        .targetParamType(ParamType.OBJECT)
                        .sourceParamType(ParamType.OBJECT)
                        .sourceParamKey("data")
                        .childParams(List.of(child))
                        .build();

        Map<String, Object> input =
                Map.of(
                        ParamMappingEngine.INPUT_PARAMS_CONFIG,
                        List.of(root),
                        ParamMappingEngine.INPUT_PAYLOAD,
                        Map.of("data", Map.of("patientName", "张三")));

        Map<?, ?> result = engine.map(input);
        Map<?, ?> patient = (Map<?, ?>) result.get("patient");
        assertEquals("张三", patient.get("name"));
    }

    @Test
    void rejectsNonObjectPayload() {
        ParamMappingDef mapping =
                ParamMappingDef.builder()
                        .targetParamKey("value")
                        .targetParamType(ParamType.STRING)
                        .mappingType(MappingType.NAME)
                        .sourceParamKey("value")
                        .build();

        IllegalArgumentException error =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                engine.map(
                                        Map.of(
                                                ParamMappingEngine.INPUT_PARAMS_CONFIG,
                                                List.of(mapping),
                                                ParamMappingEngine.INPUT_PAYLOAD,
                                                List.of("not", "an", "object"))));

        assertEquals("payload must resolve to a JSON object", error.getMessage());
    }

    @Test
    void taskWritesMappedValueToFixedOutputKey() {
        ParamMappingDef mapping =
                ParamMappingDef.builder()
                        .targetParamKey("patientCode")
                        .targetParamType(ParamType.STRING)
                        .mappingType(MappingType.NAME)
                        .sourceParamKey("patientCode")
                        .build();
        TaskModel task = new TaskModel();
        task.setTaskId("param-map-task");
        task.setInputData(
                Map.of(
                        ParamMappingEngine.INPUT_PARAMS_CONFIG,
                        List.of(mapping),
                        ParamMappingEngine.INPUT_PAYLOAD,
                        Map.of("patientCode", "2508190082")));
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("workflow-1");

        boolean executed = new ParamMappingTask(engine).execute(workflow, task, null);

        assertTrue(executed);
        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals(
                Map.of("patientCode", "2508190082"),
                task.getOutputData().get(ParamMappingTask.OUTPUT));
        assertFalse(task.getOutputData().containsKey("result"));
    }
}
