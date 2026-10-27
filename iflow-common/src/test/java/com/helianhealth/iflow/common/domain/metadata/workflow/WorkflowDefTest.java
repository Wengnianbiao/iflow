package com.helianhealth.iflow.common.domain.metadata.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowDefTest {

    @Test
    void timeoutDefaultsAndPoliciesRoundTripAndRejectInvalidJson() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        WorkflowDef missing = mapper.readValue("{}", WorkflowDef.class);
        WorkflowDef explicitNull = mapper.readValue("{\"timeoutPolicy\":null}", WorkflowDef.class);
        assertEquals(0, missing.getTimeoutSeconds());
        assertEquals(WorkflowDef.TimeoutPolicy.TIME_OUT_WF, missing.getTimeoutPolicy());
        assertEquals(WorkflowDef.TimeoutPolicy.TIME_OUT_WF, explicitNull.getTimeoutPolicy());
        assertEquals(WorkflowDef.TimeoutPolicy.ALERT_ONLY,
                mapper.readValue("{\"timeoutSeconds\":120,\"timeoutPolicy\":\"ALERT_ONLY\"}", WorkflowDef.class).getTimeoutPolicy());
        for (String json : List.of("{\"timeoutSeconds\":1.5}", "{\"timeoutSeconds\":\"60\"}", "{\"timeoutPolicy\":\"UNKNOWN\"}")) {
            assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class, () -> mapper.readValue(json, WorkflowDef.class));
        }
        assertEquals(-1, mapper.readValue("{\"timeoutSeconds\":-1}", WorkflowDef.class).getTimeoutSeconds(),
                "historical nonpositive budgets remain readable");
    }

    @Test
    void tagsTravelWithDefinitionJsonAndLegacyDefinitionsNeedNoMigration() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        WorkflowDef definition = WorkflowDef.builder().route("tagged").name("分类工作流")
                .tags(List.of("HIS", "门诊")).build();
        assertEquals(definition.getTags(), mapper.readValue(
                mapper.writeValueAsString(definition), WorkflowDef.class).getTags());
        assertTrue(mapper.readValue("{\"route\":\"legacy\"}", WorkflowDef.class).getTags().isEmpty());
        assertTrue(mapper.readValue("{\"route\":\"legacy\",\"tags\":null}", WorkflowDef.class).getTags().isEmpty());
        assertEquals(List.of("HIS", "门诊"), WorkflowDef.normalizeTags(
                Arrays.asList(" HIS ", "", null, "HIS", "门诊", "  ")));
    }

    @Test
    void routeAndVersionIdentifyDefinitionsIndependentlyOfNames() throws Exception {
        WorkflowDef first = WorkflowDef.builder().route("GetItemResult").version(1).name("获取检验结果").build();
        WorkflowDef sameName = WorkflowDef.builder().route("OtherRoute").version(1).name("获取检验结果").build();
        assertEquals("GetItemResult.1", first.key());
        assertEquals("OtherRoute.1", sameName.key());
        first.setName("新的中文名称");
        assertEquals("GetItemResult.1", first.key());
        first.setVersion(2);
        assertEquals("GetItemResult.2", first.key());
        ObjectMapper mapper = new ObjectMapper();
        var json = mapper.readTree(mapper.writeValueAsString(first));
        assertEquals("GetItemResult", json.get("route").asText());
        assertEquals("新的中文名称", json.get("name").asText());
        assertFalse(json.has("displayName"));
        assertEquals(first, mapper.treeToValue(json, WorkflowDef.class));
        assertTrue(WorkflowDef.isValidRoute("hl-open-api/GetItemResult"));
        assertFalse(WorkflowDef.isValidRoute("中文名称"));
    }

    @Test
    void inheritsAuditableFieldsWithoutOwnerApp() throws Exception {
        WorkflowDef def = new WorkflowDef();
        assertEquals(0L, def.getCreateTime());
        assertEquals(0L, def.getUpdateTime());

        def.setCreateTime(100L);
        def.setUpdateTime(200L);
        def.setCreatedBy("creator");
        def.setUpdatedBy("updater");

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(def);
        WorkflowDef restored = mapper.readValue(json, WorkflowDef.class);

        assertEquals(100L, restored.getCreateTime());
        assertEquals(200L, restored.getUpdateTime());
        assertEquals("creator", restored.getCreatedBy());
        assertEquals("updater", restored.getUpdatedBy());
        assertFalse(json.contains("ownerApp"));
    }

    @Test
    void getNextTaskReturnsFollowingTopLevelTaskAfterNestedSwitchBranch() {
        WorkflowTask branch =
                WorkflowTask.builder()
                        .name("branchTask")
                        .taskReferenceName("branchTask")
                        .type("NOOP")
                        .build();
        WorkflowTask switchTask =
                WorkflowTask.builder()
                        .name("sw1")
                        .taskReferenceName("sw1")
                        .type("SWITCH")
                        .decisionCases(Map.of("caseA", List.of(branch)))
                        .defaultCase(List.of())
                        .build();
        WorkflowTask after =
                WorkflowTask.builder()
                        .name("after")
                        .taskReferenceName("after")
                        .type("NOOP")
                        .build();

        WorkflowDef def =
                WorkflowDef.builder().route("demo").version(1).tasks(List.of(switchTask, after)).build();

        assertEquals("after", def.getNextTask("sw1").getTaskReferenceName());
        assertEquals("after", def.getNextTask("branchTask").getTaskReferenceName());
    }

    @Test
    void getNextTaskDecisionBranches() {
        WorkflowDef def = new WorkflowDef();
        def.setRoute("test_workflow");
        def.setVersion(1);
        def.getTasks().add(createWorkflowTask("simple_task_1"));
        def.getTasks().add(createWorkflowTask("simple_task_2"));

        WorkflowTask task3 = createWorkflowTask("decision_task_1");
        def.getTasks().add(task3);
        task3.setType("SWITCH");
        task3.setDefaultCase(List.of());
        task3.getDecisionCases()
                .put(
                        "Case1",
                        Arrays.asList(
                                createWorkflowTask("case_1_task_1"),
                                createWorkflowTask("case_1_task_2")));
        task3.getDecisionCases()
                .put(
                        "Case2",
                        Arrays.asList(
                                createWorkflowTask("case_2_task_1"),
                                createWorkflowTask("case_2_task_2")));
        task3.getDecisionCases()
                .put(
                        "Case3",
                        Collections.singletonList(
                                deciderTask(
                                        "decision_task_2",
                                        toMap("Case31", "case31_task_1", "case_31_task_2"),
                                        Collections.singletonList("case3_def_task"))));
        def.getTasks().add(createWorkflowTask("simple_task_3"));

        WorkflowTask next = def.getNextTask("simple_task_1");
        assertNotNull(next);
        assertEquals("simple_task_2", next.getTaskReferenceName());

        next = def.getNextTask("simple_task_2");
        assertNotNull(next);
        assertEquals(task3.getTaskReferenceName(), next.getTaskReferenceName());

        next = def.getNextTask("decision_task_1");
        assertNotNull(next);
        assertEquals("simple_task_3", next.getTaskReferenceName());

        next = def.getNextTask("case_1_task_1");
        assertNotNull(next);
        assertEquals("case_1_task_2", next.getTaskReferenceName());

        next = def.getNextTask("case_1_task_2");
        assertNotNull(next);
        assertEquals("simple_task_3", next.getTaskReferenceName());

        next = def.getNextTask("case3_def_task");
        assertNotNull(next);
        assertEquals("simple_task_3", next.getTaskReferenceName());

        next = def.getNextTask("case31_task_1");
        assertNotNull(next);
        assertEquals("case_31_task_2", next.getTaskReferenceName());
    }

    @Test
    void getNextTaskNestedConditional() {
        List<WorkflowTask> workflowTasks = new ArrayList<>(10);
        for (int i = 0; i < 10; i++) {
            workflowTasks.add(createWorkflowTask("junit_task_" + i));
        }

        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setRoute("COND_TASK_WF");

        WorkflowTask subCaseTask = new WorkflowTask();
        subCaseTask.setType("SWITCH");
        subCaseTask.setDefaultCase(List.of());
        subCaseTask.setName("case2");
        subCaseTask.setTaskReferenceName("case2");
        Map<String, List<WorkflowTask>> dcx = new HashMap<>();
        dcx.put("sc1", workflowTasks.subList(4, 5));
        dcx.put("sc2", workflowTasks.subList(5, 7));
        subCaseTask.setDecisionCases(dcx);

        WorkflowTask caseTask = new WorkflowTask();
        caseTask.setType("SWITCH");
        caseTask.setDefaultCase(List.of());
        caseTask.setName("case");
        caseTask.setTaskReferenceName("case");
        Map<String, List<WorkflowTask>> dc = new HashMap<>();
        dc.put("c1", Arrays.asList(workflowTasks.get(0), subCaseTask, workflowTasks.get(1)));
        dc.put("c2", Collections.singletonList(workflowTasks.get(3)));
        caseTask.setDecisionCases(dc);

        workflowDef.getTasks().add(caseTask);
        workflowDef.getTasks().addAll(workflowTasks.subList(8, 9));

        WorkflowTask nextTask = workflowDef.getNextTask("case");
        assertEquals("junit_task_8", nextTask.getTaskReferenceName());

        nextTask = workflowDef.getNextTask("junit_task_8");
        assertNull(nextTask);

        nextTask = workflowDef.getNextTask("junit_task_0");
        assertNotNull(nextTask);
        assertEquals("case2", nextTask.getTaskReferenceName());

        nextTask = workflowDef.getNextTask("case2");
        assertNotNull(nextTask);
        assertEquals("junit_task_1", nextTask.getTaskReferenceName());
    }

    @Test
    void getNextTaskDoWhileLastLoopTaskReturnsDoWhileTask() {
        WorkflowTask loopBody = createWorkflowTask("loop_task");
        WorkflowTask doWhile =
                WorkflowTask.builder()
                        .name("doWhile")
                        .taskReferenceName("doWhile")
                        .type("DO_WHILE")
                        .loopOver(new LinkedList<>(List.of(loopBody)))
                        .build();
        WorkflowTask after = createWorkflowTask("after");

        WorkflowDef def =
                WorkflowDef.builder().route("demo").version(1).tasks(List.of(doWhile, after)).build();

        assertEquals("doWhile", def.getNextTask("loop_task").getTaskReferenceName());
        assertEquals("after", def.getNextTask("doWhile").getTaskReferenceName());
    }

    @Test
    void getNextTaskTerminateReturnsNull() {
        WorkflowTask terminate =
                WorkflowTask.builder()
                        .name("term")
                        .taskReferenceName("term")
                        .type("TERMINATE")
                        .build();
        WorkflowDef def =
                WorkflowDef.builder()
                        .route("demo")
                        .version(1)
                        .tasks(List.of(terminate, createWorkflowTask("after")))
                        .build();

        assertNull(def.getNextTask("term"));
    }

    @Test
    void roundTripsLoopOverTaskArray() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String json =
                """
                {
                  "route": "demo",
                  "name": "演示工作流",
                  "version": 1,
                  "tasks": [{
                    "name": "t1",
                    "taskReferenceName": "t1",
                    "type": "DO_WHILE",
                    "loopCondition": "false",
                    "loopOver": [{
                      "name": "body",
                      "taskReferenceName": "body",
                      "type": "INLINE"
                    }]
                  }]
                }
                """;
        WorkflowDef def = mapper.readValue(json, WorkflowDef.class);
        assertNotNull(def.getTasks());
        assertEquals(1, def.getTasks().size());
        WorkflowTask loop = def.getTasks().get(0);
        assertEquals("false", loop.getLoopCondition());
        assertEquals(1, loop.getLoopOver().size());
        assertEquals("body", loop.getLoopOver().get(0).getTaskReferenceName());
        assertEquals("INLINE", loop.getLoopOver().get(0).getType());
        assertEquals(def, mapper.readValue(mapper.writeValueAsString(def), WorkflowDef.class));
    }

    @Test
    void loopOverDefaultsToEmptyAndPreservesExplicitNull() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        assertTrue(mapper.readValue("{}", WorkflowTask.class).getLoopOver().isEmpty());
        assertTrue(mapper.readValue("{\"loopOver\":[]}", WorkflowTask.class).getLoopOver().isEmpty());
        assertNull(mapper.readValue("{\"loopOver\":null}", WorkflowTask.class).getLoopOver());
    }

    @ParameterizedTest
    @ValueSource(strings = {"false", "true", "{}", "123", "\"invalid\""})
    void rejectsNonArrayLoopOver(String loopOver) {
        ObjectMapper mapper = new ObjectMapper();
        String json = "{\"tasks\":[{\"type\":\"DO_WHILE\",\"loopOver\":" + loopOver + "}]}";
        assertThrows(MismatchedInputException.class, () -> mapper.readValue(json, WorkflowDef.class));
    }

    private static WorkflowTask createWorkflowTask(String name) {
        WorkflowTask task = new WorkflowTask();
        task.setName(name);
        task.setTaskReferenceName(name);
        return task;
    }

    private static WorkflowTask deciderTask(
            String name, Map<String, List<String>> decisions, List<String> defaultTasks) {
        WorkflowTask task = createWorkflowTask(name);
        task.setType("SWITCH");
        decisions.forEach(
                (key, value) -> {
                    List<WorkflowTask> tasks = new LinkedList<>();
                    value.forEach(taskName -> tasks.add(createWorkflowTask(taskName)));
                    task.getDecisionCases().put(key, tasks);
                });
        List<WorkflowTask> tasks = new LinkedList<>();
        defaultTasks.forEach(defaultTask -> tasks.add(createWorkflowTask(defaultTask)));
        task.setDefaultCase(tasks);
        return task;
    }

    private static Map<String, List<String>> toMap(String key, String... values) {
        Map<String, List<String>> map = new HashMap<>();
        map.put(key, Arrays.asList(values));
        return map;
    }
}
