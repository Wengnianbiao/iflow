package com.helianhealth.iflow.core.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.iflow.common.domain.metadata.task.TaskType;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowDef;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowTask;
import com.helianhealth.iflow.core.dal.ExecutionDAOFacade;
import com.helianhealth.iflow.core.dao.ExecutionDAO;
import com.helianhealth.iflow.core.execution.mapper.NoopTaskMapper;
import com.helianhealth.iflow.core.execution.tasks.SystemTaskRegistry;
import com.helianhealth.iflow.core.execution.tasks.WorkflowSystemTask;
import com.helianhealth.iflow.core.lock.InMemoryExecutionLockService;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import com.helianhealth.iflow.core.service.MetadataService;
import com.helianhealth.iflow.core.utils.IDGenerator;
import com.helianhealth.iflow.core.utils.ParametersUtils;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowSweeperTest {

    @Test
    void leavesWorkflowRunningBeforeDeadline() {
        WorkflowSweeper sweeper = sweeper(3600);
        WorkflowModel workflow = runningWorkflow(1);
        workflow.setStartTime(System.currentTimeMillis());
        TaskModel task = inProgressTask("sync_lis_ref");
        workflow.setTasks(List.of(task));

        assertFalse(sweeper.sweep(workflow, null));
        assertEquals(WorkflowModel.Status.RUNNING, workflow.getStatus());
        assertEquals(TaskModel.Status.IN_PROGRESS, task.getStatus());
    }

    @Test
    void terminatesTimedOutWorkflowAndOnlyOpenTasks() {
        WorkflowSweeper sweeper = sweeper(3600);
        WorkflowModel workflow = runningWorkflow(1);
        workflow.setStartTime(System.currentTimeMillis() - 5_000);
        TaskModel completed = task("done_ref", TaskModel.Status.COMPLETED);
        TaskModel waiting = inProgressTask("sync_lis_ref");
        TaskModel scheduled = task("next_ref", TaskModel.Status.SCHEDULED);
        workflow.setTasks(List.of(completed, waiting, scheduled));

        List<TaskModel> updated = new ArrayList<>();
        AtomicReference<WorkflowModel> persisted = new AtomicReference<>();
        sweeper = sweeper(3600, recordingDao(workflow, updated, persisted), null);

        assertTrue(sweeper.sweep(workflow, null));

        assertEquals(WorkflowModel.Status.TIMED_OUT, workflow.getStatus());
        assertEquals(TaskModel.Status.COMPLETED, completed.getStatus());
        assertEquals(TaskModel.Status.TIMED_OUT, waiting.getStatus());
        assertEquals(TaskModel.Status.CANCELED, scheduled.getStatus());
        assertEquals(waiting.getTaskId(), workflow.getFailedTaskId());
        assertTrue(waiting.isExecuted());
        assertTrue(scheduled.isExecuted());
        assertTrue(workflow.getReasonForInCompletion().contains("未完成任务：sync_lis_ref(IN_PROGRESS)"));
        assertTrue(workflow.getReasonForInCompletion().contains("next_ref(SCHEDULED)"));
        assertEquals(List.of(waiting, scheduled), updated);
        assertEquals(WorkflowModel.Status.TIMED_OUT, persisted.get().getStatus());
    }

    @Test
    void mentionsWaitingJoinInTimeoutReason() {
        WorkflowSweeper sweeper = sweeper(60);
        WorkflowModel workflow = runningWorkflow(0);
        workflow.setStartTime(System.currentTimeMillis() - 120_000);
        TaskModel join = inProgressTask("apply_join_ref");
        join.setTaskType(TaskType.TASK_TYPE_JOIN);
        TaskModel branch = inProgressTask("sync_lis_ref");
        workflow.setTasks(List.of(join, branch));

        assertTrue(sweeper.sweep(workflow, null));
        assertTrue(workflow.getReasonForInCompletion().startsWith("工作流执行超过 "));
        assertTrue(
                workflow.getReasonForInCompletion()
                        .contains("等待 JOIN apply_join_ref；未完成任务：sync_lis_ref(IN_PROGRESS)"));
        assertEquals(TaskModel.Status.TIMED_OUT, join.getStatus());
        assertEquals(TaskModel.Status.TIMED_OUT, branch.getStatus());
    }

    @Test
    void disabledWhenWorkflowAndDefaultTimeoutAreUnset() {
        WorkflowSweeper sweeper = sweeper(0);
        WorkflowModel workflow = runningWorkflow(0);
        workflow.setStartTime(1);
        workflow.setTasks(List.of(inProgressTask("sync_lis_ref")));

        assertFalse(sweeper.sweep(workflow, null));
        assertEquals(WorkflowModel.Status.RUNNING, workflow.getStatus());
    }

    @Test
    void sweepStoredSkipsWorkflowHeldByExecutionLock() throws Exception {
        InMemoryExecutionLockService lock = new InMemoryExecutionLockService(0);
        WorkflowModel workflow = runningWorkflow(1);
        workflow.setWorkflowId("wf-locked");
        workflow.setStartTime(System.currentTimeMillis() - 10_000);
        workflow.setTasks(List.of(inProgressTask("sync_lis_ref")));
        WorkflowSweeper sweeper =
                sweeper(3600, recordingDao(workflow, new ArrayList<>(), new AtomicReference<>()), lock);

        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread holder =
                new Thread(
                        () -> {
                            assertTrue(lock.acquireLock(workflow.getWorkflowId()));
                            held.countDown();
                            try {
                                assertTrue(release.await(5, TimeUnit.SECONDS));
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            } finally {
                                lock.releaseLock(workflow.getWorkflowId());
                            }
                        });
        holder.start();
        assertTrue(held.await(5, TimeUnit.SECONDS));

        assertFalse(sweeper.sweepStored(workflow.getWorkflowId(), null));
        assertEquals(WorkflowModel.Status.RUNNING, workflow.getStatus());

        release.countDown();
        holder.join(5_000);
        assertTrue(sweeper.sweepStored(workflow.getWorkflowId(), null));
        assertEquals(WorkflowModel.Status.TIMED_OUT, workflow.getStatus());
    }

    @Test
    void driveLoopStopsAtTimeoutWithoutSchedulingAgain() {
        AtomicInteger executions = new AtomicInteger();
        WorkflowSystemTask blocking =
                new WorkflowSystemTask(TaskType.TASK_TYPE_NOOP) {
                    @Override
                    public void execute(
                            WorkflowModel workflow,
                            TaskModel task,
                            WorkflowExecutor workflowExecutor) {
                        int attempt = executions.incrementAndGet();
                        workflow.setStartTime(System.currentTimeMillis() - 10_000);
                        if (attempt > 1) {
                            task.setStatus(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR);
                            task.setReasonForInCompletion("sweep did not stop the workflow");
                        }
                    }
                };
        SystemTaskRegistry registry = new SystemTaskRegistry(Set.of(blocking));
        ParametersUtils parametersUtils = new ParametersUtils();
        DeciderService deciderService =
                new DeciderService(
                        new IDGenerator(),
                        parametersUtils,
                        registry,
                        Map.of(TaskType.TASK_TYPE_NOOP, new NoopTaskMapper()));
        WorkflowTask blockingTask =
                WorkflowTask.builder()
                        .name("blocking")
                        .taskReferenceName("blocking_ref")
                        .type(TaskType.TASK_TYPE_NOOP)
                        .build();
        WorkflowTask nextTask =
                WorkflowTask.builder()
                        .name("next")
                        .taskReferenceName("next_ref")
                        .type(TaskType.TASK_TYPE_NOOP)
                        .build();
        WorkflowDef workflowDefinition =
                WorkflowDef.builder()
                        .route("sweep-timeout")
                        .version(1)
                        .timeoutSeconds(1)
                        .tasks(List.of(blockingTask, nextTask))
                        .build();
        ExecutionDAO executionDAO =
                (ExecutionDAO)
                        Proxy.newProxyInstance(
                                ExecutionDAO.class.getClassLoader(),
                                new Class<?>[] {ExecutionDAO.class},
                                (proxy, method, args) ->
                                        switch (method.getName()) {
                                            case "createTasks" -> args[0];
                                            case "getRunningWorkflowIds" -> List.of();
                                            default -> defaultValue(method.getReturnType());
                                        });
        MetadataService metadataService =
                (MetadataService)
                        Proxy.newProxyInstance(
                                MetadataService.class.getClassLoader(),
                                new Class<?>[] {MetadataService.class},
                                (proxy, method, args) -> {
                                    if ("getWorkflowDef".equals(method.getName())) {
                                        return workflowDefinition;
                                    }
                                    return defaultValue(method.getReturnType());
                                });
        WorkflowExecutorOps executor =
                new WorkflowExecutorOps(
                        deciderService,
                        registry,
                        new ExecutionDAOFacade(executionDAO, new ObjectMapper()),
                        metadataService,
                        new IDGenerator(),
                        parametersUtils,
                        new InMemoryExecutionLockService());

        WorkflowModel result =
                executor.startWorkflow(
                        StartWorkflowInput.builder().route(workflowDefinition.getRoute()).build());

        assertEquals(1, executions.get());
        assertEquals(WorkflowModel.Status.TIMED_OUT, result.getStatus());
        assertEquals(1, result.getTasks().size());
        assertEquals(TaskModel.Status.TIMED_OUT, result.getTasks().get(0).getStatus());
        assertNull(result.getTaskByRefName("next_ref"));
        assertTrue(result.getReasonForInCompletion().contains("blocking_ref(IN_PROGRESS)"));
    }

    private static WorkflowSweeper sweeper(long defaultTimeoutSeconds) {
        return sweeper(defaultTimeoutSeconds, null, null);
    }

    private static WorkflowSweeper sweeper(
            long defaultTimeoutSeconds, ExecutionDAO executionDAO, InMemoryExecutionLockService lock) {
        ExecutionDAOFacade facade =
                executionDAO == null
                        ? null
                        : new ExecutionDAOFacade(executionDAO, new ObjectMapper());
        return new WorkflowSweeper(new SystemTaskRegistry(Set.of()), facade, lock, defaultTimeoutSeconds);
    }

    private static ExecutionDAO recordingDao(
            WorkflowModel workflow,
            List<TaskModel> updatedTasks,
            AtomicReference<WorkflowModel> persistedWorkflow) {
        return (ExecutionDAO)
                Proxy.newProxyInstance(
                        ExecutionDAO.class.getClassLoader(),
                        new Class<?>[] {ExecutionDAO.class},
                        (proxy, method, args) ->
                                switch (method.getName()) {
                                    case "updateTask" -> {
                                        updatedTasks.add((TaskModel) args[0]);
                                        yield null;
                                    }
                                    case "updateWorkflow" -> {
                                        persistedWorkflow.set((WorkflowModel) args[0]);
                                        yield workflow.getWorkflowId();
                                    }
                                    case "getWorkflow" -> Optional.of(workflow);
                                    default -> defaultValue(method.getReturnType());
                                });
    }

    private static WorkflowModel runningWorkflow(long timeoutSeconds) {
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("wf-1");
        workflow.setStatus(WorkflowModel.Status.RUNNING);
        workflow.setWorkflowDefinition(
                WorkflowDef.builder().route("demo").version(1).timeoutSeconds(timeoutSeconds).build());
        return workflow;
    }

    private static TaskModel inProgressTask(String referenceName) {
        return task(referenceName, TaskModel.Status.IN_PROGRESS);
    }

    private static TaskModel task(String referenceName, TaskModel.Status status) {
        TaskModel task = new TaskModel();
        task.setTaskId(referenceName + "-id");
        task.setReferenceTaskName(referenceName);
        task.setTaskType(TaskType.TASK_TYPE_HTTP);
        task.setStatus(status);
        return task;
    }

    private static Object defaultValue(Class<?> returnType) {
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == long.class || returnType == int.class) {
            return 0;
        }
        if (returnType == Optional.class) {
            return Optional.empty();
        }
        if (List.class.isAssignableFrom(returnType)) {
            return List.of();
        }
        return null;
    }
}
