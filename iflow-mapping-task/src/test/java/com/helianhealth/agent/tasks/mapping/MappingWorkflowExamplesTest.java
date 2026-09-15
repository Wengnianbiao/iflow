package com.helianhealth.agent.tasks.mapping;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.agent.core.execution.evaluators.GraalJSEvaluator;
import com.helianhealth.agent.core.model.TaskModel;
import com.helianhealth.agent.core.model.WorkflowModel;
import com.helianhealth.agent.core.utils.ParametersUtils;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MappingWorkflowExamplesTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final GraalJSEvaluator evaluator = new GraalJSEvaluator();
    private final MappingTask mappingTask = new MappingTask(new ParamMapTransformer(evaluator), mapper);
    private final ParametersUtils parameters = new ParametersUtils();

    @Test
    void migratedCreateArchivesPreservesPatientRequestAndSerializedArray() throws Exception {
        JsonNode definition = readExample("CreateArchives.json");
        WorkflowModel workflow = new WorkflowModel();
        workflow.setInput(Map.of("data", Map.of(
                "patientName", "测试患者", "sex", "1", "cardType", "1", "marriage", "2",
                "address", "测试地址", "doctorInfo", Map.of("userName", "测试医生", "number", "D1"))));
        TaskModel mapped = runMapping(definition.path("tasks").get(0), workflow);
        workflow.setTasks(List.of(mapped));
        Map<?, ?> patient = (Map<?, ?>) mapped.outputAsMap().get("patient");
        assertEquals("测试患者", patient.get("name"));
        assertEquals(1, patient.get("sex"));
        assertEquals("01", patient.get("idCardCode"));
        assertEquals("20", patient.get("maritalStatus"));
        assertEquals("GHIS", patient.get("visitTenant"));
        assertEquals("测试医生", patient.get("author"));
        assertEquals(Map.of("addressDetail", "测试地址"), patient.get("addressDTO"));
        assertFalse(mapped.outputAsMap().containsKey("encryptData"));

        JsonNode serializeDef = definition.path("tasks").get(1);
        assertEquals("INLINE", serializeDef.path("type").asText());
        Map<String, Object> serializeInput = resolveInput(serializeDef, workflow);
        Object serialized = evaluator.evaluate((String) serializeInput.get("expression"), serializeInput);
        assertInstanceOf(String.class, serialized);
        assertEquals(mapper.valueToTree(List.of(patient)), mapper.readTree((String) serialized));
        TaskModel serializedTask = new TaskModel();
        serializedTask.setReferenceTaskName(serializeDef.path("taskReferenceName").asText());
        serializedTask.setOutputData(serialized);
        serializedTask.setStatus(TaskModel.Status.COMPLETED);
        workflow.setTasks(List.of(mapped, serializedTask));

        Map<?, ?> body = (Map<?, ?>) resolveInput(definition.path("tasks").get(2), workflow).get("body");
        assertEquals(serialized, body.get("encryptData"));
        assertEquals(mapped.outputAsMap().get("timestamp"), body.get("timestamp"));
        assertEquals("setPatientInfo", body.get("methodName"));
    }

    @Test
    void migratedResponseMappingHandlesSuccessAndFailureFromOriginalPayload() throws Exception {
        JsonNode responseDef = readExample("CreateArchives.json").path("tasks").get(3);
        for (boolean success : List.of(true, false)) {
            TaskModel http = new TaskModel();
            http.setReferenceTaskName("call_his_ref");
            http.setStatus(TaskModel.Status.COMPLETED);
            http.setOutputData(Map.of("body", Map.of("code", success ? "0" : "500",
                    "message", "三方失败", "body", Map.of("status", success,
                            "detail", "接口拒绝", "data", Map.of("mpi", "P1", "medicalCardId", "V1")))));
            WorkflowModel workflow = new WorkflowModel();
            workflow.setTasks(List.of(http));
            var mapped = runMapping(responseDef, workflow).outputAsMap();
            assertEquals(success ? "200" : "-1", mapped.get("code"));
            assertEquals(success ? "成功" : "接口拒绝", mapped.get("message"));
            assertEquals(Map.of("outPatientCode", "P1", "visitNo", "V1"), mapped.get("rsp"));
        }
    }

    @Test
    void arrayExampleBuildsExpectedThirdPartyStructure() throws Exception {
        JsonNode definition = readExample("mapping_arrays_workflow.json");
        WorkflowModel workflow = new WorkflowModel();
        workflow.setInput(Map.of("patientId", "P1", "items", List.of(Map.of("code", "A", "qty", 2))));
        var output = runMapping(definition.path("tasks").get(0), workflow).getOutputData();
        assertEquals(Map.of("hospitalCode", "H001", "details", List.of(Map.of(
                "itemCode", "A", "quantity", 2, "patientId", "P1", "enabled", true)),
                "patients", List.of(Map.of("id", "P1"))), output);
    }

    @Test
    void syncApplySplitsItemsByServiceProviderType() throws Exception {
        JsonNode definition = readExample("sync_pacs_lis_new_apply_workflow.json");
        JsonNode splitDef = definition.path("tasks").get(0);
        assertEquals("MAPPING", splitDef.path("type").asText());

        Map<String, Object> lis = new LinkedHashMap<>();
        lis.put("applyId", "L1");
        lis.put("serviceProviderType", "LIS");
        lis.put("feeItemName", "血常规");
        Map<String, Object> pacs = new LinkedHashMap<>();
        pacs.put("applyId", "P1");
        pacs.put("serviceProviderType", "PACS");
        pacs.put("feeItemName", "CT");
        Map<String, Object> other = new LinkedHashMap<>();
        other.put("applyId", "O1");
        other.put("serviceProviderType", "OTHER");

        WorkflowModel workflow = new WorkflowModel();
        workflow.setInput(Map.of("data", Map.of("items", List.of(lis, pacs, other, Map.of("applyId", "X1")))));
        var output = runMapping(splitDef, workflow).outputAsMap();
        assertEquals(List.of(lis), output.get("lisItems"));
        assertEquals(List.of(pacs), output.get("pacsItems"));
    }

    private JsonNode readExample(String file) throws Exception {
        return mapper.readTree(Path.of("..", "docs", "workflows", file).toFile());
    }

    private Map<String, Object> resolveInput(JsonNode task, WorkflowModel workflow) {
        Map<String, Object> raw = mapper.convertValue(task.path("inputParameters"), new TypeReference<>() {});
        return parameters.getTaskInput(raw, workflow, "example");
    }

    private TaskModel runMapping(JsonNode definition, WorkflowModel workflow) {
        TaskModel task = new TaskModel();
        task.setReferenceTaskName(definition.path("taskReferenceName").asText());
        task.setInputData(resolveInput(definition, workflow));
        mappingTask.execute(workflow, task, null);
        assertEquals(TaskModel.Status.COMPLETED, task.getStatus(), task.getReasonForInCompletion());
        return task;
    }
}
