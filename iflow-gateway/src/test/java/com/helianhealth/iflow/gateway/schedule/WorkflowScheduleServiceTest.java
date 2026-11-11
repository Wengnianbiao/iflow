package com.helianhealth.iflow.gateway.schedule;

import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowSchedule;
import com.helianhealth.iflow.common.domain.run.StartWorkflowRequest;
import com.helianhealth.iflow.core.service.MetadataService;
import com.helianhealth.iflow.core.service.WorkflowExecutionService;
import com.helianhealth.iflow.persistence.mysql.dao.MySqlWorkflowScheduleDAO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.support.CronExpression;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowScheduleServiceTest {

    private static final Instant SCHEDULED_AT = Instant.parse("2026-11-10T00:00:00Z");
    private static final Instant NEXT_DAY = Instant.parse("2026-11-11T00:00:00Z");
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Mock
    private MySqlWorkflowScheduleDAO scheduleDAO;
    @Mock
    private MetadataService metadataService;
    @Mock
    private WorkflowExecutionService workflowExecutionService;

    @Test
    void previewsFiveFutureRunsInTheRequestedTimeZoneWithoutReadingWorkflowData() {
        Instant beforePreview = Instant.now();

        List<Long> times = service().preview("0 0 8 * * *", SHANGHAI.getId());
        Instant afterPreview = Instant.now();

        assertEquals(5, times.size());
        Instant first = Instant.ofEpochMilli(times.getFirst());
        assertTrue(first.isAfter(beforePreview));
        assertFalse(first.isAfter(afterPreview.plus(1, ChronoUnit.DAYS)));
        assertEquals(LocalTime.of(8, 0), first.atZone(SHANGHAI).toLocalTime());
        for (int i = 1; i < times.size(); i++) {
            assertEquals(first.plus(i, ChronoUnit.DAYS), Instant.ofEpochMilli(times.get(i)));
        }
        verifyNoInteractions(scheduleDAO, metadataService, workflowExecutionService);
    }

    @Test
    void previewRejectsInvalidCronTimeZoneAndImpossibleCalendarDate() {
        assertThrows(IllegalArgumentException.class, () -> service().preview("bad", SHANGHAI.getId()));
        assertThrows(IllegalArgumentException.class, () -> service().preview("0 0 8 * * *", "bad"));
        assertThrows(IllegalArgumentException.class, () -> service().preview("0 0 8 31 2 *", SHANGHAI.getId()));
        verifyNoInteractions(scheduleDAO, metadataService, workflowExecutionService);
    }

    @Test
    void createUsesConfiguredTimeZoneAndKeepsLatestVersionRequest() {
        WorkflowSchedule request = schedule(null);
        request.getStartWorkflowRequest().setInput(null);
        when(scheduleDAO.create(any())).thenAnswer(call -> call.getArgument(0));

        Instant beforeSave = Instant.now();
        WorkflowSchedule saved = service().create(request);
        Instant afterSave = Instant.now();

        var cron = CronExpression.parse("0 0 8 * * *");
        Instant firstPossible = cron.next(beforeSave.atZone(SHANGHAI)).toInstant();
        Instant lastPossible = cron.next(afterSave.atZone(SHANGHAI)).toInstant();
        assertTrue(
                !saved.getNextRunAt().isBefore(firstPossible) && !saved.getNextRunAt().isAfter(lastPossible));
        assertNull(saved.getStartWorkflowRequest().getVersion());
        assertEquals(Map.of(), saved.getStartWorkflowRequest().getInput());
        assertEquals("sync_lab_results", saved.getName());
        assertEquals("每天同步检验结果", saved.getDisplayName());
        verify(metadataService).getWorkflowDef("lis/sync-results", null);
    }

    @Test
    void rejectsInvalidIdentifierAndBlankDisplayNameBeforeReadingWorkflowData() {
        WorkflowSchedule request = schedule(null);
        request.setName("每天同步");
        assertThrows(IllegalArgumentException.class, () -> service().create(request));

        request.setName("sync-lab");
        assertThrows(IllegalArgumentException.class, () -> service().create(request));

        request.setName("preview");
        assertThrows(IllegalArgumentException.class, () -> service().create(request));

        request.setName("new");
        assertThrows(IllegalArgumentException.class, () -> service().create(request));

        request.setName("sync_lab_results");
        request.setDisplayName("  ");
        assertThrows(IllegalArgumentException.class, () -> service().create(request));
        verifyNoInteractions(scheduleDAO, metadataService, workflowExecutionService);
    }

    @Test
    void rejectsInvalidCronTimeZoneAndImpossibleCalendarDate() {
        WorkflowSchedule request = schedule(null);
        request.setCronExpression("bad");
        assertThrows(IllegalArgumentException.class, () -> service().create(request));

        request.setCronExpression("0 0 8 31 2 *");
        assertThrows(IllegalArgumentException.class, () -> service().create(request));

        request.setCronExpression("0 0 8 * * *");
        request.setZoneId("bad");
        assertThrows(IllegalArgumentException.class, () -> service().create(request));
        verifyNoInteractions(scheduleDAO);
    }

    @Test
    void updateReloadsAfterAConcurrentClaimBeforeDisabling() {
        WorkflowSchedule old = schedule(SCHEDULED_AT);
        WorkflowSchedule advanced = schedule(NEXT_DAY);
        WorkflowSchedule request = schedule(null);
        request.setEnabled(false);
        when(scheduleDAO.findByName("sync_lab_results")).thenReturn(Optional.of(old), Optional.of(advanced));
        when(scheduleDAO.update(eq(old), any())).thenReturn(Optional.empty());
        when(scheduleDAO.update(eq(advanced), any())).thenAnswer(call -> {
            WorkflowSchedule replacement = call.getArgument(1);
            assertFalse(replacement.getEnabled());
            assertNull(replacement.getNextRunAt());
            return Optional.of(replacement);
        });

        assertFalse(service().update("sync_lab_results", request).orElseThrow().getEnabled());
    }

    @Test
    void skipsDueTimesAtOrBeforeReadinessAndKeepsFirstTimeAfterReadiness() {
        WorkflowSchedule schedule = schedule(SCHEDULED_AT);
        when(scheduleDAO.advance(schedule, NEXT_DAY)).thenReturn(true);

        service().trigger(schedule, SCHEDULED_AT, NEXT_DAY.plusSeconds(5));

        verify(scheduleDAO).advance(schedule, NEXT_DAY);
        verifyNoInteractions(workflowExecutionService);
    }

    @Test
    void advancesPastAccumulatedTimesBeforeStartingWorkflowOnce() {
        WorkflowSchedule schedule = schedule(SCHEDULED_AT);
        Instant now = NEXT_DAY.plusSeconds(5);
        Instant nextRunAt = Instant.parse("2026-11-12T00:00:00Z");
        when(scheduleDAO.advance(schedule, nextRunAt)).thenReturn(true, false);
        when(workflowExecutionService.startWorkflow(schedule.getStartWorkflowRequest())).thenReturn("workflow-id");

        WorkflowScheduleService service = service();
        service.trigger(schedule, SCHEDULED_AT.minusSeconds(10), now);
        service.trigger(schedule, SCHEDULED_AT.minusSeconds(10), now);

        var ordered = inOrder(scheduleDAO, workflowExecutionService);
        ordered.verify(scheduleDAO).advance(schedule, nextRunAt);
        ordered.verify(workflowExecutionService).startWorkflow(schedule.getStartWorkflowRequest());
        ordered.verify(scheduleDAO).advance(schedule, nextRunAt);
        ordered.verifyNoMoreInteractions();
    }

    @Test
    void failedStartDoesNotPreventTheNextScheduledTrigger() {
        WorkflowSchedule first = schedule(SCHEDULED_AT);
        WorkflowSchedule second = schedule(NEXT_DAY);
        Instant nextRunAt = Instant.parse("2026-11-12T00:00:00Z");
        when(scheduleDAO.advance(first, NEXT_DAY)).thenReturn(true);
        when(scheduleDAO.advance(second, nextRunAt)).thenReturn(true);
        when(workflowExecutionService.startWorkflow(first.getStartWorkflowRequest()))
                .thenThrow(new IllegalStateException("start failed"))
                .thenReturn("workflow-id");

        WorkflowScheduleService service = service();
        Instant readyAt = SCHEDULED_AT.minusSeconds(10);
        assertDoesNotThrow(() -> service.trigger(first, readyAt, SCHEDULED_AT));
        assertDoesNotThrow(() -> service.trigger(second, readyAt, NEXT_DAY));

        verify(scheduleDAO).advance(second, nextRunAt);
        verify(workflowExecutionService, times(2)).startWorkflow(first.getStartWorkflowRequest());
    }

    private WorkflowScheduleService service() {
        return new WorkflowScheduleService(scheduleDAO, metadataService, workflowExecutionService);
    }

    private WorkflowSchedule schedule(Instant scheduledAt) {
        WorkflowSchedule schedule = new WorkflowSchedule(
                "  sync_lab_results  ", "  每天同步检验结果  ", "0 0 8 * * *", SHANGHAI.getId(), true,
                new StartWorkflowRequest("lis/sync-results", null, Map.of("source", "LIS")), scheduledAt);
        return schedule;
    }
}
