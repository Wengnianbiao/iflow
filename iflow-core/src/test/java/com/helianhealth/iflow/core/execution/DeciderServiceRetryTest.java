package com.helianhealth.iflow.core.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.iflow.common.domain.metadata.task.TaskType;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowDef;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowTask;
import com.helianhealth.iflow.core.exception.TerminateWorkflowException;
import com.helianhealth.iflow.core.execution.tasks.SystemTaskRegistry;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import com.helianhealth.iflow.core.utils.IDGenerator;
import com.helianhealth.iflow.core.utils.ParametersUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DeciderServiceRetryTest {
    private final DeciderService decider = new DeciderService(new IDGenerator(),
            new ParametersUtils(new ObjectMapper()), new SystemTaskRegistry(Set.of()), Map.of());

    @Test
    void retryCreatesAnIsolatedAttemptAndPreservesHistoryAndExecutionScope() {
        WorkflowTask definition = definition();
        definition.setInputParameters(Map.of("payload", "${workflow.input.payload}"));
        WorkflowModel workflow = workflow(definition);
        workflow.setInput(Map.of("payload", "new input"));
        TaskModel failed = workflow.getTasks().getFirst();
        failed.setInputData(Map.of("payload", "old input", "resolvedRequest", "preserved"));
        failed.setOutputData(List.of("old failure output"));
        failed.setReasonForInCompletion("old failure");
        failed.setForkTaskId("fork");
        failed.setParentTaskId("parent");
        failed.setIteration(2);
        failed.setReferenceTaskName("http__2");
        failed.setSeq(7);
        failed.setScheduledTime(100);
        failed.setFirstScheduledTime(90);
        failed.setStartTime(110);
        failed.setEndTime(120);
        failed.setUpdateTime(120);

        DeciderService.DeciderOutcome outcome = decider.decide(workflow);
        TaskModel retry = outcome.tasksToBeScheduled.getFirst();

        assertFalse(outcome.isComplete);
        assertEquals(List.of(failed), outcome.tasksToBeUpdated);
        assertTrue(failed.isRetried());
        assertFalse(failed.isExecuted());
        assertEquals(TaskModel.Status.FAILED, failed.getStatus());
        assertEquals(List.of("old failure output"), failed.getOutputData());
        assertNotEquals(failed.getTaskId(), retry.getTaskId());
        assertEquals(failed.getTaskId(), retry.getRetriedTaskId());
        assertEquals(1, retry.getRetryCount());
        assertFalse(retry.isRetried());
        assertFalse(retry.isExecuted());
        assertEquals(TaskModel.Status.SCHEDULED, retry.getStatus());
        assertNull(retry.getPreviousStatus());
        assertEquals(0, retry.getSeq());
        assertEquals(0, retry.getScheduledTime());
        assertEquals(90, retry.getFirstScheduledTime());
        assertEquals(0, retry.getStartTime());
        assertEquals(0, retry.getEndTime());
        assertEquals(0, retry.getUpdateTime());
        assertNull(retry.getReasonForInCompletion());
        assertEquals(Map.of(), retry.getOutputData());
        assertEquals(Map.of("payload", "new input", "resolvedRequest", "preserved"), retry.getInputData());
        assertEquals("old input", failed.getInputData().get("payload"));
        assertEquals("http__2", retry.getReferenceTaskName());
        assertEquals(2, retry.getIteration());
        assertEquals("fork", retry.getForkTaskId());
        assertEquals("parent", retry.getParentTaskId());
        assertEquals(retry.getStartDelayInSeconds(), TaskModel.fromTask(retry.toTask()).getStartDelayInSeconds());
    }

    @ParameterizedTest
    @CsvSource({"FIXED,0,2", "FIXED,2,2", "LINEAR_BACKOFF,0,2", "LINEAR_BACKOFF,2,6",
            "EXPONENTIAL_BACKOFF,0,2", "EXPONENTIAL_BACKOFF,2,8"})
    void appliesConfiguredBackoff(WorkflowTask.RetryLogic logic, int retryCount, int delay) {
        WorkflowTask definition = definition();
        definition.setRetryCount(5);
        definition.setRetryDelaySeconds(2);
        definition.setRetryLogic(logic);
        WorkflowModel workflow = workflow(definition);
        workflow.getTasks().getFirst().setRetryCount(retryCount);

        assertEquals(delay, decider.decide(workflow).tasksToBeScheduled.getFirst().getStartDelayInSeconds());
    }

    @ParameterizedTest
    @EnumSource(WorkflowTask.RetryLogic.class)
    void clampsBackoffOverflowAndKeepsZeroDelayImmediate(WorkflowTask.RetryLogic logic) {
        WorkflowTask definition = definition();
        definition.setRetryCount(Integer.MAX_VALUE);
        definition.setRetryDelaySeconds(Integer.MAX_VALUE);
        definition.setRetryLogic(logic);
        WorkflowModel workflow = workflow(definition);
        workflow.getTasks().getFirst().setRetryCount(100);
        assertEquals(Integer.MAX_VALUE,
                decider.decide(workflow).tasksToBeScheduled.getFirst().getStartDelayInSeconds());

        definition.setRetryDelaySeconds(0);
        WorkflowModel immediate = workflow(definition);
        immediate.getTasks().getFirst().setRetryCount(100);
        assertEquals(0, decider.decide(immediate).tasksToBeScheduled.getFirst().getStartDelayInSeconds());
    }

    @Test
    void retriesOnlyTheLatestAttemptAndStopsAtConfiguredCount() {
        WorkflowModel workflow = workflow(definition());
        TaskModel first = workflow.getTasks().getFirst();
        TaskModel second = decider.decide(workflow).tasksToBeScheduled.getFirst();
        workflow.getTasks().add(second);
        assertTrue(decider.decide(workflow).tasksToBeScheduled.isEmpty());
        assertFalse(second.isRetried());

        second.setStatus(TaskModel.Status.FAILED);
        TaskModel third = decider.decide(workflow).tasksToBeScheduled.getFirst();
        workflow.getTasks().add(third);
        assertEquals(2, third.getRetryCount());
        assertEquals(second.getTaskId(), third.getRetriedTaskId());
        third.setStatus(TaskModel.Status.FAILED);
        TerminateWorkflowException error = assertThrows(TerminateWorkflowException.class,
                () -> decider.decide(workflow));
        assertSame(third, error.getTask());
        assertEquals(WorkflowModel.Status.FAILED, error.getWorkflowStatus());
        assertTrue(first.isRetried());
        assertTrue(second.isRetried());
        assertFalse(third.isRetried());
    }

    @ParameterizedTest
    @ValueSource(strings = {"DECISION", "SWITCH", "FORK", "JOIN", "EXCLUSIVE_JOIN", "DO_WHILE"})
    void neverRetriesBuiltInControlTasks(String type) {
        WorkflowTask definition = definition();
        definition.setType(type);
        WorkflowModel workflow = workflow(definition);
        assertThrows(TerminateWorkflowException.class, () -> decider.decide(workflow));
        assertFalse(workflow.getTasks().getFirst().isRetried());
    }

    @ParameterizedTest
    @EnumSource(value = TaskModel.Status.class, names = {"CANCELED", "FAILED_WITH_TERMINAL_ERROR"})
    void nonRetriableStatusesTerminateWithoutCreatingAnotherAttempt(TaskModel.Status status) {
        WorkflowModel workflow = workflow(definition());
        workflow.getTasks().getFirst().setStatus(status);
        TerminateWorkflowException error = assertThrows(TerminateWorkflowException.class,
                () -> decider.decide(workflow));
        assertEquals(status == TaskModel.Status.CANCELED ? WorkflowModel.Status.TERMINATED
                : WorkflowModel.Status.FAILED, error.getWorkflowStatus());
        assertFalse(workflow.getTasks().getFirst().isRetried());
    }

    @Test
    void retriableTaskTimeoutRetriesThenKeepsTimeoutStatusWhenExhausted() {
        WorkflowModel workflow = workflow(definition());
        TaskModel task = workflow.getTasks().getFirst();
        task.setStatus(TaskModel.Status.TIMED_OUT);
        assertEquals(1, decider.decide(workflow).tasksToBeScheduled.getFirst().getRetryCount());

        WorkflowModel exhausted = workflow(definition());
        exhausted.getTasks().getFirst().setStatus(TaskModel.Status.TIMED_OUT);
        exhausted.getTasks().getFirst().setRetryCount(2);
        assertEquals(WorkflowModel.Status.TIMED_OUT, assertThrows(TerminateWorkflowException.class,
                () -> decider.decide(exhausted)).getWorkflowStatus());
    }

    @ParameterizedTest
    @CsvSource({"true,false", "false,true", "true,true"})
    void optionalAndPermissivePoliciesApplyAfterRetriesAreExhausted(boolean optional, boolean permissive) {
        WorkflowTask definition = definition();
        definition.setOptional(optional);
        definition.setPermissive(permissive);
        WorkflowModel workflow = workflow(definition);
        TaskModel first = workflow.getTasks().getFirst();
        assertEquals(1, decider.decide(workflow).tasksToBeScheduled.getFirst().getRetryCount());
        assertEquals(TaskModel.Status.FAILED, first.getStatus());

        WorkflowModel exhausted = workflow(definition);
        TaskModel last = exhausted.getTasks().getFirst();
        last.setRetryCount(2);
        if (optional) {
            assertTrue(decider.decide(exhausted).isComplete);
        } else {
            assertEquals(WorkflowModel.Status.FAILED, assertThrows(TerminateWorkflowException.class,
                    () -> decider.decide(exhausted)).getWorkflowStatus());
        }
        assertEquals(optional ? TaskModel.Status.COMPLETED_WITH_ERRORS : TaskModel.Status.FAILED,
                last.getStatus());
        assertTrue(last.isExecuted());
        assertFalse(last.isRetried());
    }

    @Test
    void resolvesMissingNodeSnapshotInsideALoop() {
        WorkflowTask definition = definition();
        WorkflowModel workflow = workflow(definition);
        workflow.setWorkflowDefinition(WorkflowDef.builder().route("retry")
                .tasks(List.of(WorkflowTask.builder().type("DO_WHILE").taskReferenceName("loop")
                        .loopOver(List.of(definition)).build())).build());
        TaskModel task = workflow.getTasks().getFirst();
        task.setWorkflowTask(null);
        task.setIteration(2);
        task.setReferenceTaskName("http__2");
        assertEquals("http__2", decider.decide(workflow).tasksToBeScheduled.getFirst().getReferenceTaskName());
        assertSame(definition, task.getWorkflowTask());
    }

    @Test
    void terminalAndTimedOutWorkflowsDoNotRetryTasks() {
        WorkflowModel workflow = workflow(definition());
        workflow.setStatus(WorkflowModel.Status.TERMINATED);
        assertTrue(decider.decide(workflow).tasksToBeScheduled.isEmpty());
        assertFalse(workflow.getTasks().getFirst().isRetried());

        WorkflowModel timedOut = workflow(definition());
        timedOut.getWorkflowDefinition().setTimeoutSeconds(1);
        timedOut.setStartTime(System.currentTimeMillis() - 2_000);
        assertEquals(WorkflowModel.Status.TIMED_OUT, assertThrows(TerminateWorkflowException.class,
                () -> decider.decide(timedOut)).getWorkflowStatus());
        assertFalse(timedOut.getTasks().getFirst().isRetried());
    }

    private static WorkflowTask definition() {
        return WorkflowTask.builder().name("http").taskReferenceName("http").type(TaskType.TASK_TYPE_HTTP)
                .retryCount(2).retryDelaySeconds(0).retryLogic(WorkflowTask.RetryLogic.FIXED).build();
    }

    private static WorkflowModel workflow(WorkflowTask definition) {
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("workflow");
        workflow.setWorkflowDefinition(WorkflowDef.builder().route("retry").tasks(List.of(definition)).build());
        workflow.setStatus(WorkflowModel.Status.RUNNING);
        workflow.setStartTime(System.currentTimeMillis());
        TaskModel failed = new TaskModel();
        failed.setTaskId("first");
        failed.setWorkflowInstanceId("workflow");
        failed.setWorkflowTask(definition);
        failed.setReferenceTaskName(definition.getTaskReferenceName());
        failed.setTaskType(definition.getType());
        failed.setStatus(TaskModel.Status.FAILED);
        workflow.setTasks(new ArrayList<>(List.of(failed)));
        return workflow;
    }
}
