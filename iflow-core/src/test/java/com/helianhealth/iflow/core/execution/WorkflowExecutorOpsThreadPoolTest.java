package com.helianhealth.iflow.core.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.iflow.common.domain.metadata.task.TaskType;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowDef;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowTask;
import com.helianhealth.iflow.core.dal.ExecutionDAOFacade;
import com.helianhealth.iflow.core.dao.ExecutionDAO;
import com.helianhealth.iflow.core.exception.TerminateWorkflowException;
import com.helianhealth.iflow.core.execution.evaluators.GraalJSEvaluator;
import com.helianhealth.iflow.core.execution.mapper.DoWhileTaskMapper;
import com.helianhealth.iflow.core.execution.mapper.ForkJoinTaskMapper;
import com.helianhealth.iflow.core.execution.mapper.JoinTaskMapper;
import com.helianhealth.iflow.core.execution.mapper.NoopTaskMapper;
import com.helianhealth.iflow.core.execution.mapper.TaskMapper;
import com.helianhealth.iflow.core.execution.mapper.TaskMapperContext;
import com.helianhealth.iflow.core.execution.tasks.DoWhile;
import com.helianhealth.iflow.core.execution.tasks.Fork;
import com.helianhealth.iflow.core.execution.tasks.Join;
import com.helianhealth.iflow.core.execution.tasks.SystemTaskRegistry;
import com.helianhealth.iflow.core.execution.tasks.WorkflowSystemTask;
import com.helianhealth.iflow.core.lock.InMemoryExecutionLockService;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import com.helianhealth.iflow.core.service.MetadataService;
import com.helianhealth.iflow.core.utils.IDGenerator;
import com.helianhealth.iflow.core.utils.ParametersUtils;
import com.helianhealth.iflow.core.utils.TaskUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowExecutorOpsThreadPoolTest {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void advancesFastBranchWithoutResubmittingSlowBranch(boolean loopBranch) throws Exception {
        CountDownLatch slowStarted = new CountDownLatch(1);
        CountDownLatch releaseSlow = new CountDownLatch(1);
        CountDownLatch successorStarted = new CountDownLatch(loopBranch ? 2 : 1);
        AtomicInteger slowExecutions = new AtomicInteger();
        WorkflowSystemTask noop = new WorkflowSystemTask(TaskType.TASK_TYPE_NOOP) {
            @Override
            public void execute(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
                try {
                    switch (TaskUtils.removeIterationFromTaskRefName(task.getReferenceTaskName())) {
                        case "fast" -> assertTrue(slowStarted.await(2, TimeUnit.SECONDS));
                        case "slow" -> {
                            slowExecutions.incrementAndGet();
                            slowStarted.countDown();
                            assertTrue(releaseSlow.await(5, TimeUnit.SECONDS));
                        }
                        case "successor" -> successorStarted.countDown();
                        default -> { }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                task.setStatus(TaskModel.Status.COMPLETED);
            }
        };
        ParametersUtils parameters = new ParametersUtils(new ObjectMapper());
        ExecutionDAOFacade dao = executionDAOFacade(new AtomicReference<>());
        SystemTaskRegistry registry = new SystemTaskRegistry(Set.of(
                noop, new Fork(), new Join(), new DoWhile(parameters, dao)));
        DeciderService decider = boundedDecider(parameters, registry, Map.of(
                TaskType.TASK_TYPE_NOOP, new NoopTaskMapper(),
                TaskType.TASK_TYPE_FORK_JOIN, new ForkJoinTaskMapper(),
                TaskType.TASK_TYPE_JOIN, new JoinTaskMapper(),
                TaskType.TASK_TYPE_DO_WHILE, new DoWhileTaskMapper(parameters)));
        List<WorkflowTask> fastBranch = List.of(
                task("fast", TaskType.TASK_TYPE_NOOP), task("successor", TaskType.TASK_TYPE_NOOP));
        if (loopBranch) {
            WorkflowTask loop = task("loop", TaskType.TASK_TYPE_DO_WHILE);
            loop.setLoopOver(fastBranch);
            loop.setLoopItems("${workflow.input.items}");
            fastBranch = List.of(loop);
        }
        WorkflowTask fork = task("fork", TaskType.TASK_TYPE_FORK_JOIN);
        fork.setForkTasks(List.of(fastBranch, List.of(task("slow", TaskType.TASK_TYPE_NOOP))));
        WorkflowTask join = task("join", TaskType.TASK_TYPE_JOIN);
        join.setJoinOn(List.of(loopBranch ? "loop" : "successor", "slow"));
        WorkflowDef definition = WorkflowDef.builder().route("uneven-fork")
                .tasks(List.of(fork, join, task("after", TaskType.TASK_TYPE_NOOP))).build();
        WorkflowExecutorOps executor = new WorkflowExecutorOps(decider, registry,
                dao, metadataService(definition),
                new IDGenerator(), parameters, new InMemoryExecutionLockService());
        ExecutorService branches = Executors.newFixedThreadPool(2);
        ExecutorService caller = Executors.newSingleThreadExecutor();
        executor.setTaskScheduler(new CallerAndForkTaskScheduler(branches, registry));
        try {
            var result = caller.submit(() -> executor.startWorkflow(
                    StartWorkflowInput.builder().route(definition.getRoute())
                            .workflowInput(Map.of("items", List.of("one", "two"))).build()));
            assertTrue(successorStarted.await(2, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> result.get(100, TimeUnit.MILLISECONDS),
                    "the caller must wait for the slow branch instead of spinning through decisions");
            releaseSlow.countDown();
            WorkflowModel workflow = result.get(2, TimeUnit.SECONDS);
            assertEquals(WorkflowModel.Status.COMPLETED, workflow.getStatus());
            assertEquals(TaskModel.Status.COMPLETED, workflow.getTaskByRefName("after").getStatus());
            assertEquals(1, slowExecutions.get());
            if (loopBranch) {
                assertEquals(2, workflow.getTaskByRefName("loop").getIteration());
            }
        } finally {
            releaseSlow.countDown();
            caller.shutdownNow();
            branches.shutdownNow();
        }
    }

    @Test
    void executesLinearTaskOnCallerThread() {
        AtomicReference<String> executionThread = new AtomicReference<>();
        WorkflowSystemTask noop =
                new WorkflowSystemTask(TaskType.TASK_TYPE_NOOP) {
                    @Override
                    public void execute(
                            WorkflowModel workflow,
                            TaskModel task,
                            WorkflowExecutor workflowExecutor) {
                        executionThread.set(Thread.currentThread().getName());
                        task.setStatus(TaskModel.Status.COMPLETED);
                    }
                };
        SystemTaskRegistry registry = new SystemTaskRegistry(Set.of(noop));
        Map<String, TaskMapper> mappers = Map.of(TaskType.TASK_TYPE_NOOP, new NoopTaskMapper());
        DeciderService decider =
                boundedDecider(new ParametersUtils(new ObjectMapper()), registry, mappers);

        WorkflowDef definition =
                WorkflowDef.builder()
                        .route("thread-pool-workflow")
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
                        new ParametersUtils(new ObjectMapper()),
                        new InMemoryExecutionLockService());
        ExecutorService taskPool =
                Executors.newFixedThreadPool(2, runnable -> new Thread(runnable, "test-task-pool"));
        executor.setTaskScheduler(new CallerAndForkTaskScheduler(taskPool, registry));

        try {
            String callerThread = Thread.currentThread().getName();
            WorkflowModel result =
                    executor.startWorkflow(
                            StartWorkflowInput.builder().route("thread-pool-workflow").build());

            assertEquals(WorkflowModel.Status.COMPLETED, result.getStatus());
            assertEquals(1, result.getTasks().size());
            assertEquals(TaskModel.Status.COMPLETED, result.getTasks().get(0).getStatus());
            assertTrue(result.getTasks().get(0).isExecuted());
            assertEquals(callerThread, executionThread.get());
            assertEquals(result.getTasks().get(0), updatedTask.get());
        } finally {
            taskPool.shutdownNow();
        }
    }

    @Test
    void executesForkBranchesConcurrentlyAndJoinsThem() throws Exception {
        CountDownLatch branchesStarted = new CountDownLatch(2);
        CountDownLatch releaseBranches = new CountDownLatch(1);
        Map<String, String> executionThreads = new ConcurrentHashMap<>();
        Set<String> mapperThreads = ConcurrentHashMap.newKeySet();
        WorkflowSystemTask noop =
                new WorkflowSystemTask(TaskType.TASK_TYPE_NOOP) {
                    @Override
                    public void execute(
                            WorkflowModel workflow,
                            TaskModel task,
                            WorkflowExecutor workflowExecutor) {
                        executionThreads.put(
                                task.getReferenceTaskName(), Thread.currentThread().getName());
                        if (Set.of("branch_a_1", "branch_b")
                                .contains(task.getReferenceTaskName())) {
                            branchesStarted.countDown();
                            try {
                                if (!releaseBranches.await(2, TimeUnit.SECONDS)) {
                                    throw new IllegalStateException(
                                            "branches did not execute concurrently");
                                }
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new IllegalStateException(e);
                            }
                        }
                        task.setStatus(TaskModel.Status.COMPLETED);
                    }
                };
        SystemTaskRegistry registry =
                new SystemTaskRegistry(Set.of(noop, new Fork(), new Join()));
        NoopTaskMapper noopMapper = new NoopTaskMapper();
        TaskMapper callerThreadNoopMapper =
                new TaskMapper() {
                    @Override
                    public String getTaskType() {
                        return TaskType.TASK_TYPE_NOOP;
                    }

                    @Override
                    public List<TaskModel> getMappedTasks(TaskMapperContext context) {
                        mapperThreads.add(Thread.currentThread().getName());
                        return noopMapper.getMappedTasks(context);
                    }
                };
        Map<String, TaskMapper> mappers =
                Map.of(
                        TaskType.TASK_TYPE_NOOP, callerThreadNoopMapper,
                        TaskType.TASK_TYPE_FORK_JOIN, new ForkJoinTaskMapper(),
                        TaskType.TASK_TYPE_JOIN, new JoinTaskMapper());
        DeciderService decider =
                boundedDecider(new ParametersUtils(new ObjectMapper()), registry, mappers);

        WorkflowTask branchA1 = task("branch_a_1", TaskType.TASK_TYPE_NOOP);
        WorkflowTask branchA2 = task("branch_a_2", TaskType.TASK_TYPE_NOOP);
        WorkflowTask branchB = task("branch_b", TaskType.TASK_TYPE_NOOP);
        WorkflowTask fork = task("fork", TaskType.TASK_TYPE_FORK_JOIN);
        fork.setForkTasks(List.of(List.of(branchA1, branchA2), List.of(branchB)));
        WorkflowTask join = task("join", TaskType.TASK_TYPE_JOIN);
        join.setJoinOn(List.of("branch_a_2", "branch_b"));
        WorkflowTask afterJoin = task("after_join", TaskType.TASK_TYPE_NOOP);
        WorkflowDef definition =
                WorkflowDef.builder()
                        .route("fork-workflow")
                        .tasks(List.of(fork, join, afterJoin))
                        .build();

        WorkflowExecutorOps executor =
                new WorkflowExecutorOps(
                        decider,
                        registry,
                        executionDAOFacade(new AtomicReference<>()),
                        metadataService(definition),
                        new IDGenerator(),
                        new ParametersUtils(new ObjectMapper()),
                        new InMemoryExecutionLockService());
        ExecutorService taskPool =
                Executors.newFixedThreadPool(
                        4, runnable -> new Thread(runnable, "test-fork-pool"));
        ExecutorService caller =
                Executors.newSingleThreadExecutor(
                        runnable -> new Thread(runnable, "test-workflow-caller"));
        executor.setTaskScheduler(new CallerAndForkTaskScheduler(taskPool, registry));

        try {
            var resultFuture =
                    caller.submit(
                            () ->
                                    executor.startWorkflow(
                                            StartWorkflowInput.builder()
                                                    .route("fork-workflow")
                                                    .build()));
            assertTrue(branchesStarted.await(2, TimeUnit.SECONDS));
            releaseBranches.countDown();

            WorkflowModel result = resultFuture.get(2, TimeUnit.SECONDS);
            assertEquals(WorkflowModel.Status.COMPLETED, result.getStatus());
            assertEquals(
                    TaskModel.Status.COMPLETED,
                    result.getTaskByRefName("branch_a_1").getStatus());
            assertEquals(
                    TaskModel.Status.COMPLETED,
                    result.getTaskByRefName("branch_a_2").getStatus());
            assertEquals(
                    TaskModel.Status.COMPLETED,
                    result.getTaskByRefName("branch_b").getStatus());
            assertEquals(TaskModel.Status.COMPLETED, result.getTaskByRefName("join").getStatus());
            assertEquals(
                    TaskModel.Status.COMPLETED,
                    result.getTaskByRefName("after_join").getStatus());

            TaskModel branchA1Runtime = result.getTaskByRefName("branch_a_1");
            TaskModel branchA2Runtime = result.getTaskByRefName("branch_a_2");
            TaskModel branchBRuntime = result.getTaskByRefName("branch_b");
            assertNotNull(branchA1Runtime.getForkTaskId());
            assertEquals(branchA1Runtime.getForkTaskId(), branchA2Runtime.getForkTaskId());
            assertEquals(branchA1Runtime.getForkTaskId(), branchBRuntime.getForkTaskId());
            assertNull(result.getTaskByRefName("join").getForkTaskId());
            assertNull(result.getTaskByRefName("after_join").getForkTaskId());
            assertEquals("test-fork-pool", executionThreads.get("branch_a_1"));
            assertEquals("test-fork-pool", executionThreads.get("branch_a_2"));
            assertEquals("test-fork-pool", executionThreads.get("branch_b"));
            assertEquals("test-workflow-caller", executionThreads.get("after_join"));
            assertEquals(Set.of("test-workflow-caller"), mapperThreads);
        } finally {
            releaseBranches.countDown();
            caller.shutdownNow();
            taskPool.shutdownNow();
        }
    }

    @Test
    void restoresOuterExecutionScopeAfterNestedJoin() throws Exception {
        Map<String, String> executionThreads = new ConcurrentHashMap<>();
        CountDownLatch innerBranchesStarted = new CountDownLatch(2);
        CountDownLatch releaseInnerBranches = new CountDownLatch(1);
        AtomicInteger nestedJoinEvaluations = new AtomicInteger();
        WorkflowSystemTask noop =
                new WorkflowSystemTask(TaskType.TASK_TYPE_NOOP) {
                    @Override
                    public void execute(
                            WorkflowModel workflow,
                            TaskModel task,
                            WorkflowExecutor workflowExecutor) {
                        executionThreads.put(
                                task.getReferenceTaskName(), Thread.currentThread().getName());
                        if (Set.of("inner_a", "inner_b")
                                .contains(task.getReferenceTaskName())) {
                            innerBranchesStarted.countDown();
                            try {
                                if (!releaseInnerBranches.await(2, TimeUnit.SECONDS)) {
                                    throw new IllegalStateException("inner branches were not released");
                                }
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new IllegalStateException(e);
                            }
                        }
                        task.setStatus(TaskModel.Status.COMPLETED);
                    }
                };
        Join joinDelegate = new Join();
        WorkflowSystemTask join =
                new WorkflowSystemTask(TaskType.TASK_TYPE_JOIN) {
                    @Override
                    public void execute(
                            WorkflowModel workflow,
                            TaskModel task,
                            WorkflowExecutor workflowExecutor) {
                        if ("nested_join".equals(task.getReferenceTaskName())) {
                            nestedJoinEvaluations.incrementAndGet();
                        }
                        joinDelegate.execute(workflow, task, workflowExecutor);
                    }
                };
        SystemTaskRegistry registry =
                new SystemTaskRegistry(Set.of(noop, new Fork(), join));
        Map<String, TaskMapper> mappers =
                Map.of(
                        TaskType.TASK_TYPE_NOOP, new NoopTaskMapper(),
                        TaskType.TASK_TYPE_FORK_JOIN, new ForkJoinTaskMapper(),
                        TaskType.TASK_TYPE_JOIN, new JoinTaskMapper());
        DeciderService decider =
                boundedDecider(new ParametersUtils(new ObjectMapper()), registry, mappers);

        WorkflowTask innerA = task("inner_a", TaskType.TASK_TYPE_NOOP);
        WorkflowTask innerB = task("inner_b", TaskType.TASK_TYPE_NOOP);
        WorkflowTask nestedFork = task("nested_fork", TaskType.TASK_TYPE_FORK_JOIN);
        nestedFork.setForkTasks(List.of(List.of(innerA), List.of(innerB)));
        WorkflowTask nestedJoin = task("nested_join", TaskType.TASK_TYPE_JOIN);
        nestedJoin.setJoinOn(List.of("inner_a", "inner_b"));
        WorkflowTask afterNested = task("after_nested", TaskType.TASK_TYPE_NOOP);
        WorkflowTask outerB = task("outer_b", TaskType.TASK_TYPE_NOOP);

        WorkflowTask outerFork = task("outer_fork", TaskType.TASK_TYPE_FORK_JOIN);
        outerFork.setForkTasks(
                List.of(List.of(nestedFork, nestedJoin, afterNested), List.of(outerB)));
        WorkflowTask outerJoin = task("outer_join", TaskType.TASK_TYPE_JOIN);
        outerJoin.setJoinOn(List.of("after_nested", "outer_b"));
        WorkflowTask afterOuter = task("after_outer", TaskType.TASK_TYPE_NOOP);
        WorkflowDef definition =
                WorkflowDef.builder()
                        .route("nested-fork-workflow")
                        .tasks(List.of(outerFork, outerJoin, afterOuter))
                        .build();

        WorkflowExecutorOps executor =
                new WorkflowExecutorOps(
                        decider,
                        registry,
                        executionDAOFacade(new AtomicReference<>()),
                        metadataService(definition),
                        new IDGenerator(),
                        new ParametersUtils(new ObjectMapper()),
                        new InMemoryExecutionLockService());
        ExecutorService taskPool =
                Executors.newFixedThreadPool(
                        6, runnable -> new Thread(runnable, "test-nested-fork-pool"));
        ExecutorService caller =
                Executors.newSingleThreadExecutor(
                        runnable -> new Thread(runnable, "test-nested-caller"));
        executor.setTaskScheduler(new CallerAndForkTaskScheduler(taskPool, registry));

        try {
            var resultFuture =
                    caller.submit(
                            () ->
                                    executor.startWorkflow(
                                            StartWorkflowInput.builder()
                                                    .route("nested-fork-workflow")
                                                    .build()));
            assertTrue(innerBranchesStarted.await(2, TimeUnit.SECONDS));
            Thread.sleep(150);
            assertTrue(
                    nestedJoinEvaluations.get() < 10,
                    "waiting nested JOIN should not be continuously re-scheduled");
            releaseInnerBranches.countDown();
            WorkflowModel result = resultFuture.get(3, TimeUnit.SECONDS);

            assertEquals(WorkflowModel.Status.COMPLETED, result.getStatus());
            TaskModel outerForkRuntime = result.getTaskByRefName("outer_fork");
            TaskModel nestedForkRuntime = result.getTaskByRefName("nested_fork");
            assertNull(outerForkRuntime.getForkTaskId());
            assertEquals(outerForkRuntime.getTaskId(), nestedForkRuntime.getForkTaskId());
            assertEquals(
                    nestedForkRuntime.getTaskId(),
                    result.getTaskByRefName("inner_a").getForkTaskId());
            assertEquals(
                    nestedForkRuntime.getTaskId(),
                    result.getTaskByRefName("inner_b").getForkTaskId());
            assertEquals(
                    outerForkRuntime.getTaskId(),
                    result.getTaskByRefName("nested_join").getForkTaskId());
            assertEquals(
                    outerForkRuntime.getTaskId(),
                    result.getTaskByRefName("after_nested").getForkTaskId());
            assertNull(result.getTaskByRefName("outer_join").getForkTaskId());
            assertNull(result.getTaskByRefName("after_outer").getForkTaskId());
            assertEquals("test-nested-fork-pool", executionThreads.get("inner_a"));
            assertEquals("test-nested-fork-pool", executionThreads.get("inner_b"));
            assertEquals("test-nested-fork-pool", executionThreads.get("after_nested"));
            assertEquals("test-nested-caller", executionThreads.get("after_outer"));
        } finally {
            releaseInnerBranches.countDown();
            caller.shutdownNow();
            taskPool.shutdownNow();
        }
    }

    @Test
    void failsWorkflowWhenForkBranchExecutorRejectsTask() {
        WorkflowSystemTask noop =
                new WorkflowSystemTask(TaskType.TASK_TYPE_NOOP) {
                    @Override
                    public void execute(
                            WorkflowModel workflow,
                            TaskModel task,
                            WorkflowExecutor workflowExecutor) {
                        task.setStatus(TaskModel.Status.COMPLETED);
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
                boundedDecider(new ParametersUtils(new ObjectMapper()), registry, mappers);

        WorkflowTask branch = task("branch", TaskType.TASK_TYPE_NOOP);
        WorkflowTask fork = task("fork", TaskType.TASK_TYPE_FORK_JOIN);
        fork.setForkTasks(List.of(List.of(branch)));
        WorkflowTask join = task("join", TaskType.TASK_TYPE_JOIN);
        join.setJoinOn(List.of("branch"));
        WorkflowDef definition =
                WorkflowDef.builder()
                        .route("rejected-fork-workflow")
                        .tasks(List.of(fork, join))
                        .build();

        AtomicReference<WorkflowModel> persistedWorkflow = new AtomicReference<>();
        WorkflowExecutorOps executor =
                new WorkflowExecutorOps(
                        decider,
                        registry,
                        executionDAOFacade(new AtomicReference<>(), persistedWorkflow),
                        metadataService(definition),
                        new IDGenerator(),
                        new ParametersUtils(new ObjectMapper()),
                        new InMemoryExecutionLockService());
        executor.setTaskScheduler(
                new CallerAndForkTaskScheduler(
                        command -> {
                            throw new RejectedExecutionException("full");
                        },
                        registry));

        TerminateWorkflowException failure = assertThrows(
                TerminateWorkflowException.class,
                () -> executor.startWorkflow(
                        StartWorkflowInput.builder().route("rejected-fork-workflow").build()));
        WorkflowModel result = persistedWorkflow.get();

        assertEquals(WorkflowModel.Status.FAILED, failure.getWorkflowStatus());
        assertNotNull(result);
        assertEquals(WorkflowModel.Status.FAILED, result.getStatus());
        assertEquals(
                TaskModel.Status.FAILED_WITH_TERMINAL_ERROR,
                result.getTaskByRefName("branch").getStatus());
        assertTrue(
                result.getTaskByRefName("branch")
                        .getReasonForInCompletion()
                        .contains("线程池已满"));
    }

    @Test
    void schedulesDoWhileIterationOnWorkflowCallerThread() {
        AtomicReference<String> loopThread = new AtomicReference<>();
        WorkflowSystemTask noop =
                new WorkflowSystemTask(TaskType.TASK_TYPE_NOOP) {
                    @Override
                    public void execute(
                            WorkflowModel workflow,
                            TaskModel task,
                            WorkflowExecutor workflowExecutor) {
                        loopThread.set(Thread.currentThread().getName());
                        task.setStatus(TaskModel.Status.COMPLETED);
                    }
                };
        ParametersUtils parametersUtils = new ParametersUtils(new ObjectMapper());
        DoWhile doWhile =
                new DoWhile(parametersUtils, executionDAOFacade(new AtomicReference<>()));
        SystemTaskRegistry registry = new SystemTaskRegistry(Set.of(noop, doWhile));
        Map<String, TaskMapper> mappers =
                Map.of(
                        TaskType.TASK_TYPE_NOOP, new NoopTaskMapper(),
                        TaskType.TASK_TYPE_DO_WHILE, new DoWhileTaskMapper(parametersUtils));
        DeciderService decider =
                boundedDecider(parametersUtils, registry, mappers);

        WorkflowTask loopBody = task("loop_body", TaskType.TASK_TYPE_NOOP);
        WorkflowTask loop = task("loop", TaskType.TASK_TYPE_DO_WHILE);
        loop.setLoopOver(List.of(loopBody));
        loop.setLoopCondition("false");
        loop.setEvaluatorType(GraalJSEvaluator.NAME);
        loop.setInputParameters(Map.of("continue", false));
        WorkflowDef definition =
                WorkflowDef.builder()
                        .route("do-while-workflow")
                        .tasks(List.of(loop))
                        .build();

        WorkflowExecutorOps executor =
                new WorkflowExecutorOps(
                        decider,
                        registry,
                        executionDAOFacade(new AtomicReference<>()),
                        metadataService(definition),
                        new IDGenerator(),
                        parametersUtils,
                        new InMemoryExecutionLockService());
        String callerThread = Thread.currentThread().getName();

        WorkflowModel result =
                executor.startWorkflow(
                        StartWorkflowInput.builder().route("do-while-workflow").build());

        assertEquals(WorkflowModel.Status.COMPLETED, result.getStatus());
        assertEquals(TaskModel.Status.COMPLETED, result.getTaskByRefName("loop").getStatus());
        assertEquals(
                TaskModel.Status.COMPLETED,
                result.getTaskByRefName("loop_body__1").getStatus());
        assertEquals(callerThread, loopThread.get());
    }

    private DeciderService boundedDecider(
            ParametersUtils parameters, SystemTaskRegistry registry, Map<String, TaskMapper> mappers) {
        return new DeciderService(new IDGenerator(), parameters, registry, mappers) {
            private int rounds;

            @Override
            public DeciderOutcome decide(WorkflowModel workflow) {
                assertTrue(++rounds <= 100, "workflow did not finish within 100 decision rounds");
                return super.decide(workflow);
            }
        };
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

    private ExecutionDAOFacade executionDAOFacade(AtomicReference<TaskModel> updatedTask) {
        return executionDAOFacade(updatedTask, new AtomicReference<>());
    }

    private ExecutionDAOFacade executionDAOFacade(
            AtomicReference<TaskModel> updatedTask, AtomicReference<WorkflowModel> updatedWorkflow) {
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
                                        case "updateWorkflow" -> {
                                            WorkflowModel workflow = (WorkflowModel) args[0];
                                            updatedWorkflow.set(workflow);
                                            yield workflow.getWorkflowId();
                                        }
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
