package com.helianhealth.agent.tasks.parammap;

import com.helianhealth.agent.core.execution.evaluators.GraalJSEvaluator;
import com.helianhealth.agent.core.model.TaskModel;
import com.helianhealth.agent.core.model.WorkflowModel;
import com.helianhealth.agent.tasks.mapping.ParamMapTransformer;
import com.helianhealth.agent.tasks.mapping.ParamMappingDef;
import com.helianhealth.agent.tasks.mapping.ParamMappingEngine;
import com.helianhealth.agent.tasks.mapping.ParamMappingTask;
import com.helianhealth.agent.tasks.mapping.enums.MappingType;
import com.helianhealth.agent.tasks.mapping.enums.ParamType;
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
        engine = new ParamMappingEngine(new ParamMapTransformer(new GraalJSEvaluator()));
    }

    @Test
    void mapsInputFieldsFromPayloadScriptParameter() {
        ParamMappingDef directMapping =
                ParamMappingDef.builder()
                        .targetParamKey("patientCode")
                        .targetParamType(ParamType.STRING)
                        .mappingType(MappingType.NAME)
                        .sourceParamKey("patientCode")
                        .build();
        ParamMappingDef namedInputMapping =
                ParamMappingDef.builder()
                        .targetParamKey("namedPatientCode")
                        .targetParamType(ParamType.STRING)
                        .mappingType(MappingType.NAME)
                        .sourceParamKey("payload.patientCode")
                        .build();

        Map<String, Object> input =
                Map.of(
                        ParamMappingEngine.INPUT_PARAMS_CONFIG,
                        List.of(directMapping, namedInputMapping),
                        ParamMappingEngine.INPUT_PAYLOAD,
                        Map.of("patientCode", "2508190082"));

        Object result = engine.map(input);

        assertInstanceOf(Map.class, result);
        assertEquals("2508190082", ((Map<?, ?>) result).get("patientCode"));
        assertEquals("2508190082", ((Map<?, ?>) result).get("namedPatientCode"));
    }

    @Test
    void mergesNamedInputsIntoOneTopLevelSourceObject() {
        ParamMappingDef patientMapping =
                ParamMappingDef.builder()
                        .targetParamKey("patientCode")
                        .targetParamType(ParamType.STRING)
                        .mappingType(MappingType.NAME)
                        .sourceParamKey("patientCode")
                        .build();
        ParamMappingDef hospitalMapping =
                ParamMappingDef.builder()
                        .targetParamKey("hospitalCode")
                        .targetParamType(ParamType.STRING)
                        .mappingType(MappingType.NAME)
                        .sourceParamKey("hospital.code")
                        .build();

        Map<String, Object> result =
                engine.map(
                        Map.of(
                                ParamMappingEngine.INPUT_PARAMS_CONFIG,
                                List.of(patientMapping, hospitalMapping),
                                "patientCode",
                                "P001",
                                "hospital",
                                Map.of("code", "H001")));

        assertEquals(Map.of("patientCode", "P001", "hospitalCode", "H001"), result);
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
    void keepsScalarPayloadAsAUserDefinedTopLevelInput() {
        ParamMappingDef mapping =
                ParamMappingDef.builder()
                        .targetParamKey("payload")
                        .targetParamType(ParamType.STRING)
                        .mappingType(MappingType.NAME)
                        .sourceParamKey("payload")
                        .build();

        Map<String, Object> result =
                engine.map(
                        Map.of(
                                ParamMappingEngine.INPUT_PARAMS_CONFIG,
                                List.of(mapping),
                                ParamMappingEngine.INPUT_PAYLOAD,
                                "plain-value"));

        assertEquals("plain-value", result.get("payload"));
    }

    @Test
    void rejectsMissingMappingInputs() {
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
                                                List.of(mapping))));

        assertEquals("at least one mapping input is required", error.getMessage());
    }

    @Test
    void taskWritesMappedFieldsToOutputRoot() {
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
        task.addOutput("stale", true);
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("workflow-1");

        new ParamMappingTask(engine).execute(workflow, task, null);

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals(Map.of("patientCode", "2508190082"), task.getOutputData());
        assertFalse(task.getOutputData().containsKey("result"));
        assertFalse(task.getOutputData().containsKey("output"));
        assertFalse(task.getOutputData().containsKey("stale"));
    }
}
