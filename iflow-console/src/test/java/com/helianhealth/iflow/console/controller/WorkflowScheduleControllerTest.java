package com.helianhealth.iflow.console.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowSchedule;
import com.helianhealth.iflow.common.domain.run.StartWorkflowRequest;
import com.helianhealth.iflow.console.config.ConsoleExceptionHandler;
import com.helianhealth.iflow.gateway.schedule.WorkflowScheduleService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

class WorkflowScheduleControllerTest {

    private static final String PATH = "/api/workflow/schedules";
    private static final String CONFIGURATION = """
            {
              "name": "daily",
              "displayName": "每天同步检验结果",
              "cronExpression": "0 0 8 * * *",
              "zoneId": "Asia/Shanghai",
              "enabled": %s,
              "nextRunAt": "2025-01-01T00:00:00Z",
              "startWorkflowRequest": {"route": "lis/sync", "input": {"source": "LIS"}}
            }
            """;

    private final WorkflowScheduleService service = mock(WorkflowScheduleService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new WorkflowScheduleController(service))
            .setControllerAdvice(new ConsoleExceptionHandler()).build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void previewReturnsEpochMillisecondsAndDoesNotResolveASchedule() throws Exception {
        String cronExpression = "0 0 */2 ? * *";
        String zoneId = "Asia/Shanghai";
        long nextRunAt = Instant.parse("2026-11-11T00:00:00Z").toEpochMilli();
        when(service.preview(cronExpression, zoneId)).thenReturn(List.of(nextRunAt));

        var response = mvc.perform(get(PATH + "/preview")
                .param("cronExpression", cronExpression)
                .param("zoneId", zoneId)).andReturn().getResponse();

        assertEquals(200, response.getStatus());
        assertEquals(nextRunAt, objectMapper.readTree(response.getContentAsString()).at("/data/0").asLong());
        verify(service).preview(cronExpression, zoneId);
        verifyNoMoreInteractions(service);
    }

    @Test
    void previewReturnsBadRequestForAnInvalidExpression() throws Exception {
        String message = "Cron 表达式格式错误";
        when(service.preview("bad", "UTC")).thenThrow(new IllegalArgumentException(message));

        var response = mvc.perform(get(PATH + "/preview")
                .param("cronExpression", "bad")
                .param("zoneId", "UTC")).andReturn().getResponse();

        assertEquals(400, response.getStatus());
        assertEquals(message, objectMapper.readTree(response.getContentAsString()).get("message").asText());
    }

    @Test
    void createsListsReadsUpdatesAndDeletesSchedulesByName() throws Exception {
        Instant nextRunAt = Instant.parse("2026-11-11T00:00:00Z");
        StartWorkflowRequest request = new StartWorkflowRequest("lis/sync", null, Map.of("source", "LIS"));
        WorkflowSchedule active = new WorkflowSchedule(
                "daily", "每天同步检验结果", "0 0 8 * * *", "Asia/Shanghai", true, request, nextRunAt);
        WorkflowSchedule paused = new WorkflowSchedule(
                "daily", "每天同步检验结果", "0 0 8 * * *", "Asia/Shanghai", false, request, null);
        when(service.create(any())).thenReturn(active);
        when(service.findAll()).thenReturn(List.of(active));
        when(service.findByName("daily")).thenReturn(Optional.of(active));
        when(service.update(eq("daily"), any())).thenReturn(Optional.of(paused));

        var created = mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON)
                .content(CONFIGURATION.formatted(true))).andReturn().getResponse();
        assertEquals(200, created.getStatus());
        var data = objectMapper.readTree(created.getContentAsString()).get("data");
        assertFalse(data.has("id"));
        assertFalse(data.has("revision"));
        assertEquals("daily", data.get("name").asText());
        assertEquals("每天同步检验结果", data.get("displayName").asText());
        assertEquals(nextRunAt.toString(), data.get("nextRunAt").asText());
        var submitted = ArgumentCaptor.forClass(WorkflowSchedule.class);
        verify(service).create(submitted.capture());
        assertEquals("daily", submitted.getValue().getName());
        assertEquals("每天同步检验结果", submitted.getValue().getDisplayName());
        assertNull(submitted.getValue().getNextRunAt());
        assertEquals(request, submitted.getValue().getStartWorkflowRequest());

        var listed = mvc.perform(get(PATH)).andReturn().getResponse();
        assertEquals(200, listed.getStatus());
        assertEquals(data, objectMapper.readTree(listed.getContentAsString()).at("/data/0"));
        var found = mvc.perform(get(PATH + "/daily")).andReturn().getResponse();
        assertEquals(200, found.getStatus());
        assertEquals(data, objectMapper.readTree(found.getContentAsString()).get("data"));

        var updated = mvc.perform(put(PATH + "/daily").contentType(MediaType.APPLICATION_JSON)
                .content(CONFIGURATION.formatted(false))).andReturn().getResponse();
        assertEquals(200, updated.getStatus());
        assertTrue(objectMapper.readTree(updated.getContentAsString()).at("/data/nextRunAt").isNull());
        verify(service).update(eq("daily"), submitted.capture());
        assertEquals(Boolean.FALSE, submitted.getValue().getEnabled());

        assertEquals(200, mvc.perform(delete(PATH + "/daily")).andReturn().getResponse().getStatus());
        verify(service).delete("daily");
    }

    @Test
    void missingSchedulesReturn404AndInvalidNamesNeverReachService() throws Exception {
        assertEquals(404, mvc.perform(get(PATH + "/missing_name")).andReturn().getResponse().getStatus());
        assertEquals(404, mvc.perform(put(PATH + "/missing_name").contentType(MediaType.APPLICATION_JSON)
                .content(CONFIGURATION.formatted(true))).andReturn().getResponse().getStatus());
        verify(service).findByName("missing_name");
        verify(service).update(eq("missing_name"), any());

        assertEquals(400, mvc.perform(get(PATH + "/bad-name")).andReturn().getResponse().getStatus());
        assertEquals(400, mvc.perform(delete(PATH + "/new")).andReturn().getResponse().getStatus());
        verifyNoMoreInteractions(service);
    }
}
