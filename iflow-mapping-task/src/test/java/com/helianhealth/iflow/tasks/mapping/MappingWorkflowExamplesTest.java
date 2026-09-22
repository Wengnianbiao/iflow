package com.helianhealth.iflow.tasks.mapping;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.iflow.core.execution.evaluators.GraalJSEvaluator;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import com.helianhealth.iflow.core.utils.ParametersUtils;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
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
        assertEquals(List.of("pacs_loop_ref", "sync_ecg_ref"),
                mapper.convertValue(definition.path("tasks").get(2).path("joinOn"), new TypeReference<List<String>>() {}));

        Map<String, Object> lis = new LinkedHashMap<>();
        lis.put("applyId", "L1");
        lis.put("serviceProviderType", "LIS");
        lis.put("feeItemName", "血常规");
        Map<String, Object> pacs = new LinkedHashMap<>();
        pacs.put("applyId", "P1");
        pacs.put("serviceProviderType", "PACS");
        pacs.put("feeItemName", "CT");
        Map<String, Object> ecgByType = new LinkedHashMap<>();
        ecgByType.put("applyId", "E1");
        ecgByType.put("serviceProviderType", "ECG");
        ecgByType.put("feeItemName", "常规心电图检查");
        Map<String, Object> ecgByName = new LinkedHashMap<>();
        ecgByName.put("applyId", "E2");
        ecgByName.put("serviceProviderType", "PACS");
        ecgByName.put("feeItemName", "常规心电图检查");
        Map<String, Object> other = new LinkedHashMap<>();
        other.put("applyId", "O1");
        other.put("serviceProviderType", "OTHER");

        WorkflowModel workflow = new WorkflowModel();
        workflow.setInput(Map.of("data", Map.of("items", List.of(lis, pacs, ecgByType, ecgByName, other, Map.of("applyId", "X1")))));
        var output = runMapping(splitDef, workflow).outputAsMap();
        assertEquals(List.of(pacs), output.get("pacsItems"));
        assertEquals(List.of(ecgByType, ecgByName), output.get("ecgItems"));
        assertEquals(3, ((Number) output.get("skippedItemCount")).intValue());
        assertFalse(output.containsKey("lisItems"));

        Map<String, Object> pacsByDepartType = new LinkedHashMap<>();
        pacsByDepartType.put("applyId", "P2");
        pacsByDepartType.put("departType", "PACS");
        Map<String, Object> ecgByDepartType = new LinkedHashMap<>();
        ecgByDepartType.put("applyId", "E3");
        ecgByDepartType.put("departType", "ECG");
        workflow.setInput(Map.of("data", Map.of("items", List.of(lis, pacsByDepartType, ecgByDepartType))));
        var byDepartType = runMapping(splitDef, workflow).outputAsMap();
        assertEquals(List.of(pacsByDepartType), byDepartType.get("pacsItems"));
        assertEquals(List.of(ecgByDepartType), byDepartType.get("ecgItems"));
        assertEquals(1, ((Number) byDepartType.get("skippedItemCount")).intValue());
    }

    @Test
    void ecgApplyMappingBuildsHelianCheckRequest() throws Exception {
        JsonNode definition = readExample("sync_pacs_lis_new_apply_workflow.json");
        JsonNode mappingDef = definition.path("tasks").get(1).path("forkTasks").get(1).get(0);
        assertEquals("MAPPING", mappingDef.path("type").asText());
        assertEquals("HTTP", definition.path("tasks").get(1).path("forkTasks").get(1).get(1).path("type").asText());

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("applyId", "80000025001");
        item.put("idPatientFeeItem", "158314");
        item.put("interfaceCode", "ECG001");
        item.put("feeItemName", "常规心电图检查");
        item.put("priceYuan", "35.0");
        item.put("factPriceYuan", "35.0");
        item.put("ddInterfaceCode1", "311");
        item.put("departName", "心电室");
        item.put("number", "2004011");
        item.put("userName", "张三");

        Map<String, Object> patient = new LinkedHashMap<>();
        patient.put("hisPatientId", "4754989");
        patient.put("visitNo", "2100628331");
        patient.put("patientCode", "82601230009");
        patient.put("patientName", "李四");
        patient.put("age", 45);
        patient.put("sex", "1");
        patient.put("phone", "13900001234");
        patient.put("identitycard", "330326197806151234");
        patient.put("registertime", "2026-01-23 09:30:00");

        TaskModel split = new TaskModel();
        split.setReferenceTaskName("split_apply_ref");
        split.setStatus(TaskModel.Status.COMPLETED);
        split.setOutputData(Map.of("ecgItems", List.of(item)));

        WorkflowModel workflow = new WorkflowModel();
        workflow.setInput(Map.of("data", Map.of("patientInfo", patient, "doctorInfo", Map.of())));
        workflow.setTasks(List.of(split));

        var output = runMapping(mappingDef, workflow).outputAsMap();
        assertEquals("4754989", output.get("patientId"));
        assertEquals("2100628331", output.get("clinicId"));
        assertEquals("82601230009", output.get("patientCode"));
        assertEquals("李四", output.get("name"));
        assertEquals("45", output.get("age"));
        assertEquals("男", output.get("sex"));
        assertEquals("13900001234", output.get("phone"));
        assertEquals("330326197806151234", output.get("identityCard"));
        Map<String, Object> expectedApply = new LinkedHashMap<>();
        expectedApply.put("applyId", "80000025001");
        expectedApply.put("checkType", "ECG");
        expectedApply.put("itemId", "ECG001");
        expectedApply.put("itemName", "常规心电图检查");
        expectedApply.put("price", "35.0");
        expectedApply.put("factPrice", "35.0");
        expectedApply.put("applyDeptId", "tjzx");
        expectedApply.put("applyDeptName", "体检中心");
        expectedApply.put("executeDeptId", "311");
        expectedApply.put("executeDeptName", "心电室");
        expectedApply.put("applyUserId", "2004011");
        expectedApply.put("applyUserName", "张三");
        expectedApply.put("applyDate", "2026-01-23 09:30:00");
        assertEquals(List.of(expectedApply), output.get("applyList"));
    }

    @Test
    void pacsSoapBuilderSignsPwdAndWrapsExamineregisterData() throws Exception {
        JsonNode definition = readExample("sync_pacs_lis_new_apply_workflow.json");
        JsonNode buildDef = definition.path("tasks").get(1).path("forkTasks").get(0).get(0).path("loopOver").get(0);
        String expression = buildDef.path("inputParameters").path("expression").asText();
        assertTrue(expression.contains("Crypto.md5"));
        assertTrue(expression.contains("examineregister"));

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("applyId", "SQ20260920001");
        item.put("feeItemName", "胸部CT平扫");
        item.put("interfaceCode", "CT001");
        item.put("departName", "放射科");
        item.put("ddInterfaceCode1", "0201");
        item.put("priceYuan", "120.00");
        item.put("number", "YS0011");
        item.put("userName", "申请医生A");
        item.put("idPatientFeeItem", "158314");

        Map<String, Object> patient = new LinkedHashMap<>();
        patient.put("patientName", "张三");
        patient.put("sex", "1");
        patient.put("age", 35);
        patient.put("birthday", "1990-01-01");
        patient.put("identitycard", "330100199001011234");
        patient.put("patientCode", "P20260920001");
        patient.put("phone", "13800138000");

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("appID", "demoApp");
        input.put("serviceUri", "http://example/Service.asmx");
        input.put("item", item);
        input.put("patientInfo", patient);
        input.put("doctorInfo", Map.of());

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) evaluator.evaluate(expression, input);
        String ymd = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String expectedPwd = HexFormat.of().formatHex(
                MessageDigest.getInstance("MD5").digest(("demoAppexamineregister" + ymd).getBytes(StandardCharsets.UTF_8)));
        assertEquals(expectedPwd, result.get("pwd"));
        assertEquals("http://example/Service.asmx", result.get("serviceUri"));
        String soap = String.valueOf(result.get("soapXml"));
        assertTrue(soap.contains("<tem:method>examineregister</tem:method>"));
        assertTrue(soap.contains("<tem:pwd>" + expectedPwd + "</tem:pwd>"));
        assertTrue(soap.contains("<![CDATA["));
        assertTrue(soap.contains("<SQDBH>SQ20260920001</SQDBH>"));
        assertTrue(soap.contains("<YLXMMC>胸部CT平扫</YLXMMC>"));
        assertTrue(soap.contains("<YLXMLX>CT</YLXMLX>"));
        assertTrue(soap.contains("<BRXB>M</BRXB>"));
        assertEquals("SQ20260920001", result.get("applyId"));
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
