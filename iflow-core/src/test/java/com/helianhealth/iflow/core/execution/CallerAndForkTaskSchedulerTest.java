package com.helianhealth.iflow.core.execution;

import com.helianhealth.iflow.common.domain.metadata.task.TaskType;
import com.helianhealth.iflow.core.execution.tasks.SystemTaskRegistry;
import com.helianhealth.iflow.core.execution.tasks.WorkflowSystemTask;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CallerAndForkTaskSchedulerTest {

    @Test
    void startsLinearBusinessOnCallerAndNeverEvaluatesItsBusinessAgain() {
        String caller = Thread.currentThread().getName();
        AtomicInteger starts = new AtomicInteger();
        WorkflowSystemTask action = new WorkflowSystemTask(TaskType.TASK_TYPE_NOOP) {
            @Override
            public void start(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
                assertEquals(caller, Thread.currentThread().getName());
                assertEquals(TaskModel.Status.IN_PROGRESS, task.getStatus());
                assertTrue(task.getStartTime() >= task.getScheduledTime());
                starts.incrementAndGet();
                task.setOutputData(List.of(1, 2));
                task.setStatus(TaskModel.Status.COMPLETED);
            }

            @Override
            public boolean execute(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
                throw new AssertionError("business tasks must not be evaluated");
            }
        };
        CallerAndForkTaskScheduler scheduler = scheduler(
                command -> { throw new AssertionError("linear tasks must use the caller"); }, action);
        TaskModel task = task("linear", TaskType.TASK_TYPE_NOOP, false);
        WorkflowModel workflow = workflow(task);
        try (WorkflowDriveContext context = new WorkflowDriveContext()) {
            assertTrue(scheduler.schedule(workflow, List.of(task), null, context));
            assertFalse(scheduler.schedule(workflow, List.of(task), null, context));
            assertFalse(scheduler.evaluate(workflow, List.of(task), null, context));

            assertEquals(1, starts.get());
            assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
            assertEquals(TaskModel.Status.IN_PROGRESS, task.getPreviousStatus());
            assertEquals(List.of(1, 2), task.getOutputData());
            assertTrue(task.getEndTime() >= task.getStartTime());
            assertEquals(task.getEndTime(), task.getUpdateTime());
            assertEquals(0, context.outstandingBranches());
        }
    }

    @Test
    void repeatedQueuedCandidateKeepsScheduledStatusAndSubmitsOnlyOnce() {
        List<Runnable> queued = new ArrayList<>();
        AtomicInteger starts = new AtomicInteger();
        CallerAndForkTaskScheduler scheduler = scheduler(command -> queued.add(command), completingAction(starts));
        TaskModel task = task("branch", TaskType.TASK_TYPE_NOOP, true);
        WorkflowModel workflow = workflow(task);
        try (WorkflowDriveContext context = new WorkflowDriveContext()) {
            assertFalse(scheduler.schedule(workflow, List.of(task), null, context));
            assertFalse(scheduler.schedule(workflow, List.of(task), null, context));
            assertFalse(scheduler.schedule(workflow, List.of(task), null, context));
            scheduler.evaluate(workflow, List.of(task), null, context);
            scheduler.evaluate(workflow, List.of(task), null, context);

            assertEquals(1, queued.size());
            assertEquals(1, context.outstandingBranches());
            assertEquals(TaskModel.Status.SCHEDULED, task.getStatus());
            assertEquals(0, task.getStartTime());
            assertEquals(0, starts.get());

            queued.get(0).run();
            assertEquals(TaskModel.Status.SCHEDULED, task.getStatus(),
                    "only the caller consuming events may change the canonical task");
            assertFalse(scheduler.schedule(workflow, List.of(task), null, context));
            assertEquals(1, queued.size(), "pending completion still owns the submission");
            assertTrue(context.consumeEvents(workflow));
            assertEquals(1, starts.get());
            assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
            assertEquals(TaskModel.Status.IN_PROGRESS, task.getPreviousStatus());
            assertEquals(0, context.outstandingBranches());
            assertFalse(scheduler.schedule(workflow, List.of(task), null, context));
            assertEquals(1, queued.size());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void repeatedRunningCandidateDoesNotResubmitBeforeOrAfterStartedEvent(boolean consumeStarted)
            throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger starts = new AtomicInteger();
        AtomicInteger submissions = new AtomicInteger();
        ExecutorService branches = Executors.newSingleThreadExecutor();
        WorkflowSystemTask action = new WorkflowSystemTask(TaskType.TASK_TYPE_NOOP) {
            @Override
            public void start(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
                starts.incrementAndGet();
                started.countDown();
                await(release);
                task.setStatus(TaskModel.Status.COMPLETED);
            }
        };
        Executor trackingExecutor = command -> {
            submissions.incrementAndGet();
            branches.execute(command);
        };
        CallerAndForkTaskScheduler scheduler = scheduler(trackingExecutor, action);
        TaskModel task = task("running", TaskType.TASK_TYPE_NOOP, true);
        WorkflowModel workflow = workflow(task);
        try (WorkflowDriveContext context = new WorkflowDriveContext()) {
            try {
                scheduler.schedule(workflow, List.of(task), null, context);
                assertTrue(started.await(2, TimeUnit.SECONDS));
                if (consumeStarted) {
                    assertTrue(context.consumeEvents(workflow));
                    assertEquals(TaskModel.Status.IN_PROGRESS, task.getStatus());
                } else {
                    assertEquals(TaskModel.Status.SCHEDULED, task.getStatus());
                }

                assertFalse(scheduler.schedule(workflow, List.of(task), null, context));
                assertFalse(scheduler.schedule(workflow, List.of(task), null, context));
                assertEquals(1, starts.get());
                assertEquals(1, submissions.get());
                assertEquals(1, context.outstandingBranches());

                release.countDown();
                branches.shutdown();
                assertTrue(branches.awaitTermination(2, TimeUnit.SECONDS));
                assertTrue(context.consumeEvents(workflow));
                assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
                assertEquals(0, context.outstandingBranches());
            } finally {
                release.countDown();
                branches.shutdownNow();
            }
        }
    }

    @Test
    void submitsEveryReadyBranchWithoutWaitingForTheFirst() {
        List<Runnable> queued = new ArrayList<>();
        AtomicInteger starts = new AtomicInteger();
        CallerAndForkTaskScheduler scheduler = scheduler(command -> queued.add(command), completingAction(starts));
        TaskModel first = task("first", TaskType.TASK_TYPE_NOOP, true);
        TaskModel second = task("second", TaskType.TASK_TYPE_NOOP, true);
        WorkflowModel workflow = workflow(first, second);
        try (WorkflowDriveContext context = new WorkflowDriveContext()) {
            scheduler.schedule(workflow, List.of(first, second), null, context);
            assertEquals(2, queued.size());
            assertEquals(2, context.outstandingBranches());
            assertEquals(0, starts.get());

            queued.get(1).run();
            context.consumeEvents(workflow);
            assertEquals(TaskModel.Status.SCHEDULED, first.getStatus());
            assertEquals(TaskModel.Status.COMPLETED, second.getStatus());
            assertEquals(1, context.outstandingBranches());
            queued.get(0).run();
            context.consumeEvents(workflow);
            assertEquals(0, context.outstandingBranches());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {TaskType.TASK_TYPE_JOIN, TaskType.TASK_TYPE_EXCLUSIVE_JOIN,
            TaskType.TASK_TYPE_DO_WHILE})
    void startsControlOnceAndEvaluatesItRepeatedlyOnCallerInsideFork(String type) {
        String caller = Thread.currentThread().getName();
        AtomicInteger starts = new AtomicInteger();
        AtomicInteger evaluations = new AtomicInteger();
        WorkflowSystemTask control = new WorkflowSystemTask(type) {
            @Override
            public void start(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
                assertEquals(caller, Thread.currentThread().getName());
                starts.incrementAndGet();
            }

            @Override
            public boolean execute(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
                assertEquals(caller, Thread.currentThread().getName());
                evaluations.incrementAndGet();
                return false;
            }
        };
        CallerAndForkTaskScheduler scheduler = scheduler(
                command -> { throw new AssertionError("control tasks must use the caller"); }, control);
        TaskModel task = task("control", type, true);
        WorkflowModel workflow = workflow(task);
        try (WorkflowDriveContext context = new WorkflowDriveContext()) {
            assertTrue(scheduler.schedule(workflow, List.of(task), null, context));
            long started = task.getStartTime();
            assertFalse(scheduler.schedule(workflow, List.of(task), null, context));
            assertFalse(scheduler.evaluate(workflow, List.of(task), null, context));
            assertFalse(scheduler.evaluate(workflow, List.of(task), null, context));

            assertEquals(1, starts.get());
            assertEquals(2, evaluations.get());
            assertEquals(started, task.getStartTime());
            assertEquals(TaskModel.Status.IN_PROGRESS, task.getStatus());
            assertEquals(0, task.getEndTime());
            assertEquals(0, context.outstandingBranches());
        }
    }

    @Test
    void forwardsControlProgressAndTurnsEvaluationExceptionIntoFailure() {
        WorkflowSystemTask control = new WorkflowSystemTask(TaskType.TASK_TYPE_JOIN) {
            @Override
            public boolean execute(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
                if (task.getTaskId().equals("failed")) {
                    throw new IllegalArgumentException("invalid join input");
                }
                task.setStatus(TaskModel.Status.COMPLETED);
                return true;
            }
        };
        CallerAndForkTaskScheduler scheduler = scheduler(Runnable::run, control);
        TaskModel completed = task("completed", TaskType.TASK_TYPE_JOIN, false);
        TaskModel failed = task("failed", TaskType.TASK_TYPE_JOIN, false);
        WorkflowModel workflow = workflow(completed, failed);
        try (WorkflowDriveContext context = new WorkflowDriveContext()) {
            scheduler.schedule(workflow, List.of(completed, failed), null, context);
            assertTrue(scheduler.evaluate(workflow, List.of(completed, failed), null, context));
            assertEquals(TaskModel.Status.COMPLETED, completed.getStatus());
            assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, failed.getStatus());
            assertTrue(failed.getReasonForInCompletion().contains("invalid join input"));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void turnsStartExceptionIntoTerminalFailureAndReclaimsBranch(boolean forkBranch) {
        WorkflowSystemTask action = new WorkflowSystemTask(TaskType.TASK_TYPE_NOOP) {
            @Override
            public void start(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
                throw new IllegalArgumentException("remote failure");
            }
        };
        TaskModel task = task("failed", TaskType.TASK_TYPE_NOOP, forkBranch);
        WorkflowModel workflow = workflow(task);
        try (WorkflowDriveContext context = new WorkflowDriveContext()) {
            scheduler(Runnable::run, action).schedule(workflow, List.of(task), null, context);
            context.consumeEvents(workflow);
            assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, task.getStatus());
            assertTrue(task.getReasonForInCompletion().contains("remote failure"));
            assertEquals(0, context.outstandingBranches());
            assertTrue(task.getEndTime() > 0);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void failsBusinessStartThatReturnsBeforeTerminalState(boolean forkBranch) {
        TaskModel task = task("unfinished", TaskType.TASK_TYPE_NOOP, forkBranch);
        WorkflowModel workflow = workflow(task);
        WorkflowSystemTask action = new WorkflowSystemTask(TaskType.TASK_TYPE_NOOP) {};
        try (WorkflowDriveContext context = new WorkflowDriveContext()) {
            scheduler(Runnable::run, action).schedule(workflow, List.of(task), null, context);
            context.consumeEvents(workflow);
            assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, task.getStatus());
            assertTrue(task.getReasonForInCompletion().contains("start()"));
            assertEquals(0, context.outstandingBranches());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void publishesSubmissionFailureAndReclaimsOutstanding(boolean rejected) {
        AtomicInteger submissions = new AtomicInteger();
        Executor unavailable = command -> {
            submissions.incrementAndGet();
            if (rejected) {
                throw new RejectedExecutionException("full");
            }
            throw new IllegalStateException("executor unavailable");
        };
        TaskModel task = task("rejected", TaskType.TASK_TYPE_NOOP, true);
        WorkflowModel workflow = workflow(task);
        CallerAndForkTaskScheduler scheduler = scheduler(unavailable, completingAction(new AtomicInteger()));
        try (WorkflowDriveContext context = new WorkflowDriveContext()) {
            scheduler.schedule(workflow, List.of(task), null, context);
            assertFalse(scheduler.schedule(workflow, List.of(task), null, context));
            assertEquals(1, submissions.get());
            assertEquals(TaskModel.Status.SCHEDULED, task.getStatus());
            assertEquals(1, context.outstandingBranches());
            assertTrue(context.consumeEvents(workflow));
            assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, task.getStatus());
            assertTrue(task.getReasonForInCompletion().contains(
                    rejected ? "线程池已满" : "executor unavailable"));
            assertEquals(0, task.getStartTime());
            assertEquals(0, context.outstandingBranches());
            assertFalse(scheduler.schedule(workflow, List.of(task), null, context));
            assertEquals(1, submissions.get());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void hidesEarlyTerminalStatusAndNestedInputChangesUntilWorkerReturns() throws Exception {
        CountDownLatch prematureCompletion = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        TaskModel canonical = task("isolated", TaskType.TASK_TYPE_NOOP, true);
        canonical.setInputData(new LinkedHashMap<>(Map.of("nested",
                new LinkedHashMap<>(Map.of("value", "original")))));
        WorkflowSystemTask action = new WorkflowSystemTask(TaskType.TASK_TYPE_NOOP) {
            @Override
            public void start(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
                assertNotSame(canonical, task);
                ((Map<String, Object>) task.getInputData().get("nested")).put("value", "changed");
                task.setStatus(TaskModel.Status.COMPLETED);
                prematureCompletion.countDown();
                await(release);
                task.setOutputData(List.of("fully", "published"));
            }
        };
        ExecutorService branches = Executors.newSingleThreadExecutor();
        WorkflowModel workflow = workflow(canonical);
        try (WorkflowDriveContext context = new WorkflowDriveContext()) {
            try {
                scheduler(branches, action).schedule(workflow, List.of(canonical), null, context);
                assertTrue(prematureCompletion.await(2, TimeUnit.SECONDS));
                context.consumeEvents(workflow);
                assertEquals(TaskModel.Status.IN_PROGRESS, canonical.getStatus());
                assertEquals(Map.of("nested", Map.of("value", "original")), canonical.getInputData());
                assertEquals(Map.of(), canonical.getOutputData());
                assertEquals(1, context.outstandingBranches());

                release.countDown();
                branches.shutdown();
                assertTrue(branches.awaitTermination(2, TimeUnit.SECONDS));
                context.consumeEvents(workflow);
                assertEquals(TaskModel.Status.COMPLETED, canonical.getStatus());
                assertEquals(TaskModel.Status.IN_PROGRESS, canonical.getPreviousStatus());
                assertEquals(List.of("fully", "published"), canonical.getOutputData());
                assertEquals(Map.of("nested", Map.of("value", "changed")), canonical.getInputData());
                assertEquals(0, context.outstandingBranches());
            } finally {
                release.countDown();
                branches.shutdownNow();
            }
        }
    }

    @Test
    void doesNotRunQueuedBranchAfterDriveCloses() {
        List<Runnable> queued = new ArrayList<>();
        AtomicInteger starts = new AtomicInteger();
        TaskModel task = task("queued", TaskType.TASK_TYPE_NOOP, true);
        WorkflowModel workflow = workflow(task);
        WorkflowDriveContext context = new WorkflowDriveContext();
        scheduler(queued::add, completingAction(starts)).schedule(workflow, List.of(task), null, context);
        context.close();
        task.setStatus(TaskModel.Status.CANCELED);
        workflow.setStatus(WorkflowModel.Status.TERMINATED);
        queued.get(0).run();
        assertEquals(0, starts.get());
        assertFalse(context.hasEvents());
        assertEquals(TaskModel.Status.CANCELED, task.getStatus());
    }

    @Test
    void ignoresLateWorkerResultAfterDriveCloses() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        WorkflowSystemTask action = new WorkflowSystemTask(TaskType.TASK_TYPE_NOOP) {
            @Override
            public void start(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
                started.countDown();
                await(release);
                task.setOutputData("late output");
                task.setStatus(TaskModel.Status.COMPLETED);
            }
        };
        ExecutorService branches = Executors.newSingleThreadExecutor();
        TaskModel task = task("late", TaskType.TASK_TYPE_NOOP, true);
        WorkflowModel workflow = workflow(task);
        try (WorkflowDriveContext context = new WorkflowDriveContext()) {
            try {
                scheduler(branches, action).schedule(workflow, List.of(task), null, context);
                assertTrue(started.await(2, TimeUnit.SECONDS));
                context.consumeEvents(workflow);
                task.setStatus(TaskModel.Status.CANCELED);
                workflow.setStatus(WorkflowModel.Status.TERMINATED);
                context.close();
                release.countDown();
                branches.shutdown();
                assertTrue(branches.awaitTermination(2, TimeUnit.SECONDS));
                assertEquals(TaskModel.Status.CANCELED, task.getStatus());
                assertEquals(Map.of(), task.getOutputData());
                assertFalse(context.hasEvents());
            } finally {
                release.countDown();
                branches.shutdownNow();
            }
        }
    }

    private static CallerAndForkTaskScheduler scheduler(Executor executor, WorkflowSystemTask task) {
        return new CallerAndForkTaskScheduler(executor, new SystemTaskRegistry(Set.of(task)));
    }

    private static WorkflowSystemTask completingAction(AtomicInteger starts) {
        return new WorkflowSystemTask(TaskType.TASK_TYPE_NOOP) {
            @Override
            public void start(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
                starts.incrementAndGet();
                task.setStatus(TaskModel.Status.COMPLETED);
            }
        };
    }

    private static WorkflowModel workflow(TaskModel... tasks) {
        WorkflowModel workflow = new WorkflowModel();
        workflow.getTasks().addAll(List.of(tasks));
        return workflow;
    }

    private static TaskModel task(String id, String type, boolean forkBranch) {
        TaskModel task = new TaskModel();
        task.setTaskId(id);
        task.setReferenceTaskName(id);
        task.setTaskType(type);
        task.setScheduledTime(1);
        task.setStatus(TaskModel.Status.SCHEDULED);
        task.setForkTaskId(forkBranch ? "fork" : null);
        return task;
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(error);
        }
    }
}
