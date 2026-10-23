package com.helianhealth.iflow.core.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowDef;
import com.helianhealth.iflow.core.dal.ExecutionDAOFacade;
import com.helianhealth.iflow.core.listener.WorkflowStatusListener;
import com.helianhealth.iflow.core.listener.WorkflowStatusListener.WorkflowEventType;
import com.helianhealth.iflow.core.lock.InMemoryExecutionLockService;
import com.helianhealth.iflow.core.model.WorkflowModel;
import com.helianhealth.iflow.core.queue.InMemoryExecutionQueue;
import com.helianhealth.iflow.core.queue.InMemoryQueueDAO;
import com.helianhealth.iflow.core.service.MetadataService;
import com.helianhealth.iflow.core.utils.IDGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.Mockito.*;

class WorkflowStatusListenerTest {

    private final ExecutionDAOFacade dao = mock(ExecutionDAOFacade.class);
    private final DeciderService decider = mock(DeciderService.class);
    private final MetadataService metadata = mock(MetadataService.class);
    private final WorkflowCompletionRegistry completions = new WorkflowCompletionRegistry();

    @ParameterizedTest
    @EnumSource(value = WorkflowModel.Status.class, names = "RUNNING", mode = EnumSource.Mode.EXCLUDE)
    void notifiesTerminalStatusOnSavingThreadAfterPersistence(WorkflowModel.Status status) {
        Thread savingThread = Thread.currentThread();
        WorkflowStatusListener listener = spy(new TestWorkflowStatusListener((workflowId, notifiedStatus) -> {
            assertSame(savingThread, Thread.currentThread());
            assertEquals("workflow", workflowId);
            assertEquals(status, notifiedStatus);
        }));
        WorkflowModel workflow = workflow(status);

        executor(List.of(listener)).updateWorkflow(workflow);

        var order = inOrder(dao, listener);
        order.verify(dao).updateWorkflow(workflow);
        switch (status) {
            case COMPLETED -> order.verify(listener).onWorkflowCompleted(workflow);
            case FAILED -> order.verify(listener).onWorkflowFailed(workflow);
            case TIMED_OUT -> order.verify(listener).onWorkflowTimedOut(workflow);
            case TERMINATED -> order.verify(listener).onWorkflowTerminated(workflow);
            default -> throw new AssertionError(status);
        }
        order.verifyNoMoreInteractions();
    }

    @Test
    void savesRunningWorkflowWithoutNotifyingListeners() {
        WorkflowStatusListener listener = mock(WorkflowStatusListener.class);
        WorkflowModel workflow = workflow(WorkflowModel.Status.RUNNING);

        executor(List.of(listener)).updateWorkflow(workflow);

        verify(dao).updateWorkflow(workflow);
        verifyNoInteractions(listener);
    }

    @Test
    void persistenceFailureDoesNotNotifyListeners() {
        WorkflowStatusListener listener = mock(WorkflowStatusListener.class);
        WorkflowModel workflow = workflow(WorkflowModel.Status.COMPLETED);
        IllegalStateException failure = new IllegalStateException("save failed");
        when(dao.updateWorkflow(workflow)).thenThrow(failure);

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> executor(List.of(listener, completions)).updateWorkflow(workflow)));

        verifyNoInteractions(listener);
    }

    @Test
    void listenerFailureDoesNotPreventOtherListenersOrCompletionWaiter() {
        WorkflowStatusListener failing = mock(WorkflowStatusListener.class);
        WorkflowStatusListener observer = mock(WorkflowStatusListener.class);
        WorkflowModel workflow = workflow(WorkflowModel.Status.COMPLETED);
        doThrow(new IllegalStateException("listener failed"))
                .when(failing).onWorkflowCompleted(workflow);
        WorkflowExecutorOps executor = executor(List.of(failing, completions, observer));

        try (var completion = completions.register("workflow")) {
            assertDoesNotThrow(() -> executor.updateWorkflow(workflow));
            assertTimeoutPreemptively(Duration.ofSeconds(2), completion::await);
        }

        verify(observer).onWorkflowCompleted(workflow);
    }

    @Test
    void observingAlreadyTerminalWorkflowDoesNotNotifyAgain() {
        WorkflowStatusListener listener = mock(WorkflowStatusListener.class);
        WorkflowModel workflow = workflow(WorkflowModel.Status.TERMINATED);
        when(dao.getWorkflowModel("workflow")).thenReturn(workflow);

        executor(List.of(listener)).terminateWorkflow(workflow, "already stopped");

        verify(dao, never()).updateWorkflow(any());
        verifyNoInteractions(listener);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void notifiesStartedAfterCreationBeforeFirstDecision(boolean synchronous) {
        WorkflowStatusListener listener = mock(WorkflowStatusListener.class);
        Thread savingThread = Thread.currentThread();
        doAnswer(invocation -> {
            WorkflowModel workflow = invocation.getArgument(0);
            assertEquals(WorkflowModel.Status.RUNNING, workflow.getStatus());
            assertSame(savingThread, Thread.currentThread());
            return null;
        }).when(listener).onWorkflowStarted(any());
        StartWorkflowInput input = completingInput();
        WorkflowExecutorOps executor = executor(List.of(listener, completions));

        if (synchronous) {
            assertEquals(WorkflowModel.Status.COMPLETED, executor.startWorkflowSync(input).getStatus());
        } else {
            assertEquals("workflow", executor.startWorkflow(input));
        }

        var order = inOrder(dao, listener, decider);
        order.verify(dao).createWorkflow(any());
        order.verify(listener).onWorkflowStarted(any());
        order.verify(decider).decide(any());
        verify(listener).onWorkflowCompleted(any());
        verifyNoMoreInteractions(listener);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void creationFailureDoesNotNotifyStarted(boolean synchronous) {
        WorkflowStatusListener listener = mock(WorkflowStatusListener.class);
        StartWorkflowInput input = completingInput();
        IllegalStateException failure = new IllegalStateException("create failed");
        when(dao.createWorkflow(any())).thenThrow(failure);
        WorkflowExecutorOps executor = executor(List.of(listener, completions));

        assertSame(failure, assertThrows(IllegalStateException.class, () -> {
            if (synchronous) {
                executor.startWorkflowSync(input);
            } else {
                executor.startWorkflow(input);
            }
        }));

        verifyNoInteractions(listener, decider);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void startedListenerFailureDoesNotPreventDecisionOrOtherListeners(boolean synchronous) {
        WorkflowStatusListener failing = mock(WorkflowStatusListener.class);
        WorkflowStatusListener observer = mock(WorkflowStatusListener.class);
        doThrow(new IllegalStateException("started listener failed")).when(failing).onWorkflowStarted(any());
        StartWorkflowInput input = completingInput();
        WorkflowExecutorOps executor = executor(List.of(failing, observer, completions));

        if (synchronous) {
            assertEquals(WorkflowModel.Status.COMPLETED, executor.startWorkflowSync(input).getStatus());
        } else {
            assertEquals("workflow", executor.startWorkflow(input));
        }

        verify(observer).onWorkflowStarted(any());
        verify(observer).onWorkflowCompleted(any());
        verify(decider).decide(any());
        verify(dao, never()).removeWorkflow(any());
    }

    @ParameterizedTest
    @EnumSource(WorkflowEventType.class)
    void serializesSupportedLifecycleEventsInLowercase(WorkflowEventType event) throws Exception {
        assertEquals('"' + event.name().toLowerCase(Locale.ROOT) + '"',
                new ObjectMapper().writeValueAsString(event));
    }

    private StartWorkflowInput completingInput() {
        WorkflowDef definition = new WorkflowDef();
        definition.setRoute("listener-test");
        when(metadata.getWorkflowDef("listener-test", null)).thenReturn(definition);
        DeciderService.DeciderOutcome outcome = mock(DeciderService.DeciderOutcome.class);
        outcome.isComplete = true;
        outcome.tasksToBeUpdated = List.of();
        when(decider.decide(any())).thenReturn(outcome);
        StartWorkflowInput input = new StartWorkflowInput();
        input.setRoute("listener-test");
        input.setWorkflowId("workflow");
        return input;
    }

    private WorkflowExecutorOps executor(List<WorkflowStatusListener> listeners) {
        return new WorkflowExecutorOps(decider, null, dao, metadata, new IDGenerator(), null,
                new InMemoryExecutionLockService(),
                new InMemoryExecutionQueue(new InMemoryQueueDAO(), Runnable::run),
                completions, listeners);
    }

    private static WorkflowModel workflow(WorkflowModel.Status status) {
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("workflow");
        workflow.setStatus(status);
        return workflow;
    }
}
