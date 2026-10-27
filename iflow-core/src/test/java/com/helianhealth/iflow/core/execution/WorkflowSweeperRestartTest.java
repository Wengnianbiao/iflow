package com.helianhealth.iflow.core.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowDef;
import com.helianhealth.iflow.core.dal.ExecutionDAOFacade;
import com.helianhealth.iflow.core.execution.tasks.SystemTaskRegistry;
import com.helianhealth.iflow.core.listener.WorkflowStatusListener;
import com.helianhealth.iflow.core.lock.InMemoryExecutionLockService;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowExecutionMapper;
import com.helianhealth.iflow.core.model.WorkflowModel;
import com.helianhealth.iflow.core.utils.IDGenerator;
import com.helianhealth.iflow.core.utils.ParametersUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WorkflowSweeperRestartTest {
    private static final long PROCESS_START = System.currentTimeMillis() - 120_000;
    private final SystemTaskRegistry registry = new SystemTaskRegistry(Set.of());
    private final ExecutionDAOFacade dao = mock(ExecutionDAOFacade.class);
    private final WorkflowCompletionRegistry completions = new WorkflowCompletionRegistry();
    private final WorkflowStatusListener listener = mock(WorkflowStatusListener.class);
    private final DeciderService decider = new DeciderService(new IDGenerator(),
            new ParametersUtils(new ObjectMapper()), registry, Map.of());
    private final WorkflowExecutor executor = new WorkflowExecutorOps(null, registry, dao, null,
            new IDGenerator(), null, new InMemoryExecutionLockService(), null, completions,
            List.of(listener, completions));
    private final WorkflowSweeper sweeper = new WorkflowSweeper(registry, dao,
            new InMemoryExecutionLockService(), decider, PROCESS_START);

    @Test
    void restartInterruptsEvenAlertOnlyUnexpiredInstancesAndOnlyCancelsOpenTasks() {
        WorkflowModel workflow = workflow(PROCESS_START - 1, WorkflowModel.Status.RUNNING);
        workflow.getWorkflowDefinition().setTimeoutPolicy(WorkflowDef.TimeoutPolicy.ALERT_ONLY);
        workflow.getWorkflowDefinition().setTimeoutSeconds(Long.MAX_VALUE);
        TaskModel completed = task("done", TaskModel.Status.COMPLETED);
        TaskModel running = task("running", TaskModel.Status.IN_PROGRESS);
        TaskModel queued = task("queued", TaskModel.Status.SCHEDULED);
        workflow.setTasks(List.of(completed, running, queued));
        when(dao.getWorkflowModel("workflow")).thenReturn(workflow);

        try (var registration = completions.register("workflow")) {
            assertTrue(sweeper.sweepStored("workflow", executor));
            assertTimeoutPreemptively(Duration.ofSeconds(1), registration::await);
        }
        assertEquals(WorkflowModel.Status.INTERRUPTED, workflow.getStatus());
        assertEquals(TaskModel.Status.COMPLETED, completed.getStatus());
        assertEquals(TaskModel.Status.CANCELED, running.getStatus());
        assertEquals(TaskModel.Status.CANCELED, queued.getStatus());
        assertTrue(workflow.getReasonForInCompletion().contains("进程重启"));
        assertTrue(workflow.getEndTime() > 0);
        assertTrue(running.getEndTime() > 0);
        var order = inOrder(dao, listener);
        order.verify(dao).getWorkflowModel("workflow");
        order.verify(dao).updateTask(running);
        order.verify(dao).updateTask(queued);
        order.verify(dao).updateWorkflow(workflow);
        order.verify(listener).onWorkflowInterrupted(workflow);
        assertFalse(sweeper.sweepStored("workflow", executor));
        verify(listener, times(1)).onWorkflowInterrupted(workflow);
    }

    @Test
    void instancesCreatedAtOrAfterProcessStartAreNotRestarted() {
        WorkflowModel workflow = workflow(PROCESS_START, WorkflowModel.Status.RUNNING);
        workflow.getWorkflowDefinition().setTimeoutSeconds(300);
        when(dao.getWorkflowModel("workflow")).thenReturn(workflow);
        assertFalse(sweeper.sweepStored("workflow", executor));
        verify(dao, never()).updateWorkflow(any());
        verifyNoInteractions(listener);
    }

    @Test
    void currentProcessExpiredInstanceOnlyPersistsWhenPolicyTerminates() {
        WorkflowModel workflow = workflow(PROCESS_START, WorkflowModel.Status.RUNNING);
        workflow.getWorkflowDefinition().setTimeoutPolicy(WorkflowDef.TimeoutPolicy.ALERT_ONLY);
        when(dao.getWorkflowModel("workflow")).thenReturn(workflow);
        assertFalse(sweeper.sweepStored("workflow", executor));
        assertEquals(WorkflowModel.Status.RUNNING, workflow.getStatus());
        verifyNoInteractions(listener);
        assertFalse(sweeper.sweepStored("workflow", executor));
        verify(dao, never()).updateWorkflow(any());

        workflow.getWorkflowDefinition().setTimeoutPolicy(WorkflowDef.TimeoutPolicy.TIME_OUT_WF);
        assertTrue(sweeper.sweepStored("workflow", executor));
        assertEquals(WorkflowModel.Status.TIMED_OUT, workflow.getStatus());
        verify(dao).updateWorkflow(workflow);
        verify(listener).onWorkflowTimedOut(workflow);
    }

    @Test
    void terminalPersistenceFailureDoesNotNotifyAndNextSweepRetries() {
        AtomicReference<WorkflowModel> stored = new AtomicReference<>(
                workflow(PROCESS_START - 1, WorkflowModel.Status.RUNNING));
        when(dao.getUnfinishedWorkflowIds()).thenReturn(List.of("workflow"));
        when(dao.getWorkflowModel("workflow")).thenAnswer(invocation ->
                WorkflowExecutionMapper.toModel(WorkflowExecutionMapper.toWorkflowHeader(stored.get())));
        when(dao.updateWorkflow(any())).thenThrow(new IllegalStateException("temporary save failure"))
                .thenAnswer(invocation -> {
                    stored.set(invocation.getArgument(0));
                    return "workflow";
                });
        assertDoesNotThrow(() -> sweeper.sweepRunningWorkflows(executor));
        verifyNoInteractions(listener);
        assertEquals(WorkflowModel.Status.RUNNING, stored.get().getStatus());
        sweeper.sweepRunningWorkflows(executor);
        assertEquals(WorkflowModel.Status.INTERRUPTED, stored.get().getStatus());
        verify(listener).onWorkflowInterrupted(any());
        sweeper.sweepRunningWorkflows(executor);
        verify(dao, times(2)).updateWorkflow(any());
    }

    @ParameterizedTest
    @EnumSource(value = WorkflowModel.Status.class, names = {"COMPLETED", "FAILED", "TIMED_OUT", "TERMINATED", "INTERRUPTED"})
    void neverChangesExistingTerminalInstances(WorkflowModel.Status status) {
        when(dao.getWorkflowModel("workflow")).thenReturn(workflow(PROCESS_START - 1, status));
        assertFalse(sweeper.sweepStored("workflow", executor));
        verify(dao, never()).updateWorkflow(any());
        verifyNoInteractions(listener);
    }

    private static WorkflowModel workflow(long startTime, WorkflowModel.Status status) {
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("workflow");
        workflow.setWorkflowDefinition(WorkflowDef.builder().route("test").timeoutSeconds(60).build());
        workflow.setStartTime(startTime);
        workflow.setStatus(status);
        return workflow;
    }

    private static TaskModel task(String id, TaskModel.Status status) {
        TaskModel task = new TaskModel();
        task.setTaskId(id);
        task.setReferenceTaskName(id);
        task.setTaskType("HTTP");
        task.setStatus(status);
        return task;
    }
}
