package com.helianhealth.iflow.core.execution;

import com.helianhealth.iflow.core.dal.ExecutionDAOFacade;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class WorkflowDriveContextTest {

    @Test
    void tracksSubmittedBranchIdsOnceAndKeepsContextsIsolated() {
        TaskModel first = task("same-id");
        TaskModel second = task("same-id");
        try (WorkflowDriveContext firstDrive = new WorkflowDriveContext();
                WorkflowDriveContext secondDrive = new WorkflowDriveContext()) {
            assertTrue(firstDrive.submitBranch(first));
            assertFalse(firstDrive.submitBranch(first));
            assertFalse(firstDrive.submitBranch(second));
            assertEquals(1, firstDrive.outstandingBranches());
            assertEquals(0, secondDrive.outstandingBranches());
            assertTrue(secondDrive.submitBranch(second));
            assertEquals(1, secondDrive.outstandingBranches());

            firstDrive.publishCompleted(first, result(first, "first drive"));
            assertTrue(firstDrive.consumeEvents(new WorkflowModel()));
            assertEquals(0, firstDrive.outstandingBranches());
            assertEquals(1, secondDrive.outstandingBranches());
        }
    }

    @Test
    void completionReclaimsOnlyItsOwnSubmittedBranch() {
        TaskModel first = task("first");
        TaskModel second = task("second");
        try (WorkflowDriveContext context = new WorkflowDriveContext()) {
            assertTrue(context.submitBranch(first));
            assertTrue(context.submitBranch(second));
            assertEquals(2, context.outstandingBranches());

            context.publishCompleted(first, result(first, "finished"));
            assertTrue(context.consumeEvents(new WorkflowModel()));
            assertEquals(1, context.outstandingBranches());
            assertFalse(context.submitBranch(second));
            assertEquals(TaskModel.Status.SCHEDULED, second.getStatus());

            context.publishCompleted(second, result(second, "also finished"));
            assertTrue(context.consumeEvents(new WorkflowModel()));
            assertEquals(0, context.outstandingBranches());
        }
    }

    @Test
    void publishesStartedThenCompletedWhilePreservingTaskIdentityAndScope() {
        ExecutionDAOFacade executionDAOFacade = mock(ExecutionDAOFacade.class);
        WorkflowModel workflow = new WorkflowModel();
        TaskModel target = task("canonical");
        target.setForkTaskId("original-fork");
        target.setIteration(3);
        target.setParentTaskId("original-parent");
        try (WorkflowDriveContext context = new WorkflowDriveContext(executionDAOFacade)) {
            assertTrue(context.submitBranch(target));
            context.publishStarted(target, 42);
            assertEquals(TaskModel.Status.SCHEDULED, target.getStatus());
            assertEquals(0, target.getStartTime());
            verify(executionDAOFacade, never()).updateTask(target);

            assertTrue(context.consumeEvents(workflow));
            assertEquals(TaskModel.Status.IN_PROGRESS, target.getStatus());
            assertEquals(TaskModel.Status.SCHEDULED, target.getPreviousStatus());
            assertEquals(42, target.getStartTime());
            assertEquals(1, context.outstandingBranches());
            verify(executionDAOFacade).updateTask(target);
            assertEquals(List.of(target), context.drainDirtyTasks());

            TaskModel result = result(target, "published");
            result.setTaskId("worker-id");
            result.setForkTaskId("worker-fork");
            result.setIteration(100);
            result.setParentTaskId("worker-parent");
            context.publishCompleted(target, result);
            assertEquals(TaskModel.Status.IN_PROGRESS, target.getStatus());
            assertTrue(context.consumeEvents(workflow));

            assertEquals(TaskModel.Status.COMPLETED, target.getStatus());
            assertEquals(TaskModel.Status.IN_PROGRESS, target.getPreviousStatus());
            assertEquals("published", target.getOutputData());
            assertEquals(42, target.getStartTime());
            assertEquals(55, target.getEndTime());
            assertEquals("canonical", target.getTaskId());
            assertEquals("original-fork", target.getForkTaskId());
            assertEquals("original-parent", target.getParentTaskId());
            assertEquals(3, target.getIteration());
            assertEquals(0, context.outstandingBranches());
            assertEquals(List.of(target), context.drainDirtyTasks());
            assertFalse(context.consumeEvents(workflow));
        }
    }

    @Test
    void completionWithoutStartedReclaimsRejectedSubmissionAndPublishesFailure() {
        TaskModel target = task("rejected");
        TaskModel failed = task("rejected");
        failed.setStatus(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR);
        failed.setReasonForInCompletion("pool is full");
        failed.setEndTime(12);
        ExecutionDAOFacade executionDAOFacade = mock(ExecutionDAOFacade.class);
        try (WorkflowDriveContext context = new WorkflowDriveContext(executionDAOFacade)) {
            assertTrue(context.submitBranch(target));
            context.publishCompleted(target, failed);
            assertTrue(context.consumeEvents(new WorkflowModel()));
            assertEquals(0, context.outstandingBranches());
            assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, target.getStatus());
            assertEquals(TaskModel.Status.SCHEDULED, target.getPreviousStatus());
            assertEquals("pool is full", target.getReasonForInCompletion());
            assertEquals(0, target.getStartTime());
            verifyNoInteractions(executionDAOFacade);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void reclaimsCompletionAfterTaskOrWorkflowTerminates(boolean workflowTerminates) {
        WorkflowModel workflow = new WorkflowModel();
        TaskModel target = task("canceled");
        try (WorkflowDriveContext context = new WorkflowDriveContext()) {
            assertTrue(context.submitBranch(target));
            context.publishStarted(target, 42);
            context.publishCompleted(target, result(target, "must not be published"));
            if (workflowTerminates) {
                workflow.setStatus(WorkflowModel.Status.TERMINATED);
            } else {
                target.setStatus(TaskModel.Status.CANCELED);
            }
            assertTrue(context.consumeEvents(workflow));
            assertEquals(0, context.outstandingBranches());
            assertEquals(workflowTerminates ? TaskModel.Status.SCHEDULED : TaskModel.Status.CANCELED,
                    target.getStatus());
            assertEquals(Map.of(), target.getOutputData());
            assertEquals(0, target.getStartTime());
            assertEquals(List.of(), context.drainDirtyTasks());
        }
    }

    @Test
    void closeDiscardsQueuedEventsAndRejectsFurtherSubmissions() {
        WorkflowDriveContext context = new WorkflowDriveContext();
        TaskModel target = task("closed");
        context.markDirty(target);
        assertTrue(context.submitBranch(target));
        context.publishStarted(target, 42);
        context.close();
        context.publishCompleted(target, result(target, "late"));
        assertTrue(context.isClosed());
        assertFalse(context.hasEvents());
        assertEquals(List.of(), context.drainDirtyTasks());
        assertThrows(IllegalStateException.class, () -> context.submitBranch(target));
        assertEquals(TaskModel.Status.SCHEDULED, target.getStatus());
        assertEquals(Map.of(), target.getOutputData());
    }

    @Test
    void rejectsNonOwnerMutationsButAcceptsWorkerEvents() throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        WorkflowModel workflow = new WorkflowModel();
        TaskModel target = task("owned");
        try (WorkflowDriveContext context = new WorkflowDriveContext()) {
            assertTrue(context.submitBranch(target));
            worker.submit(() -> {
                assertThrows(IllegalStateException.class, () -> context.submitBranch(target));
                assertThrows(IllegalStateException.class, () -> context.consumeEvents(workflow));
                assertThrows(IllegalStateException.class, () -> context.markStarted(target, 42));
                assertThrows(IllegalStateException.class, context::close);
                context.publishStarted(target, 42);
                context.publishCompleted(target, result(target, List.of(1, 2)));
            }).get(2, TimeUnit.SECONDS);
            assertTrue(context.consumeEvents(workflow));
            assertEquals(TaskModel.Status.COMPLETED, target.getStatus());
            assertEquals(List.of(1, 2), target.getOutputData());
            assertEquals(0, context.outstandingBranches());
        } finally {
            worker.shutdownNow();
        }
    }

    @Test
    void notificationPublishedBeforeWaitingIsRetained() throws Exception {
        ExecutorService caller = Executors.newSingleThreadExecutor();
        try {
            caller.submit(() -> {
                TaskModel target = task("already-completed");
                try (WorkflowDriveContext context = new WorkflowDriveContext()) {
                    assertTrue(context.submitBranch(target));
                    context.publishStarted(target, 42);
                    context.publishCompleted(target, result(target, null));
                    context.awaitChange();
                    assertTrue(context.consumeEvents(new WorkflowModel()));
                    assertEquals(TaskModel.Status.COMPLETED, target.getStatus());
                    assertNull(target.getOutputData());
                    assertEquals(0, context.outstandingBranches());
                }
                return null;
            }).get(2, TimeUnit.SECONDS);
        } finally {
            caller.shutdownNow();
        }
    }

    @Test
    void notificationBetweenEmptyCheckAndWaitCannotBeLost() throws Exception {
        ExecutorService caller = Executors.newSingleThreadExecutor();
        CountDownLatch emptyChecked = new CountDownLatch(1);
        CountDownLatch allowWait = new CountDownLatch(1);
        AtomicReference<WorkflowDriveContext> current = new AtomicReference<>();
        TaskModel target = task("race");
        try {
            var driven = caller.submit(() -> {
                try (WorkflowDriveContext context = new WorkflowDriveContext()) {
                    current.set(context);
                    assertTrue(context.submitBranch(target));
                    assertFalse(context.hasEvents());
                    emptyChecked.countDown();
                    assertTrue(allowWait.await(2, TimeUnit.SECONDS));
                    context.awaitChange();
                    assertTrue(context.consumeEvents(new WorkflowModel()));
                    assertEquals(TaskModel.Status.COMPLETED, target.getStatus());
                    assertEquals(0, context.outstandingBranches());
                }
                return null;
            });
            assertTrue(emptyChecked.await(2, TimeUnit.SECONDS));
            current.get().publishCompleted(target, result(target, "race resolved"));
            allowWait.countDown();
            driven.get(2, TimeUnit.SECONDS);
        } finally {
            allowWait.countDown();
            caller.shutdownNow();
        }
    }

    @Test
    void oneWakeupConsumesBatchAndSeparateLaterWakeupStillWorks() throws Exception {
        ExecutorService caller = Executors.newSingleThreadExecutor();
        CountDownLatch firstBatchConsumed = new CountDownLatch(1);
        AtomicReference<WorkflowDriveContext> current = new AtomicReference<>();
        TaskModel later = task("later");
        try {
            var driven = caller.submit(() -> {
                try (WorkflowDriveContext context = new WorkflowDriveContext()) {
                    current.set(context);
                    WorkflowModel workflow = new WorkflowModel();
                    for (int i = 0; i < 3; i++) {
                        TaskModel target = task("batch-" + i);
                        assertTrue(context.submitBranch(target));
                        context.publishStarted(target, 42);
                        context.publishCompleted(target, result(target, i));
                    }
                    assertTrue(context.submitBranch(later));
                    context.awaitChange();
                    context.consumeEvents(workflow);
                    assertEquals(1, context.outstandingBranches());
                    assertFalse(context.hasEvents());
                    firstBatchConsumed.countDown();
                    context.awaitChange();
                    context.consumeEvents(workflow);
                    assertEquals(0, context.outstandingBranches());
                    assertEquals(TaskModel.Status.COMPLETED, later.getStatus());
                }
                return null;
            });
            assertTrue(firstBatchConsumed.await(2, TimeUnit.SECONDS));
            current.get().publishCompleted(later, result(later, "last"));
            driven.get(2, TimeUnit.SECONDS);
        } finally {
            caller.shutdownNow();
        }
    }

    private static TaskModel task(String id) {
        TaskModel task = new TaskModel();
        task.setTaskId(id);
        task.setReferenceTaskName(id);
        task.setTaskType("NOOP");
        task.setStatus(TaskModel.Status.SCHEDULED);
        return task;
    }

    private static TaskModel result(TaskModel target, Object output) {
        TaskModel result = TaskModel.fromTask(target.toTask());
        result.setStartTime(42);
        result.setEndTime(55);
        result.setUpdateTime(55);
        result.setOutputData(output);
        result.setStatus(TaskModel.Status.COMPLETED);
        return result;
    }
}
