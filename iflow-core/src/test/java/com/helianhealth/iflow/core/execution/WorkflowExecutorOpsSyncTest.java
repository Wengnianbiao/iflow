package com.helianhealth.iflow.core.execution;

import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowDef;
import com.helianhealth.iflow.common.domain.run.StartWorkflowRequest;
import com.helianhealth.iflow.core.dal.ExecutionDAOFacade;
import com.helianhealth.iflow.core.lock.ExecutionLockService;
import com.helianhealth.iflow.core.lock.InMemoryLock;
import com.helianhealth.iflow.core.model.WorkflowModel;
import com.helianhealth.iflow.core.queue.InMemoryExecutionQueue;
import com.helianhealth.iflow.core.queue.InMemoryQueueDAO;
import com.helianhealth.iflow.core.service.MetadataService;
import com.helianhealth.iflow.core.utils.IDGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkflowExecutorOpsSyncTest {

    @Test
    void synchronousEntryCreatesWorkflowAndReturnsImmediatelyWhenTerminal() {
        Fixture fixture = new Fixture();
        AtomicReference<WorkflowModel> completed = new AtomicReference<>();
        fixture.start = workflow -> {
            assertEquals("pacs/apply", workflow.getWorkflowDefinition().getRoute());
            assertEquals(2, workflow.getWorkflowDefinition().getVersion());
            assertEquals(Map.of("id", 7), workflow.getInput());
            workflow.setStatus(WorkflowModel.Status.COMPLETED);
            completed.set(workflow);
            fixture.executor.updateWorkflow(completed.get());
        };

        WorkflowModel result = fixture.executor.startWorkflowSync(
                new StartWorkflowInput(request()));

        assertSame(completed.get(), result);
        verify(fixture.dao, never()).getWorkflowModel(any());
        try (var ignored = fixture.completions.register(result.getWorkflowId())) {
            // Returning must remove the previous registration.
        }
    }

    @Test
    void observesCompletionThatArrivesBeforeStartReturnsRunningSnapshot() {
        Fixture fixture = new Fixture();
        AtomicReference<WorkflowModel> completed = new AtomicReference<>();
        fixture.start = workflow -> {
            completed.set(workflow(workflow.getWorkflowId(), WorkflowModel.Status.COMPLETED));
            when(fixture.dao.getWorkflowModel(workflow.getWorkflowId())).thenReturn(completed.get());
            fixture.executor.updateWorkflow(completed.get());
        };

        WorkflowModel result = org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(
                java.time.Duration.ofSeconds(2), () -> fixture.executor.startWorkflowSync(
                        new StartWorkflowInput(request())));
        assertSame(completed.get(), result);
    }

    @Test
    void waitsForPersistedTerminalState() throws Exception {
        Fixture fixture = new Fixture();
        CountDownLatch started = new CountDownLatch(1);
        AtomicReference<String> id = new AtomicReference<>();
        fixture.start = workflow -> {
            id.set(workflow.getWorkflowId());
            started.countDown();
        };
        var caller = Executors.newSingleThreadExecutor();
        try {
            var result = caller.submit(() -> fixture.executor.startWorkflowSync(
                    new StartWorkflowInput(request())));
            assertTrue(started.await(2, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> result.get(50, TimeUnit.MILLISECONDS));
            WorkflowModel completed = workflow(id.get(), WorkflowModel.Status.COMPLETED);
            when(fixture.dao.getWorkflowModel(id.get())).thenReturn(completed);
            fixture.executor.updateWorkflow(completed);
            assertSame(completed, result.get(2, TimeUnit.SECONDS));
        } finally {
            caller.shutdownNow();
        }
    }

    @Test
    void interruptionOnlyStopsWaitingAndCleansRegistration() throws Exception {
        Fixture fixture = new Fixture();
        CountDownLatch started = new CountDownLatch(1);
        AtomicReference<Thread> thread = new AtomicReference<>();
        AtomicReference<String> id = new AtomicReference<>();
        AtomicBoolean interrupted = new AtomicBoolean();
        fixture.start = workflow -> {
            id.set(workflow.getWorkflowId());
            thread.set(Thread.currentThread());
            started.countDown();
        };
        var caller = Executors.newSingleThreadExecutor();
        try {
            var result = caller.submit(() -> {
                assertThrows(IllegalStateException.class, () -> fixture.executor.startWorkflowSync(
                        new StartWorkflowInput(request())));
                interrupted.set(Thread.currentThread().isInterrupted());
            });
            assertTrue(started.await(2, TimeUnit.SECONDS));
            thread.get().interrupt();
            result.get(2, TimeUnit.SECONDS);
            assertTrue(interrupted.get());
            verify(fixture.dao, never()).getWorkflowModel(any());
            try (var ignored = fixture.completions.register(id.get())) { }
        } finally {
            caller.shutdownNow();
        }
    }

    @Test
    void removesCompletionRegistrationWhenStartThrows() {
        Fixture fixture = new Fixture();
        when(fixture.metadata.getWorkflowDef(any(), any()))
                .thenThrow(new IllegalArgumentException("invalid definition"));
        StartWorkflowInput input = new StartWorkflowInput(request());
        input.setWorkflowId("invalid");
        assertThrows(IllegalArgumentException.class,
                () -> fixture.executor.startWorkflowSync(input));
        try (var ignored = fixture.completions.register("invalid")) { }
        verifyNoInteractions(fixture.dao);
    }

    private static WorkflowModel workflow(String id, WorkflowModel.Status status) {
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId(id);
        workflow.setStatus(status);
        return workflow;
    }

    private static StartWorkflowRequest request() {
        return StartWorkflowRequest.builder().route("pacs/apply").version(2).input(Map.of("id", 7)).build();
    }

    @Test
    void ordinaryEntryReturnsIdWithoutRegisteringCompletionWaiter() {
        Fixture fixture = new Fixture();
        AtomicReference<WorkflowModel> started = new AtomicReference<>();
        fixture.start = workflow -> {
            started.set(workflow);
            try (var ignored = fixture.completions.register(workflow.getWorkflowId())) { }
        };

        String workflowId = fixture.executor.startWorkflow(
                new StartWorkflowInput(request()));

        assertNotNull(workflowId);
        assertEquals(started.get().getWorkflowId(), workflowId);
        assertEquals(WorkflowModel.Status.RUNNING, started.get().getStatus());
        verify(fixture.dao, never()).getWorkflowModel(any());
    }

    @Test
    void ordinaryEntryUsesProvidedWorkflowId() {
        Fixture fixture = new Fixture();
        StartWorkflowInput input = new StartWorkflowInput(request());
        input.setWorkflowId("provided-id");

        assertEquals("provided-id", fixture.executor.startWorkflow(input));
        verify(fixture.dao).createWorkflow(argThat(workflow -> "provided-id".equals(workflow.getWorkflowId())));
    }

    @Test
    void ordinaryEntryRemovesPartialInstanceWhenPersistenceFails() {
        Fixture fixture = new Fixture();
        IllegalStateException failure = new IllegalStateException("persistence failed");
        when(fixture.dao.createWorkflow(any())).thenThrow(failure);
        StartWorkflowInput input = new StartWorkflowInput(request());
        input.setWorkflowId("partial");

        assertSame(failure, assertThrows(IllegalStateException.class, () -> fixture.executor.startWorkflow(input)));

        verify(fixture.dao).removeWorkflow("partial");
        verifyNoInteractions(fixture.decider);
    }

    @Test
    void ordinaryEntryPreservesDecisionErrorWhenCleanupAlsoFails() {
        Fixture fixture = new Fixture();
        IllegalStateException failure = new IllegalStateException("initial decision failed");
        fixture.start = workflow -> { throw failure; };
        doThrow(new IllegalStateException("cleanup failed")).when(fixture.dao).removeWorkflow("decision-error");
        StartWorkflowInput input = new StartWorkflowInput(request());
        input.setWorkflowId("decision-error");

        assertSame(failure, assertThrows(IllegalStateException.class, () -> fixture.executor.startWorkflow(input)));

        verify(fixture.dao).removeWorkflow("decision-error");
    }

    @Test
    void synchronousEntryCleansCompletionRegistrationWhenPersistenceFails() {
        Fixture fixture = new Fixture();
        IllegalStateException failure = new IllegalStateException("persistence failed");
        when(fixture.dao.createWorkflow(any())).thenThrow(failure);
        StartWorkflowInput input = new StartWorkflowInput(request());
        input.setWorkflowId("sync-create-error");

        assertSame(failure, assertThrows(IllegalStateException.class, () -> fixture.executor.startWorkflowSync(input)));

        try (var ignored = fixture.completions.register("sync-create-error")) { }
        verify(fixture.dao, never()).removeWorkflow(any());
    }

    private static final class Fixture {
        private final ExecutionDAOFacade dao = mock(ExecutionDAOFacade.class);
        private final MetadataService metadata = mock(MetadataService.class);
        private final DeciderService decider = mock(DeciderService.class);
        private final WorkflowCompletionRegistry completions = new WorkflowCompletionRegistry();
        private final AtomicInteger starts = new AtomicInteger();
        private Consumer<WorkflowModel> start = workflow -> { };
        private final WorkflowExecutorOps executor = new WorkflowExecutorOps(
                decider, null, dao, metadata, new IDGenerator(), null, new ExecutionLockService(new InMemoryLock()),
                new InMemoryExecutionQueue(new InMemoryQueueDAO(), Runnable::run), completions,
                List.of(completions));

        private Fixture() {
            WorkflowDef definition = new WorkflowDef();
            definition.setRoute("pacs/apply");
            definition.setVersion(2);
            when(metadata.getWorkflowDef("pacs/apply", 2)).thenReturn(definition);
            when(decider.decide(any())).thenAnswer(invocation -> {
                assertEquals(1, starts.incrementAndGet(), "start must run the first decision exactly once");
                start.accept(invocation.getArgument(0));
                DeciderService.DeciderOutcome outcome = mock(DeciderService.DeciderOutcome.class);
                outcome.tasksToBeScheduled = List.of();
                outcome.tasksToBeUpdated = List.of();
                return outcome;
            });
        }
    }
}
