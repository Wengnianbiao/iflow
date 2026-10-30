package com.helianhealth.iflow.console.controller;

import com.helianhealth.iflow.common.domain.statistics.ExecutionStatistics;
import com.helianhealth.iflow.core.dao.IndexDAO;
import com.helianhealth.iflow.core.dao.MetadataDAO;
import com.helianhealth.iflow.core.service.MetadataService;
import com.helianhealth.iflow.core.service.MetadataServiceImpl;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowDef;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowTask;
import java.util.List;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExecutionStatisticsControllerTest {
    @Test
    void overviewCountsAllLatestNestedDefinitionsIndependentlyOfRuntimeFilter() {
        var dao = mock(IndexDAO.class);
        var metadata = mock(MetadataDAO.class);
        var branch = new WorkflowTask();
        branch.setType("SWITCH");
        branch.setDecisionCases(Map.of("one", List.of(new WorkflowTask())));
        branch.setDefaultCase(List.of(new WorkflowTask()));
        var loop = new WorkflowTask();
        loop.setType("DO_WHILE");
        loop.setLoopOver(List.of(new WorkflowTask()));
        var fork = new WorkflowTask();
        fork.setType("FORK_JOIN");
        fork.setForkTasks(List.of(List.of(branch), List.of(loop)));
        var latest = new WorkflowDef();
        latest.setRoute("a");
        latest.setVersion(3);
        latest.setTasks(List.of(fork));
        var other = new WorkflowDef();
        other.setRoute("b");
        other.setTasks(List.of(new WorkflowTask()));
        when(metadata.getAllLatest()).thenReturn(List.of(latest, other));
        var metrics = new ExecutionStatistics.Metrics(0, 0, 0, 0, 0, null, null, null, null);
        var runtime = new ExecutionStatistics.RuntimeOverview(metrics, metrics, "hour", List.of());
        when(dao.overview(any())).thenReturn(runtime);
        var controller = new ExecutionStatisticsController(dao, new MetadataServiceImpl(metadata));

        var result = controller.overview(10, 100, "Asia/Shanghai", " a ").getData();
        assertEquals(2, result.getWorkflowCount());
        assertEquals(7, result.getTaskCount());
        assertSame(runtime, result.getRuntime());
        var query = ArgumentCaptor.forClass(ExecutionStatistics.OverviewQuery.class);
        verify(dao).overview(query.capture());
        assertEquals("a", query.getValue().getWorkflowRoute());
        assertEquals("Asia/Shanghai", query.getValue().getTimeZone().getId());
        verify(metadata).getAllLatest();
        verifyNoMoreInteractions(metadata);
    }

    @Test
    void overviewBindsParametersAndRejectsBadRangeOrZoneBeforeReadingData() throws Exception {
        var dao = mock(IndexDAO.class);
        var metadata = mock(MetadataService.class);
        when(metadata.getAllLatest()).thenReturn(List.of());
        var controller = new ExecutionStatisticsController(dao, metadata);
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new com.helianhealth.iflow.console.config.ConsoleExceptionHandler()).build();
        var response = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/workflow/statistics/overview").param("startTime", "10").param("endTime", "100")
                .param("timeZone", "Asia/Shanghai")).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getContentAsString());
        assertEquals(0, json.at("/data/workflowCount").asLong());
        assertEquals(0, json.at("/data/taskCount").asLong());
        clearInvocations(dao, metadata);
        assertThrows(IllegalArgumentException.class, () -> controller.overview(100, 10, "UTC", ""));
        assertThrows(IllegalArgumentException.class, () -> controller.overview(10, 100, "bad-zone", ""));
        assertThrows(IllegalArgumentException.class, () -> controller.overview(10, 100, "", ""));
        verifyNoInteractions(dao, metadata);
    }

    @Test
    void bindsHttpParametersWithoutCompilerParameterMetadata() throws Exception {
        var dao = mock(IndexDAO.class);
        var metrics = new ExecutionStatistics.Metrics(0, 0, 0, 0, 0, null, null, null, null);
        when(dao.workflows(any())).thenReturn(new ExecutionStatistics.Page(metrics, java.util.List.of(), 0, 1, 20));
        when(dao.tasks(any())).thenReturn(new ExecutionStatistics.Page(metrics, java.util.List.of(), 0, 1, 20));
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(new ExecutionStatisticsController(dao, mock(MetadataService.class)))
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
        new ExecutionStatisticsController(dao, mock(MetadataService.class)).statistics(request, 100, 200, " demo ", true,
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
        new ExecutionStatisticsController(dao, mock(MetadataService.class)).statistics(request, 100, 200, "", true,
                "totalCount", true, 1, 20);
        verify(dao).workflows(any());
        verifyNoMoreInteractions(dao);
    }

    @Test
    void rejectsInvalidRangesAndMissingNodeIdentityBeforeQuerying() {
        var dao = mock(IndexDAO.class);
        var controller = new ExecutionStatisticsController(dao, mock(MetadataService.class));
        assertThrows(IllegalArgumentException.class, () -> controller.statistics(mock(HttpServletRequest.class),
                200, 100, "", false, "totalCount", true, 1, 20));
        assertThrows(IllegalArgumentException.class, () -> controller.taskInstances(100, 200, "demo", "", "HTTP", false, 1, 20));
        verifyNoInteractions(dao);
    }
}
