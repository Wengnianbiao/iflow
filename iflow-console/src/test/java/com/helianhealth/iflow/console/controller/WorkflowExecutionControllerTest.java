package com.helianhealth.iflow.console.controller;

import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowDef;
import com.helianhealth.iflow.console.model.response.WorkflowExecutionSummary;
import com.helianhealth.iflow.core.dao.ExecutionDAO;
import com.helianhealth.iflow.core.model.WorkflowModel;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkflowExecutionControllerTest {
    private final Map<List<Object>, Object> answers = new HashMap<>();
    private final Map<List<Object>, Integer> calls = new HashMap<>();
    private final ExecutionDAO dao = (ExecutionDAO) Proxy.newProxyInstance(
            ExecutionDAO.class.getClassLoader(), new Class<?>[]{ExecutionDAO.class}, (proxy, method, args) -> {
                var key = List.<Object>of(method.getName(), List.of(args));
                if (!answers.containsKey(key)) throw new AssertionError("Unexpected DAO call: " + key);
                calls.merge(key, 1, Integer::sum);
                return answers.get(key);
            });

    private void answer(String method, Object value, Object... args) {
        answers.put(List.of(method, List.of(args)), value);
    }
    private final WorkflowExecutionController controller = new WorkflowExecutionController(dao, null, null, null);

    @Test
    void mergesSelectedWorkflowsBeforeSortingAndPagination() {
        answer("getWorkflowsByType", List.of(workflow("a", "first", 10)), "first", 0L, 100L);
        answer("getWorkflowsByType", List.of(workflow("b", "second", 20)), "second", 0L, 100L);

        var page = controller.search(null, null, List.of("first", "second", "first"), null, null,
                null, null, 0L, 100L, 1, 1).getData();

        assertEquals(2, page.getTotal());
        assertEquals(List.of("b"), page.getRows().stream().map(WorkflowExecutionSummary::getWorkflowId).toList());
        assertEquals(1, calls.get(List.of("getWorkflowsByType", List.of("first", 0L, 100L))));
    }

    @Test
    void selectedNamesAlsoFilterDirectIdLookup() {
        answer("getWorkflow", Optional.of(workflow("a", "third", 10)), "a", false);
        var page = controller.search(null, null, List.of("first", "second"), null, null,
                "a", null, null, null, 1, 15).getData();
        assertEquals(0, page.getTotal());
    }

    @Test
    void correlationSearchIncludesAllSelectionsAndLegacyNameStillWorks() {
        var first = workflow("a", "first", 10);
        var second = workflow("b", "second", 20);
        first.setCorrelationId("order");
        second.setCorrelationId("order");
        answer("getWorkflowsByCorrelationId", List.of(first), "first", "order", false);
        answer("getWorkflowsByCorrelationId", List.of(second), "second", "order", false);
        var page = controller.search(null, null, List.of("first", "second"), null, null,
                null, "order", null, null, 1, 15).getData();
        assertEquals(List.of("b", "a"), page.getRows().stream().map(WorkflowExecutionSummary::getWorkflowId).toList());
        var legacy = controller.search(null, "first", null, null, null,
                null, "order", null, null, 1, 15).getData();
        assertEquals(1, legacy.getTotal());
    }

    private WorkflowModel workflow(String id, String name, long start) {
        var workflow = new WorkflowModel();
        workflow.setWorkflowId(id);
        workflow.setStartTime(start);
        workflow.setWorkflowDefinition(WorkflowDef.builder().name(name).displayName("展示名称").version(1).build());
        return workflow;
    }
}
