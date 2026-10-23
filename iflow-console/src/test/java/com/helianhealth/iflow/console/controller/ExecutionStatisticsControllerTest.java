package com.helianhealth.iflow.console.controller;

import com.helianhealth.iflow.common.domain.statistics.ExecutionStatistics;
import com.helianhealth.iflow.core.dao.IndexDAO;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExecutionStatisticsControllerTest {
    @Test
    void bindsHttpParametersWithoutCompilerParameterMetadata() throws Exception {
        var dao = mock(IndexDAO.class);
        var metrics = new ExecutionStatistics.Metrics(0, 0, 0, 0, 0, null, null, null, null);
        when(dao.workflows(any())).thenReturn(new ExecutionStatistics.Page(metrics, java.util.List.of(), 0, 1, 20));
        when(dao.tasks(any())).thenReturn(new ExecutionStatistics.Page(metrics, java.util.List.of(), 0, 1, 20));
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(new ExecutionStatisticsController(dao))
                .setControllerAdvice(new com.helianhealth.iflow.console.config.ConsoleExceptionHandler()).build();
        for (String mode : java.util.List.of("workflows", "tasks")) {
            var response = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .get("/api/workflow/statistics/" + mode).servletPath("/api/workflow/statistics/" + mode)
                    .param("startTime", "0").param("endTime", "100")).andReturn().getResponse();
            assertEquals(200, response.getStatus(), response.getContentAsString());
        }
        verify(dao).workflows(any());
        verify(dao).tasks(any());
    }

    @Test
    void taskEndpointPassesAsyncFilterAndClampsPaging() {
        var dao = mock(IndexDAO.class);
        var request = mock(HttpServletRequest.class);
        when(request.getServletPath()).thenReturn("/api/workflow/statistics/tasks");
        new ExecutionStatisticsController(dao).statistics(request, 100, 200, " demo ", true,
                "avgDurationMs", false, 0, 1000);
        var captured = ArgumentCaptor.forClass(ExecutionStatistics.Query.class);
        verify(dao).tasks(captured.capture());
        assertEquals("demo", captured.getValue().workflowRoute());
        assertTrue(captured.getValue().asyncOnly());
        assertEquals(1, captured.getValue().pageNum());
        assertEquals(100, captured.getValue().pageSize());
        assertFalse(captured.getValue().descending());
        verifyNoMoreInteractions(dao);
    }

    @Test
    void workflowEndpointUsesWorkflowAggregation() {
        var dao = mock(IndexDAO.class);
        var request = mock(HttpServletRequest.class);
        when(request.getServletPath()).thenReturn("/api/workflow/statistics/workflows");
        new ExecutionStatisticsController(dao).statistics(request, 100, 200, "", true,
                "totalCount", true, 1, 20);
        verify(dao).workflows(any());
        verifyNoMoreInteractions(dao);
    }

    @Test
    void rejectsInvalidRangesAndMissingNodeIdentityBeforeQuerying() {
        var dao = mock(IndexDAO.class);
        var controller = new ExecutionStatisticsController(dao);
        assertThrows(IllegalArgumentException.class, () -> controller.statistics(mock(HttpServletRequest.class),
                200, 100, "", false, "totalCount", true, 1, 20));
        assertThrows(IllegalArgumentException.class, () -> controller.taskInstances(100, 200, "demo", "", "HTTP", false, 1, 20));
        verifyNoInteractions(dao);
    }
}
