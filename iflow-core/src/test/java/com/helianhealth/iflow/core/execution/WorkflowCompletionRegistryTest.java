package com.helianhealth.iflow.core.execution;

import com.helianhealth.iflow.core.model.WorkflowModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowCompletionRegistryTest {

    @ParameterizedTest
    @EnumSource(value = WorkflowModel.Status.class, names = "RUNNING", mode = EnumSource.Mode.EXCLUDE)
    void everyTerminalCallbackWakesWaiter(WorkflowModel.Status status) {
        WorkflowCompletionRegistry registry = new WorkflowCompletionRegistry();
        WorkflowModel workflow = workflow("workflow", status);
        try (var registration = registry.register("workflow")) {
            switch (status) {
                case COMPLETED -> registry.onWorkflowCompleted(workflow);
                case FAILED -> registry.onWorkflowFailed(workflow);
                case TIMED_OUT -> registry.onWorkflowTimedOut(workflow);
                case TERMINATED -> registry.onWorkflowTerminated(workflow);
                case INTERRUPTED -> registry.onWorkflowInterrupted(workflow);
                default -> throw new AssertionError(status);
            }
            assertTimeoutPreemptively(Duration.ofSeconds(2), registration::await);
        }
    }

    @Test
    void completionBeforeWaitingIsRetainedAndRepeatedCallbacksAreIdempotent() throws Exception {
        WorkflowCompletionRegistry registry = new WorkflowCompletionRegistry();
        var caller = Executors.newSingleThreadExecutor();
        try (var registration = registry.register("workflow")) {
            WorkflowModel workflow = workflow("workflow", WorkflowModel.Status.COMPLETED);
            registry.onWorkflowCompleted(workflow);
            registry.onWorkflowCompleted(workflow);
            assertTimeoutPreemptively(Duration.ofSeconds(2), registration::await);
            registry.onWorkflowCompleted(workflow);

            CountDownLatch waiting = new CountDownLatch(1);
            var duplicateWait = caller.submit(() -> {
                waiting.countDown();
                registration.await();
                return null;
            });
            assertTrue(waiting.await(2, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> duplicateWait.get(100, TimeUnit.MILLISECONDS));
            duplicateWait.cancel(true);
        } finally {
            caller.shutdownNow();
        }
    }

    @Test
    void onlyTerminalCallbackForRegisteredWorkflowWakesWaiter() throws Exception {
        WorkflowCompletionRegistry registry = new WorkflowCompletionRegistry();
        var caller = Executors.newSingleThreadExecutor();
        try (var registration = registry.register("workflow")) {
            var result = caller.submit(() -> {
                registration.await();
                return "completed";
            });
            registry.onWorkflowCompleted(workflow("other", WorkflowModel.Status.COMPLETED));
            registry.onWorkflowStarted(workflow("workflow", WorkflowModel.Status.RUNNING));
            assertThrows(TimeoutException.class, () -> result.get(100, TimeUnit.MILLISECONDS));

            registry.onWorkflowTerminated(workflow("workflow", WorkflowModel.Status.TERMINATED));
            assertEquals("completed", result.get(2, TimeUnit.SECONDS));
        } finally {
            caller.shutdownNow();
        }
    }

    @Test
    void closingOldRegistrationDoesNotRemoveNewWaiter() {
        WorkflowCompletionRegistry registry = new WorkflowCompletionRegistry();
        var previous = registry.register("workflow");
        previous.close();
        try (var current = registry.register("workflow")) {
            previous.close();
            registry.onWorkflowCompleted(workflow("workflow", WorkflowModel.Status.COMPLETED));
            assertTimeoutPreemptively(Duration.ofSeconds(2), current::await);
        }
        assertDoesNotThrow(() -> registry.onWorkflowCompleted(workflow("workflow", WorkflowModel.Status.COMPLETED)));
    }

    private static WorkflowModel workflow(String workflowId, WorkflowModel.Status status) {
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId(workflowId);
        workflow.setStatus(status);
        return workflow;
    }
}
