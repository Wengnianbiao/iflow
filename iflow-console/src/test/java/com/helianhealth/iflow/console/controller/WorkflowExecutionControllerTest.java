package com.helianhealth.iflow.console.controller;

import com.helianhealth.iflow.common.base.PageList;
import com.helianhealth.iflow.common.domain.run.ExecutionSearch;
import com.helianhealth.iflow.common.domain.run.StartWorkflowRequest;
import com.helianhealth.iflow.core.dao.ExecutionDAO;
import com.helianhealth.iflow.core.dao.IndexDAO;
import com.helianhealth.iflow.core.service.WorkflowExecutionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkflowExecutionControllerTest {
    private final ExecutionDAO dao = mock(ExecutionDAO.class);
    private final IndexDAO indexDAO = mock(IndexDAO.class);
    private final WorkflowExecutionController controller = new WorkflowExecutionController(dao, null, null, indexDAO);

    @Test
    void consoleStartReturnsSubmittedWorkflowIdUsingAsynchronousEntry() {
        WorkflowExecutionService service = mock(WorkflowExecutionService.class);
        StartWorkflowRequest request = StartWorkflowRequest.builder().route("pacs/apply").build();
        when(service.startWorkflow(request)).thenReturn("submitted");
        var launchController = new WorkflowExecutionController(dao, service, null, indexDAO);

        assertEquals("submitted", launchController.start(request).getData());
        verify(service).startWorkflow(request);
        verifyNoMoreInteractions(service);
        verifyNoInteractions(dao, indexDAO);
    }

    @Test
    void searchAlwaysUsesIndexAndPassesCombinedFiltersAndPagination() {
        var indexed = new ExecutionSearch.WorkflowSummary("indexed", "first", "展示名称", 2,
                "COMPLETED", "order", 10, 30, 31, null, null);
        when(indexDAO.searchWorkflows(any())).thenReturn(new PageList<>(7, 2, 1, List.of(indexed)));
        var page = controller.search(null, List.of("first", "second", "first"), 2, "completed",
                null, "order", 0L, 100L, 2, 1).getData();
        assertEquals(7, page.getTotal());
        assertEquals("indexed", page.getRows().getFirst().getWorkflowId());
        assertEquals(20L, page.getRows().getFirst().getDurationMs());
        var captured = ArgumentCaptor.forClass(ExecutionSearch.WorkflowQuery.class);
        verify(indexDAO).searchWorkflows(captured.capture());
        assertEquals(List.of("first", "second"), captured.getValue().workflowRoutes());
        assertEquals(List.of("COMPLETED"), captured.getValue().statuses());
        assertEquals("order", captured.getValue().correlationId());
        assertEquals(2, captured.getValue().pageNum());
        assertEquals(1, captured.getValue().pageSize());
        verifyNoInteractions(dao);
    }

    @Test
    void emptyIndexReturnsEmptyResultsWithoutFallingBackToStoredWorkflows() {
        emptyWorkflowIndex();
        var page = controller.search("first", null, null, null,
                "unindexed", null, null, null, 1, 15).getData();
        assertEquals(0, page.getTotal());
        assertTrue(page.getRows().isEmpty());
        var captured = ArgumentCaptor.forClass(ExecutionSearch.WorkflowQuery.class);
        verify(indexDAO).searchWorkflows(captured.capture());
        assertEquals(List.of("first"), captured.getValue().workflowRoutes());
        assertEquals("unindexed", captured.getValue().workflowId());
        assertNull(captured.getValue().startTime());
        assertNull(captured.getValue().endTime());
        verifyNoInteractions(dao);
    }

    @Test
    void recentInstancesUseIndexImmediately() {
        emptyWorkflowIndex();
        assertTrue(controller.listInstances("first", 2, 50).getData().isEmpty());
        var captured = ArgumentCaptor.forClass(ExecutionSearch.WorkflowQuery.class);
        verify(indexDAO).searchWorkflows(captured.capture());
        assertEquals(List.of("first"), captured.getValue().workflowRoutes());
        assertEquals(2, captured.getValue().version());
        assertEquals(50, captured.getValue().pageSize());
        verifyNoInteractions(dao);
    }

    @Test
    void taskSearchQueriesIndexWithoutAnInitializationGate() {
        when(indexDAO.searchTasks(any())).thenReturn(new PageList<>(0, 1, 20, List.of()));
        assertEquals(0, controller.searchTasks("first", "wf", null, "call", "HTTP", "failed",
                true, 0L, 100L, 1, 20).getData().getTotal());
        var captured = ArgumentCaptor.forClass(ExecutionSearch.TaskQuery.class);
        verify(indexDAO).searchTasks(captured.capture());
        assertTrue(captured.getValue().asyncOnly());
        assertEquals("wf", captured.getValue().workflowId());
        assertEquals(List.of("FAILED"), captured.getValue().statuses());
        verifyNoInteractions(dao);
    }

    @Test
    void unboundedSearchDefaultsToSevenDaysAndClampsPagination() {
        emptyWorkflowIndex();
        long before = System.currentTimeMillis();
        controller.search(null, null, null, null, null, null, null, null, 0, 1000);
        var captured = ArgumentCaptor.forClass(ExecutionSearch.WorkflowQuery.class);
        verify(indexDAO).searchWorkflows(captured.capture());
        var query = captured.getValue();
        assertEquals(1, query.pageNum());
        assertEquals(100, query.pageSize());
        assertTrue(query.workflowRoutes().isEmpty());
        assertTrue(query.endTime() >= before && query.endTime() <= System.currentTimeMillis());
        assertEquals(Duration.ofDays(7).toMillis(), query.endTime() - query.startTime());
        verifyNoInteractions(dao);
    }

    @Test
    void correlationSearchDoesNotAddImplicitTimeBounds() {
        emptyWorkflowIndex();
        controller.search("first", null, null, null, null, "order", null, null, 1, 20);
        var captured = ArgumentCaptor.forClass(ExecutionSearch.WorkflowQuery.class);
        verify(indexDAO).searchWorkflows(captured.capture());
        assertEquals("order", captured.getValue().correlationId());
        assertNull(captured.getValue().startTime());
        assertNull(captured.getValue().endTime());
        verifyNoInteractions(dao);
    }

    private void emptyWorkflowIndex() {
        when(indexDAO.searchWorkflows(any())).thenReturn(new PageList<>(0, 1, 20, List.of()));
    }
}
