package com.helianhealth.iflow.core.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.iflow.common.domain.metadata.task.TaskType;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowTask;
import com.helianhealth.iflow.core.execution.mapper.HttpTaskMapper;
import com.helianhealth.iflow.core.execution.mapper.TaskMapperContext;
import com.helianhealth.iflow.core.execution.tasks.SystemTaskRegistry;
import com.helianhealth.iflow.core.execution.tasks.WorkflowSystemTask;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import com.helianhealth.iflow.core.utils.ParametersUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CallerAndForkTaskSchedulerTest {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void reusesRunningBranchAndSchedulesSuccessorBeforeWaiting(boolean joinBeforeSuccessor)
            throws Exception {
        CountDownLatch slowStarted = new CountDownLatch(1);
        CountDownLatch releaseSlow = new CountDownLatch(1);
        CountDownLatch successorStarted = new CountDownLatch(1);
        AtomicInteger slowExecutions = new AtomicInteger();
        WorkflowSystemTask action = new WorkflowSystemTask(TaskType.TASK_TYPE_NOOP) {
            @Override
            public void execute(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
                try {
                    switch (task.getTaskId()) {
                        case "slow" -> {
                            slowExecutions.incrementAndGet();
                            slowStarted.countDown();
                            assertTrue(releaseSlow.await(5, TimeUnit.SECONDS));
                        }
                        case "fast" -> assertTrue(slowStarted.await(2, TimeUnit.SECONDS));
                        case "successor" -> successorStarted.countDown();
                        default -> throw new AssertionError("unexpected task");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                task.setStatus(TaskModel.Status.COMPLETED);
            }
        };
        WorkflowSystemTask waitingJoin = new WorkflowSystemTask(TaskType.TASK_TYPE_JOIN) {};
        ExecutorService branches = Executors.newFixedThreadPool(3);
        ExecutorService caller = Executors.newSingleThreadExecutor();
        CallerAndForkTaskScheduler scheduler = new CallerAndForkTaskScheduler(
                branches, new SystemTaskRegistry(Set.of(action, waitingJoin)));
        WorkflowModel workflow = new WorkflowModel();
        TaskModel slow = task("slow", TaskType.TASK_TYPE_NOOP, true);
        TaskModel fast = task("fast", TaskType.TASK_TYPE_NOOP, true);
        TaskModel successor = task("successor", TaskType.TASK_TYPE_NOOP, true);
        TaskModel join = task("join", TaskType.TASK_TYPE_JOIN, false);
        workflow.getTasks().addAll(List.of(slow, fast, successor, join));
        try {
            caller.submit(() -> scheduler.schedule(workflow, List.of(slow, fast), null))
                    .get(2, TimeUnit.SECONDS);
            assertEquals(TaskModel.Status.IN_PROGRESS, slow.getStatus());

            List<TaskModel> nextRound = joinBeforeSuccessor
                    ? List.of(slow, join, successor) : List.of(slow, successor, join);
            var advanced = caller.submit(() -> scheduler.schedule(workflow, nextRound, null));
            assertTrue(successorStarted.await(2, TimeUnit.SECONDS),
                    "the fast branch successor must start while the slow branch is running");
            advanced.get(2, TimeUnit.SECONDS);

            CountDownLatch waitingRoundStarted = new CountDownLatch(1);
            var waiting = caller.submit(() -> {
                waitingRoundStarted.countDown();
                scheduler.schedule(workflow, List.of(), null);
            });
            assertTrue(waitingRoundStarted.await(2, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> waiting.get(100, TimeUnit.MILLISECONDS),
                    "a round without new tasks must wait for an existing execution");
            releaseSlow.countDown();
            waiting.get(2, TimeUnit.SECONDS);
            branches.shutdown();
            assertTrue(branches.awaitTermination(2, TimeUnit.SECONDS));
            assertEquals(1, slowExecutions.get());
        } finally {
            releaseSlow.countDown();
            scheduler.release(workflow);
            caller.shutdownNow();
            branches.shutdownNow();
        }
    }

    @Test
    void reservesQueuedTasksAndKeepsWorkflowsIsolated() {
        List<Runnable> queued = new ArrayList<>();
        AtomicInteger executions = new AtomicInteger();
        WorkflowSystemTask action = new WorkflowSystemTask(TaskType.TASK_TYPE_NOOP) {
            @Override
            public void execute(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
                if (task.isForkBranchTask()) {
                    executions.incrementAndGet();
                }
                task.setStatus(TaskModel.Status.COMPLETED);
            }
        };
        CallerAndForkTaskScheduler scheduler = new CallerAndForkTaskScheduler(
                queued::add, new SystemTaskRegistry(Set.of(action)));
        WorkflowModel first = new WorkflowModel();
        first.setWorkflowId("first");
        WorkflowModel second = new WorkflowModel();
        second.setWorkflowId("second");
        TaskModel firstBranch = task("same-id", TaskType.TASK_TYPE_NOOP, true);
        TaskModel secondBranch = task("same-id", TaskType.TASK_TYPE_NOOP, true);
        try {
            scheduler.schedule(first,
                    List.of(firstBranch, task("caller-1", TaskType.TASK_TYPE_NOOP, false)), null);
            scheduler.schedule(first,
                    List.of(firstBranch, task("caller-2", TaskType.TASK_TYPE_NOOP, false)), null);
            assertEquals(1, queued.size(), "a queued task must also retain its execution slot");
            scheduler.schedule(second,
                    List.of(secondBranch, task("caller-3", TaskType.TASK_TYPE_NOOP, false)), null);
            assertEquals(2, queued.size(), "workflows must not share execution slots");

            // 驱动结束时，已排队但尚未返回的任务仍保留占用。
            scheduler.release(first);
            WorkflowModel reloaded = new WorkflowModel();
            reloaded.setWorkflowId(first.getWorkflowId());
            scheduler.schedule(reloaded,
                    List.of(firstBranch, task("caller-4", TaskType.TASK_TYPE_NOOP, false)), null);
            assertEquals(2, queued.size());
            queued.forEach(Runnable::run);
            scheduler.schedule(first, List.of(), null);
            scheduler.schedule(second, List.of(), null);
            assertEquals(2, executions.get());
        } finally {
            scheduler.release(first);
            scheduler.release(second);
        }
    }

    @Test
    void doesNotExecuteQueuedTaskAfterWorkflowTerminates() {
        List<Runnable> queued = new ArrayList<>();
        AtomicInteger branchExecutions = new AtomicInteger();
        WorkflowSystemTask action = new WorkflowSystemTask(TaskType.TASK_TYPE_NOOP) {
            @Override
            public void execute(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
                if (task.isForkBranchTask()) {
                    branchExecutions.incrementAndGet();
                }
                task.setStatus(TaskModel.Status.COMPLETED);
            }
        };
        CallerAndForkTaskScheduler scheduler = new CallerAndForkTaskScheduler(
                queued::add, new SystemTaskRegistry(Set.of(action)));
        WorkflowModel workflow = new WorkflowModel();
        scheduler.schedule(workflow, List.of(
                task("branch", TaskType.TASK_TYPE_NOOP, true),
                task("caller", TaskType.TASK_TYPE_NOOP, false)), null);
        workflow.setStatus(WorkflowModel.Status.TERMINATED);
        scheduler.release(workflow);

        queued.forEach(Runnable::run);

        assertEquals(0, branchExecutions.get());
    }

    @ParameterizedTest
    @ValueSource(strings = {TaskType.TASK_TYPE_JOIN, TaskType.TASK_TYPE_DO_WHILE})
    void evaluatesNestedControlTasksOnCallerThread(String type) {
        AtomicInteger evaluations = new AtomicInteger();
        String callerThread = Thread.currentThread().getName();
        WorkflowSystemTask control = new WorkflowSystemTask(type) {
            @Override
            public void execute(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
                assertEquals(callerThread, Thread.currentThread().getName());
                evaluations.incrementAndGet();
            }
        };
        CallerAndForkTaskScheduler scheduler = new CallerAndForkTaskScheduler(
                command -> { throw new AssertionError("control tasks must use the caller"); },
                new SystemTaskRegistry(Set.of(control)));
        WorkflowModel workflow = new WorkflowModel();
        TaskModel task = task("control", type, true);
        try {
            scheduler.schedule(workflow, List.of(task), null);
            scheduler.schedule(workflow, List.of(task), null);
            assertEquals(2, evaluations.get());
        } finally {
            scheduler.release(workflow);
        }
    }

    private static TaskModel task(String id, String type, boolean forkBranch) {
        TaskModel task = new TaskModel();
        task.setTaskId(id);
        task.setReferenceTaskName(id);
        task.setTaskType(type);
        task.setStatus(TaskModel.Status.IN_PROGRESS);
        task.setForkTaskId(forkBranch ? "fork" : null);
        return task;
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void startsInProgressTaskWhenExecutionBegins(boolean forkBranch) {
        WorkflowModel workflow = new WorkflowModel();
        WorkflowTask definition = WorkflowTask.builder()
                .type(TaskType.TASK_TYPE_HTTP)
                .taskReferenceName("http_ref")
                .inputParameters(Map.of())
                .build();
        TaskModel task = new HttpTaskMapper(new ParametersUtils(new ObjectMapper()))
                .getMappedTasks(TaskMapperContext.builder()
                        .workflowModel(workflow)
                        .workflowTask(definition)
                        .taskId("http-task")
                        .forkTaskId(forkBranch ? "fork-task" : null)
                        .build())
                .get(0);
        long scheduledTime = task.getScheduledTime();
        assertEquals(TaskModel.Status.IN_PROGRESS, task.getStatus());
        assertEquals(0, task.getStartTime());

        WorkflowSystemTask http = new WorkflowSystemTask(TaskType.TASK_TYPE_HTTP) {
            @Override
            public void execute(WorkflowModel model, TaskModel current, WorkflowExecutor executor) {
                assertEquals(TaskModel.Status.IN_PROGRESS, current.getStatus());
                assertTrue(current.getStartTime() >= scheduledTime);
                current.setStatus(TaskModel.Status.COMPLETED);
            }
        };
        CallerAndForkTaskScheduler scheduler = new CallerAndForkTaskScheduler(
                Runnable::run, new SystemTaskRegistry(Set.of(http)));

        scheduler.schedule(workflow, List.of(task), null);

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals(TaskModel.Status.IN_PROGRESS, task.getPreviousStatus());
        assertEquals(scheduledTime, task.getScheduledTime());
        assertTrue(task.getEndTime() >= task.getStartTime());
        assertEquals(task.getEndTime(), task.getUpdateTime());
    }

    @Test
    void preservesStartTimeWhenEvaluatingWaitingControlTaskAgain() {
        TaskModel task = new TaskModel();
        task.setTaskId("join-task");
        task.setTaskType(TaskType.TASK_TYPE_JOIN);
        task.setStatus(TaskModel.Status.IN_PROGRESS);
        task.setStartTime(42L);
        WorkflowSystemTask waitingJoin = new WorkflowSystemTask(TaskType.TASK_TYPE_JOIN) {};
        CallerAndForkTaskScheduler scheduler = new CallerAndForkTaskScheduler(
                Runnable::run, new SystemTaskRegistry(Set.of(waitingJoin)));
        WorkflowModel workflow = new WorkflowModel();

        scheduler.schedule(workflow, List.of(task), null);
        scheduler.schedule(workflow, List.of(task), null);

        assertEquals(TaskModel.Status.IN_PROGRESS, task.getStatus());
        assertEquals(42L, task.getStartTime());
        assertEquals(0, task.getEndTime());
        assertTrue(task.getUpdateTime() > 0);
    }
}
