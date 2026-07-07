package com.helianhealth.agent.core.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.agent.common.domain.metadata.task.TaskType;
import com.helianhealth.agent.common.domain.metadata.workflow.WorkflowDef;
import com.helianhealth.agent.common.domain.metadata.workflow.WorkflowTask;
import com.helianhealth.agent.core.dal.ExecutionDAOFacade;
import com.helianhealth.agent.core.dao.ExecutionDAO;
import com.helianhealth.agent.core.execution.mapper.ForkJoinTaskMapper;
import com.helianhealth.agent.core.execution.mapper.JoinTaskMapper;
import com.helianhealth.agent.core.execution.mapper.NoopTaskMapper;
import com.helianhealth.agent.core.execution.mapper.TaskMapper;
import com.helianhealth.agent.core.execution.tasks.Fork;
import com.helianhealth.agent.core.execution.tasks.Join;
import com.helianhealth.agent.core.execution.tasks.SystemTaskRegistry;
import com.helianhealth.agent.core.execution.tasks.WorkflowSystemTask;
import com.helianhealth.agent.core.lock.InMemoryExecutionLockService;
import com.helianhealth.agent.core.model.TaskModel;
import com.helianhealth.agent.core.model.WorkflowModel;
import com.helianhealth.agent.core.service.MetadataService;
import com.helianhealth.agent.core.utils.IDGenerator;
import com.helianhealth.agent.core.utils.ParametersUtils;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowExecutorOpsThreadPoolTest {

    @Test
    void executesTaskInPoolAndWaitsForCompletionCallback() {
        AtomicReference<String> executionThread = new AtomicReference<>();
        WorkflowSystemTask noop =
                new WorkflowSystemTask(TaskType.TASK_TYPE_NOOP) {
                    @Override
                    public boolean execute(
                            WorkflowModel workflow,
                            TaskModel task,
                            WorkflowExecutor workflowExecutor) {
                        executionThread.set(Thread.currentThread().getName());
                        task.setStatus(TaskModel.Status.COMPLETED);
                        return true;
                    }
                };
        SystemTaskRegistry registry = new SystemTaskRegistry(Set.of(noop));
        Map<String, TaskMapper> mappers = Map.of(TaskType.TASK_TYPE_NOOP, new NoopTaskMapper());
        DeciderService decider =
                new DeciderService(new IDGenerator(), new ParametersUtils(), registry, mappers);

        WorkflowDef definition =
                WorkflowDef.builder()
                        .name("thread-pool-workflow")
                        .tasks(
                                java.util.List.of(
                                        WorkflowTask.builder()
                                                .name("noop")
                                                .taskReferenceName("noop_ref")
                                                .type(TaskType.TASK_TYPE_NOOP)
                                                .build()))
                        .build();
        MetadataService metadataService = metadataService(definition);
        AtomicReference<TaskModel> updatedTask = new AtomicReference<>();
        ExecutionDAOFacade executionDAOFacade = executionDAOFacade(updatedTask);

        WorkflowExecutorOps executor =
                new WorkflowExecutorOps(
                        decider,
                        registry,
                        executionDAOFacade,
                        metadataService,
                        new IDGenerator(),
                        new ParametersUtils(),
                        new InMemoryExecutionLockService());
        ExecutorService taskPool =
                Executors.newFixedThreadPool(2, runnable -> new Thread(runnable, "test-task-pool"));
        executor.setTaskExecutor(taskPool);

        try {
            WorkflowModel result =
                    executor.startWorkflow(
                            StartWorkflowInput.builder().name("thread-pool-workflow").build());

            assertEquals(WorkflowModel.Status.COMPLETED, result.getStatus());
            assertEquals(1, result.getTasks().size());
            assertEquals(TaskModel.Status.COMPLETED, result.getTasks().get(0).getStatus());
            assertTrue(result.getTasks().get(0).isExecuted());
            assertEquals("test-task-pool", executionThread.get());
            assertEquals(result.getTasks().get(0), updatedTask.get());
        } finally {
            taskPool.shutdownNow();
        }
    }

    @Test
    void executesForkBranchesConcurrentlyAndJoinsThem() throws Exception {
        CountDownLatch branchesStarted = new CountDownLatch(2);
        CountDownLatch releaseBranches = new CountDownLatch(1);
        WorkflowSystemTask noop =
                new WorkflowSystemTask(TaskType.TASK_TYPE_NOOP) {
                    @Override
                    public boolean execute(
                            WorkflowModel workflow,
                            TaskModel task,
                            WorkflowExecutor workflowExecutor) {
                        branchesStarted.countDown();
                        try {
                            if (!releaseBranches.await(2, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("branches did not execute concurrently");
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(e);
                        }
                        task.setStatus(TaskModel.Status.COMPLETED);
                        return true;
                    }
                };
        SystemTaskRegistry registry =
                new SystemTaskRegistry(Set.of(noop, new Fork(), new Join()));
        Map<String, TaskMapper> mappers =
                Map.of(
                        TaskType.TASK_TYPE_NOOP, new NoopTaskMapper(),
                        TaskType.TASK_TYPE_FORK_JOIN, new ForkJoinTaskMapper(),
                        TaskType.TASK_TYPE_JOIN, new JoinTaskMapper());
        DeciderService decider =
                new DeciderService(new IDGenerator(), new ParametersUtils(), registry, mappers);

        WorkflowTask branchA = task("branch_a", TaskType.TASK_TYPE_NOOP);
        WorkflowTask branchB = task("branch_b", TaskType.TASK_TYPE_NOOP);
        WorkflowTask fork = task("fork", TaskType.TASK_TYPE_FORK_JOIN);
        fork.setForkTasks(List.of(List.of(branchA), List.of(branchB)));
        WorkflowTask join = task("join", TaskType.TASK_TYPE_JOIN);
        join.setJoinOn(List.of("branch_a", "branch_b"));
        WorkflowDef definition =
                WorkflowDef.builder()
                        .name("fork-workflow")
                        .tasks(List.of(fork, join))
                        .build();

        WorkflowExecutorOps executor =
                new WorkflowExecutorOps(
                        decider,
                        registry,
                        executionDAOFacade(new AtomicReference<>()),
                        metadataService(definition),
                        new IDGenerator(),
                        new ParametersUtils(),
                        new InMemoryExecutionLockService());
        ExecutorService taskPool = Executors.newFixedThreadPool(4);
        ExecutorService caller = Executors.newSingleThreadExecutor();
        executor.setTaskExecutor(taskPool);

        try {
            var resultFuture =
                    caller.submit(
                            () ->
                                    executor.startWorkflow(
                                            StartWorkflowInput.builder()
                                                    .name("fork-workflow")
                                                    .build()));
            assertTrue(branchesStarted.await(2, TimeUnit.SECONDS));
            releaseBranches.countDown();

            WorkflowModel result = resultFuture.get(2, TimeUnit.SECONDS);
            assertEquals(WorkflowModel.Status.COMPLETED, result.getStatus());
            assertEquals(
                    TaskModel.Status.COMPLETED,
                    result.getTaskByRefName("branch_a").getStatus());
            assertEquals(
                    TaskModel.Status.COMPLETED,
                    result.getTaskByRefName("branch_b").getStatus());
            assertEquals(TaskModel.Status.COMPLETED, result.getTaskByRefName("join").getStatus());
        } finally {
            releaseBranches.countDown();
            caller.shutdownNow();
            taskPool.shutdownNow();
        }
    }

    private WorkflowTask task(String referenceName, String type) {
        return WorkflowTask.builder()
                .name(referenceName)
                .taskReferenceName(referenceName)
                .type(type)
                .build();
    }

    private MetadataService metadataService(WorkflowDef definition) {
        return (MetadataService)
                Proxy.newProxyInstance(
                        MetadataService.class.getClassLoader(),
                        new Class<?>[] {MetadataService.class},
                        (proxy, method, args) -> {
                            if ("getWorkflowDef".equals(method.getName())) {
                                return definition;
                            }
                            return defaultValue(method.getReturnType());
                        });
    }

    @SuppressWarnings("unchecked")
    private ExecutionDAOFacade executionDAOFacade(AtomicReference<TaskModel> updatedTask) {
        ExecutionDAO executionDAO =
                (ExecutionDAO)
                        Proxy.newProxyInstance(
                                ExecutionDAO.class.getClassLoader(),
                                new Class<?>[] {ExecutionDAO.class},
                                (proxy, method, args) -> {
                                    return switch (method.getName()) {
                                        case "createTasks" -> args[0];
                                        case "updateTask" -> {
                                            updatedTask.set((TaskModel) args[0]);
                                            yield null;
                                        }
                                        case "updateWorkflow" ->
                                                ((WorkflowModel) args[0]).getWorkflowId();
                                        default -> defaultValue(method.getReturnType());
                                    };
                                });
        return new ExecutionDAOFacade(executionDAO, new ObjectMapper());
    }

    private Object defaultValue(Class<?> returnType) {
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == long.class || returnType == int.class) {
            return 0;
        }
        if (returnType == Optional.class) {
            return Optional.empty();
        }
        return null;
    }
}
