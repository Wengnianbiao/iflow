package com.helianhealth.iflow.core.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.iflow.common.domain.metadata.task.TaskType;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowDef;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowTask;
import com.helianhealth.iflow.core.dal.ExecutionDAOFacade;
import com.helianhealth.iflow.core.queue.InMemoryQueueDAO;
import com.helianhealth.iflow.core.dao.ExecutionDAO;
import com.helianhealth.iflow.core.exception.TransientException;
import com.helianhealth.iflow.core.exception.TerminateWorkflowException;
import com.helianhealth.iflow.core.execution.mapper.ForkJoinTaskMapper;
import com.helianhealth.iflow.core.execution.mapper.DoWhileTaskMapper;
import com.helianhealth.iflow.core.execution.mapper.HttpTaskMapper;
import com.helianhealth.iflow.core.execution.mapper.JdbcTaskMapper;
import com.helianhealth.iflow.core.execution.mapper.JoinTaskMapper;
import com.helianhealth.iflow.core.execution.mapper.ParamMappingTaskMapper;
import com.helianhealth.iflow.core.execution.mapper.TaskMapper;
import com.helianhealth.iflow.core.execution.mapper.TaskMapperContext;
import com.helianhealth.iflow.core.execution.mapper.WebServiceTaskMapper;
import com.helianhealth.iflow.core.execution.tasks.Fork;
import com.helianhealth.iflow.core.execution.tasks.DoWhile;
import com.helianhealth.iflow.core.execution.tasks.Join;
import com.helianhealth.iflow.core.execution.tasks.SystemTaskRegistry;
import com.helianhealth.iflow.core.execution.tasks.WorkflowSystemTask;
import com.helianhealth.iflow.core.lock.ExecutionLockService;
import com.helianhealth.iflow.core.listener.WorkflowStatusListener;
import com.helianhealth.iflow.core.lock.InMemoryLock;
import com.helianhealth.iflow.core.lock.Lock;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowExecutionMapper;
import com.helianhealth.iflow.core.model.WorkflowModel;
import com.helianhealth.iflow.core.service.MetadataService;
import com.helianhealth.iflow.core.utils.IDGenerator;
import com.helianhealth.iflow.core.utils.ParametersUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class WorkflowExecutorOpsAsyncTasksTest {

    @ParameterizedTest
    @CsvSource({"false,false,false", "false,true,false", "true,false,false", "true,true,false",
            "false,false,true", "false,true,true", "true,false,true", "true,true,true"})
    void retriesFailedBusinessTasksAndAdvancesOnceForBothEntryPoints(boolean synchronous, boolean async, boolean permissive)
            throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger successors = new AtomicInteger();
        ParametersUtils parameters = new ParametersUtils(new ObjectMapper());
        WorkflowTask http = task("http", TaskType.TASK_TYPE_HTTP);
        http.setRetryCount(2);
        http.setPermissive(permissive);
        WorkflowDef definition = definition("retry-success", List.of(http, mappingTask("after", "${http.output}")));
        MemoryStore store = new MemoryStore();
        TestWorkflowExecutor executor = executor(definition,
                new SystemTaskRegistry(Set.of(retryingHttp(attempts, 1, async), completedMapping(successors))),
                Map.of(TaskType.TASK_TYPE_HTTP, new HttpTaskMapper(parameters),
                        TaskType.TASK_TYPE_MAPPING, new ParamMappingTaskMapper(parameters)), parameters, store);
        ExecutorService workers = Executors.newSingleThreadExecutor();
        ExecutorService caller = Executors.newSingleThreadExecutor();
        executor.useTaskExecutor(workers);
        try {
            var result = caller.submit(() -> {
                if (synchronous) {
                    return executor.startWorkflowSync(input(definition)).getWorkflowId();
                }
                return executor.startWorkflow(input(definition));
            });
            result.get(3, TimeUnit.SECONDS);
            assertTrue(store.completed.await(3, TimeUnit.SECONDS));
            WorkflowModel completed = store.facade().getWorkflowModel(store.workflow.get().getWorkflowId());
            assertEquals(WorkflowModel.Status.COMPLETED, completed.getStatus());
            assertEquals(2, attempts.get());
            assertEquals(1, successors.get());
            assertEquals(3, completed.getTasks().size());
            TaskModel original = completed.getTasks().getFirst();
            TaskModel retry = completed.getTaskByRefName("http");
            assertEquals(TaskModel.Status.FAILED, original.getStatus());
            assertTrue(original.isRetried());
            assertEquals(original.getTaskId(), retry.getRetriedTaskId());
            assertEquals(1, retry.getRetryCount());
            assertEquals(original.getFirstScheduledTime(), retry.getFirstScheduledTime());
            assertTrue(retry.getStartTime() >= retry.getScheduledTime());
            assertEquals("success", completed.getOutput());
        } finally {
            caller.shutdownNow();
            workers.shutdownNow();
        }
    }

    @ParameterizedTest
    @CsvSource({"false,false,false", "true,false,false", "false,true,false", "true,true,false",
            "false,false,true", "true,false,true", "false,true,true", "true,true,true"})
    void exhaustedRetriesApplyFailurePolicyOnlyAfterAllAttempts(boolean optional, boolean permissive, boolean synchronous)
            throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger successors = new AtomicInteger();
        ParametersUtils parameters = new ParametersUtils(new ObjectMapper());
        WorkflowTask http = task("http", TaskType.TASK_TYPE_HTTP);
        http.setRetryCount(2);
        http.setOptional(optional);
        http.setPermissive(permissive);
        WorkflowDef definition = definition("retry-exhausted", List.of(http, mappingTask("after", "continued")));
        MemoryStore store = new MemoryStore();
        TestWorkflowExecutor executor = executor(definition,
                new SystemTaskRegistry(Set.of(retryingHttp(attempts, Integer.MAX_VALUE, true), completedMapping(successors))),
                Map.of(TaskType.TASK_TYPE_HTTP, new HttpTaskMapper(parameters),
                        TaskType.TASK_TYPE_MAPPING, new ParamMappingTaskMapper(parameters)), parameters, store);
        ExecutorService workers = Executors.newSingleThreadExecutor();
        ExecutorService caller = Executors.newSingleThreadExecutor();
        try {
            executor.useTaskExecutor(workers);
            StartWorkflowInput start = input(definition);
            start.setWorkflowId("retry-exhausted");
            var result = caller.submit(() -> {
                if (synchronous) {
                    return executor.startWorkflowSync(start).getWorkflowId();
                }
                return executor.startWorkflow(start);
            });
            if (synchronous && !optional) {
                ExecutionException error = assertThrows(ExecutionException.class,
                        () -> result.get(3, TimeUnit.SECONDS));
                assertTrue(error.getCause() instanceof TerminateWorkflowException);
            } else {
                assertEquals(start.getWorkflowId(), result.get(3, TimeUnit.SECONDS));
            }
            assertTrue(store.completed.await(3, TimeUnit.SECONDS));
            WorkflowModel completed = store.facade().getWorkflowModel(start.getWorkflowId());
            assertEquals(optional ? WorkflowModel.Status.COMPLETED : WorkflowModel.Status.FAILED, completed.getStatus());
            assertEquals(3, attempts.get());
            assertEquals(optional || permissive ? 1 : 0, successors.get());
            assertEquals(2, completed.getTaskByRefName("http").getRetryCount());
            assertEquals(optional ? TaskModel.Status.COMPLETED_WITH_ERRORS : TaskModel.Status.FAILED,
                    completed.getTaskByRefName("http").getStatus());
            if (!optional && permissive) {
                assertTrue(completed.getReasonForInCompletion().contains(completed.getTaskByRefName("http").getTaskId()));
                assertTrue(completed.getReasonForInCompletion().contains("temporary failure"));
                assertEquals(TaskModel.Status.COMPLETED, completed.getTaskByRefName("after").getStatus());
            }
        } finally {
            caller.shutdownNow();
            workers.shutdownNow();
        }
    }

    @ParameterizedTest
    @CsvSource({"complete,false", "complete,true", "cancel,true", "timeout,true"})
    void delayedRetryWaitsWithoutHoldingWorkflowLockAndSkipsTerminalWorkflow(String mode, boolean async) throws Exception {
        boolean cancel = "cancel".equals(mode);
        boolean timeout = "timeout".equals(mode);
        AtomicInteger attempts = new AtomicInteger();
        ParametersUtils parameters = new ParametersUtils(new ObjectMapper());
        WorkflowTask http = task("http", TaskType.TASK_TYPE_HTTP);
        http.setRetryCount(1);
        http.setRetryDelaySeconds(1);
        WorkflowDef definition = definition("delayed-retry", List.of(http));
        if (timeout) {
            definition.setTimeoutSeconds(1);
        }
        MemoryStore store = new MemoryStore();
        TestWorkflowExecutor executor = executor(definition,
                new SystemTaskRegistry(Set.of(retryingHttp(attempts, 1, async))),
                Map.of(TaskType.TASK_TYPE_HTTP, new HttpTaskMapper(parameters)), parameters, store);
        CountDownLatch retryConsumed = new CountDownLatch(1);
        AtomicInteger dispatches = new AtomicInteger();
        try (ExecutorService workers = Executors.newSingleThreadExecutor()) {
            executor.useTaskExecutor(command -> {
                int dispatch = dispatches.incrementAndGet();
                workers.execute(() -> {
                    try {
                        command.run();
                    } finally {
                        if (!async || dispatch >= 2) {
                            retryConsumed.countDown();
                        }
                    }
                });
            });
            String id = executor.startWorkflow(input(definition));
            assertTrue(store.retryScheduled.await(2, TimeUnit.SECONDS));
            TaskModel waiting = store.tasks.get("http");
            assertEquals(TaskModel.Status.SCHEDULED, waiting.getStatus());
            assertEquals(0, waiting.getStartTime());
            assertEquals(1, waiting.getStartDelayInSeconds());
            assertEquals(1, attempts.get());
            // 单线程任务池仍能处理其他工作，工作流锁也已释放。
            assertEquals("available", workers.submit(() -> "available").get(500, TimeUnit.MILLISECONDS));
            if (cancel) {
                executor.terminateWorkflow(store.facade().getWorkflowModel(id), "cancel during retry delay");
            }
            assertTrue(retryConsumed.await(3, TimeUnit.SECONDS));
            WorkflowModel completed = store.facade().getWorkflowModel(id);
            WorkflowModel.Status expected = cancel ? WorkflowModel.Status.TERMINATED
                    : timeout ? WorkflowModel.Status.TIMED_OUT : WorkflowModel.Status.COMPLETED;
            assertEquals(expected, completed.getStatus());
            assertEquals(cancel || timeout ? 1 : 2, attempts.get());
            if (!cancel && !timeout) {
                TaskModel retry = completed.getTaskByRefName("http");
                assertTrue(retry.getStartTime() - retry.getScheduledTime() >= 1_000);
            }
        }
    }

    private static WorkflowSystemTask retryingHttp(AtomicInteger attempts, int failures, boolean async) {
        return new WorkflowSystemTask(TaskType.TASK_TYPE_HTTP) {
            @Override
            public boolean isAsync() {
                return async;
            }

            @Override
            public void start(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
                if (attempts.incrementAndGet() <= failures) {
                    task.setOutputData("failed output");
                    task.setReasonForInCompletion("temporary failure");
                    task.setStatus(TaskModel.Status.FAILED);
                } else {
                    task.setOutputData("success");
                    task.setStatus(TaskModel.Status.COMPLETED);
                }
            }
        };
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void retryInsideForkedLoopDoesNotFailLoopOrJoinPrematurely(boolean async) throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger mappings = new AtomicInteger();
        ParametersUtils parameters = new ParametersUtils(new ObjectMapper());
        WorkflowTask http = task("http", TaskType.TASK_TYPE_HTTP);
        http.setRetryCount(1);
        WorkflowTask loop = task("loop", TaskType.TASK_TYPE_DO_WHILE);
        loop.setLoopOver(List.of(http));
        loop.setLoopCondition("false");
        WorkflowTask fork = task("fork", TaskType.TASK_TYPE_FORK_JOIN);
        fork.setForkTasks(List.of(List.of(loop), List.of(mappingTask("fast", "fast"))));
        WorkflowTask join = task("join", TaskType.TASK_TYPE_JOIN);
        join.setJoinOn(List.of("loop", "fast"));
        WorkflowDef definition = definition("fork-loop-retry",
                List.of(fork, join, mappingTask("after", "finished")));
        MemoryStore store = new MemoryStore();
        TestWorkflowExecutor executor = executor(definition,
                new SystemTaskRegistry(Set.of(new Fork(), new Join(), new DoWhile(parameters, store.facade()),
                        retryingHttp(attempts, 1, async), completedMapping(mappings))),
                Map.of(TaskType.TASK_TYPE_FORK_JOIN, new ForkJoinTaskMapper(),
                        TaskType.TASK_TYPE_JOIN, new JoinTaskMapper(),
                        TaskType.TASK_TYPE_DO_WHILE, new DoWhileTaskMapper(parameters),
                        TaskType.TASK_TYPE_HTTP, new HttpTaskMapper(parameters),
                        TaskType.TASK_TYPE_MAPPING, new ParamMappingTaskMapper(parameters)), parameters, store);
        try (ExecutorService workers = Executors.newSingleThreadExecutor()) {
            executor.useTaskExecutor(workers);
            String id = executor.startWorkflow(input(definition));
            assertTrue(store.completed.await(3, TimeUnit.SECONDS));
            WorkflowModel completed = store.facade().getWorkflowModel(id);
            assertEquals(WorkflowModel.Status.COMPLETED, completed.getStatus());
            assertEquals(2, attempts.get());
            assertEquals(2, mappings.get());
            assertEquals(TaskModel.Status.COMPLETED, completed.getTaskByRefName("loop").getStatus());
            assertEquals(TaskModel.Status.COMPLETED, completed.getTaskByRefName("join").getStatus());
            TaskModel retry = completed.getTaskByRefName("http__1");
            assertEquals(1, retry.getRetryCount());
            assertEquals(1, retry.getIteration());
            assertEquals(completed.getTaskByRefName("fork").getTaskId(), retry.getForkTaskId());
            assertEquals("finished", completed.getOutput());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"HTTP", "JDBC", "SPI", "WEBSERVICE"})
    void queuesLinearAsyncTasksAndPublishesOnlyCompleteResults(String type) throws Exception {
        CountDownLatch releaseWorker = new CountDownLatch(1);
        CountDownLatch taskEntered = new CountDownLatch(1);
        CountDownLatch releaseTask = new CountDownLatch(1);
        AtomicInteger executions = new AtomicInteger();
        AtomicReference<String> executionThread = new AtomicReference<>();
        WorkflowSystemTask businessTask = new WorkflowSystemTask(type) {
            @Override
            public boolean isAsync() {
                return true;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void start(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
                executions.incrementAndGet();
                executionThread.set(Thread.currentThread().getName());
                ((Map<String, Object>) task.getInputData().get("request")).put("value", "rendered");
                task.addInput("renderedBody", "<ready/>");
                task.setStatus(TaskModel.Status.COMPLETED);
                task.setOutputData("partial");
                taskEntered.countDown();
                await(releaseTask);
                task.setOutputData(List.of("complete", 42));
            }
        };
        ParametersUtils parameters = new ParametersUtils(new ObjectMapper());
        WorkflowTask definitionTask = task("io", type);
        definitionTask.setInputParameters(Map.of("request", Map.of("value", "original")));
        WorkflowDef definition = definition("linear-async-" + type, List.of(definitionTask));
        MemoryStore store = new MemoryStore();
        SystemTaskRegistry registry = new SystemTaskRegistry(Set.of(businessTask));
        AtomicInteger terminalNotifications = new AtomicInteger();
        TestWorkflowExecutor executor = executor(definition, registry,
                Map.of(type, businessMapper(type, parameters)), parameters, store,
                new TestWorkflowStatusListener((workflowId, status) -> {
                    terminalNotifications.incrementAndGet();
                    WorkflowModel persisted = store.facade().getWorkflowModel(workflowId);
                    assertEquals(WorkflowModel.Status.COMPLETED, status);
                    assertEquals(status, persisted.getStatus());
                    assertEquals(List.of("complete", 42), persisted.getOutput());
                    assertEquals("test-async-worker", Thread.currentThread().getName());
                    throw new IllegalStateException("listener failure must not block synchronous return");
                }));
        ExecutorService workers = Executors.newSingleThreadExecutor(
                runnable -> new Thread(runnable, "test-async-worker"));
        ExecutorService caller = Executors.newSingleThreadExecutor(
                runnable -> new Thread(runnable, "test-sync-caller"));
        InMemoryQueueDAO queue = executor.queue();
        executor.useTaskExecutor(workers);
        workers.submit(() -> await(releaseWorker));
        try {
            var result = caller.submit(() -> executor.startWorkflowSync(input(definition)));
            assertTrue(store.scheduled.await(2, TimeUnit.SECONDS));
            TaskModel queued = store.tasks.get("io");
            assertEquals(TaskModel.Status.SCHEDULED, queued.getStatus());
            assertEquals(0, queued.getStartTime());
            assertEquals(0, executions.get());
            assertFalse(result.isDone());

            releaseWorker.countDown();
            assertTrue(taskEntered.await(2, TimeUnit.SECONDS));
            assertTrue(queue.containsMessage(type, store.tasks.get("io").getTaskId()));
            assertTrue(store.inProgress.await(2, TimeUnit.SECONDS));
            TaskModel running = store.tasks.get("io");
            assertEquals(TaskModel.Status.IN_PROGRESS, running.getStatus());
            assertTrue(running.getStartTime() > 0);
            assertEquals(Map.of(), running.getOutputData());
            assertEquals(Map.of("request", Map.of("value", "original")), running.getInputData());
            assertThrows(TimeoutException.class, () -> result.get(100, TimeUnit.MILLISECONDS));

            releaseTask.countDown();
            WorkflowModel completed = result.get(2, TimeUnit.SECONDS);
            workers.submit(() -> { }).get(2, TimeUnit.SECONDS);
            assertFalse(queue.containsMessage(type, store.tasks.get("io").getTaskId()));
            assertEquals(WorkflowModel.Status.COMPLETED, completed.getStatus());
            assertEquals("test-async-worker", executionThread.get());
            assertEquals(Set.of("test-sync-caller", "test-async-worker"), store.taskWriteThreads);
            assertEquals(1, executions.get());
            assertEquals(List.of("complete", 42), completed.getOutput());
            TaskModel finished = completed.getTaskByRefName("io");
            assertEquals(TaskModel.Status.COMPLETED, finished.getStatus());
            assertTrue(finished.isExecuted());
            assertEquals(Map.of("request", Map.of("value", "rendered"),
                    "renderedBody", "<ready/>"), finished.getInputData());
            assertEquals(List.of("complete", 42), store.tasks.get("io").getOutputData());
            assertEquals(1, terminalNotifications.get());
        } finally {
            releaseWorker.countDown();
            releaseTask.countDown();
            caller.shutdownNow();
            workers.shutdownNow();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void keepsMappingOnSynchronousCallerEvenInsideFork(boolean forked) {
        Map<String, String> executionThreads = new ConcurrentHashMap<>();
        WorkflowSystemTask mapping = new WorkflowSystemTask(TaskType.TASK_TYPE_MAPPING) {
            @Override
            public void start(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
                executionThreads.put(task.getReferenceTaskName(), Thread.currentThread().getName());
                task.setOutputData(task.getInputData().get("payload"));
                task.setStatus(TaskModel.Status.COMPLETED);
            }
        };
        WorkflowTask first = mappingTask("first", Map.of("value", 1));
        List<WorkflowTask> tasks = List.of(first);
        if (forked) {
            WorkflowTask fork = task("fork", TaskType.TASK_TYPE_FORK_JOIN);
            fork.setForkTasks(List.of(List.of(first), List.of(mappingTask("second", 2))));
            WorkflowTask join = task("join", TaskType.TASK_TYPE_JOIN);
            join.setJoinOn(List.of("first", "second"));
            tasks = List.of(fork, join);
        }
        WorkflowDef definition = definition("mapping-caller-" + forked, tasks);
        ParametersUtils parameters = new ParametersUtils(new ObjectMapper());
        SystemTaskRegistry registry = new SystemTaskRegistry(Set.of(mapping, new Fork(), new Join()));
        TestWorkflowExecutor executor = executor(definition, registry, Map.of(
                TaskType.TASK_TYPE_MAPPING, new ParamMappingTaskMapper(parameters),
                TaskType.TASK_TYPE_FORK_JOIN, new ForkJoinTaskMapper(),
                TaskType.TASK_TYPE_JOIN, new JoinTaskMapper()), parameters, new MemoryStore());
        executor.useTaskExecutor(command -> {
            throw new RejectedExecutionException("MAPPING must stay on the caller thread");
        });

        WorkflowModel result = executor.startWorkflowSync(input(definition));

        assertEquals(WorkflowModel.Status.COMPLETED, result.getStatus());
        assertEquals(forked ? 2 : 1, executionThreads.size());
        assertEquals(Set.of(Thread.currentThread().getName()), Set.copyOf(executionThreads.values()));
        assertEquals(Map.of("value", 1), result.getTaskByRefName("first").getOutputData());
    }

    @Test
    void resolvesMixedAsyncAndMappingSuccessorsAfterMergingCompleteResults() {
        Map<String, String> executionThreads = new ConcurrentHashMap<>();
        WorkflowSystemTask http = new WorkflowSystemTask(TaskType.TASK_TYPE_HTTP) {
            @Override
            public boolean isAsync() {
                return true;
            }

            @Override
            public void start(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
                executionThreads.put("http", Thread.currentThread().getName());
                task.setOutputData(task.getInputData().get("request"));
                task.setStatus(TaskModel.Status.COMPLETED);
            }
        };
        WorkflowSystemTask mapping = new WorkflowSystemTask(TaskType.TASK_TYPE_MAPPING) {
            @Override
            public void start(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
                executionThreads.put("mapping", Thread.currentThread().getName());
                task.setOutputData(task.getInputData().get("payload"));
                task.setStatus(TaskModel.Status.COMPLETED);
            }
        };
        WorkflowSystemTask webService = new WorkflowSystemTask(TaskType.TASK_TYPE_WEBSERVICE) {
            @Override
            public boolean isAsync() {
                return true;
            }

            @Override
            public void start(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
                executionThreads.put("soap", Thread.currentThread().getName());
                task.addInput("renderedBody", "<request><id>7</id></request>");
                task.setOutputData(Map.of("response", List.of("xml", 7)));
                task.setStatus(TaskModel.Status.COMPLETED);
            }
        };
        WorkflowTask request = task("http", TaskType.TASK_TYPE_HTTP);
        request.setInputParameters(Map.of("request", "${workflow.input.source}"));
        WorkflowTask transform = mappingTask("mapping", "${http.output}");
        WorkflowTask soap = task("soap", TaskType.TASK_TYPE_WEBSERVICE);
        soap.setInputParameters(Map.of(
                "templateParameters", Map.of("payload", "${mapping.output}"),
                "soapTemplate", "<request><id>$.payload.id</id></request>"));
        WorkflowDef definition = definition("mixed-async-mapping", List.of(request, transform, soap));
        ParametersUtils parameters = new ParametersUtils(new ObjectMapper());
        MemoryStore store = new MemoryStore();
        SystemTaskRegistry registry = new SystemTaskRegistry(Set.of(http, mapping, webService));
        TestWorkflowExecutor executor = executor(definition, registry, Map.of(
                TaskType.TASK_TYPE_HTTP, new HttpTaskMapper(parameters),
                TaskType.TASK_TYPE_MAPPING, new ParamMappingTaskMapper(parameters),
                TaskType.TASK_TYPE_WEBSERVICE, new WebServiceTaskMapper(parameters)), parameters, store);
        ExecutorService workers = Executors.newFixedThreadPool(2,
                runnable -> new Thread(runnable, "test-mixed-worker"));
        executor.useTaskExecutor(workers);
        List<Object> source = List.of(Map.of("id", 7), "raw");
        try {
            StartWorkflowInput startInput = input(definition);
            startInput.setWorkflowInput(Map.of("source", source));
            WorkflowModel result = executor.startWorkflowSync(startInput);

            assertEquals(WorkflowModel.Status.COMPLETED, result.getStatus());
            assertEquals(source, result.getTaskByRefName("http").getOutputData());
            assertEquals(source, result.getTaskByRefName("mapping").getOutputData());
            assertEquals(Map.of("payload", source),
                    result.getTaskByRefName("soap").getInputData().get("templateParameters"));
            assertEquals("<request><id>7</id></request>",
                    store.tasks.get("soap").getInputData().get("renderedBody"));
            assertEquals(Map.of("response", List.of("xml", 7)), result.getOutput());
            assertEquals("test-mixed-worker", executionThreads.get("http"));
            assertEquals("test-mixed-worker", executionThreads.get("soap"));
            assertEquals("test-mixed-worker", executionThreads.get("mapping"));
        } finally {
            workers.shutdownNow();
        }
    }

    @Test
    void startDecidesOnCallerAndReturnsWhileAsyncTaskRuns() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Map<String, String> mappingThreads = new ConcurrentHashMap<>();
        WorkflowSystemTask mapping = new WorkflowSystemTask(TaskType.TASK_TYPE_MAPPING) {
            @Override
            public void start(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
                mappingThreads.put(task.getReferenceTaskName(), Thread.currentThread().getName());
                task.setOutputData(task.getInputData().get("payload"));
                task.setStatus(TaskModel.Status.COMPLETED);
            }
        };
        WorkflowSystemTask http = new WorkflowSystemTask(TaskType.TASK_TYPE_HTTP) {
            @Override
            public boolean isAsync() { return true; }

            @Override
            public void start(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
                entered.countDown();
                await(release);
                task.setOutputData("finished");
                task.setStatus(TaskModel.Status.COMPLETED);
            }
        };
        ParametersUtils parameters = new ParametersUtils(new ObjectMapper());
        WorkflowDef definition = definition("caller-start", List.of(mappingTask("before", "prepared"),
                task("http", TaskType.TASK_TYPE_HTTP), mappingTask("after", "finished")));
        MemoryStore store = new MemoryStore();
        TestWorkflowExecutor executor = executor(definition, new SystemTaskRegistry(Set.of(http, mapping)),
                Map.of(TaskType.TASK_TYPE_HTTP, new HttpTaskMapper(parameters),
                        TaskType.TASK_TYPE_MAPPING, new ParamMappingTaskMapper(parameters)), parameters, store);
        ExecutorService workers = Executors.newSingleThreadExecutor(
                runnable -> new Thread(runnable, "test-io-worker"));
        ExecutorService request = Executors.newSingleThreadExecutor(
                runnable -> new Thread(runnable, "test-start-caller"));
        executor.useTaskExecutor(workers);
        try {
            String workflowId = request.submit(() -> executor.startWorkflow(input(definition)))
                    .get(2, TimeUnit.SECONDS);
            WorkflowModel running = store.facade().getWorkflowModel(workflowId);
            assertEquals(WorkflowModel.Status.RUNNING, running.getStatus());
            assertNotNull(running.getTaskByRefName("http"), "first decision must finish before returning");
            assertEquals("test-start-caller", mappingThreads.get("before"));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertEquals("available", request.submit(() -> "available").get(2, TimeUnit.SECONDS));

            release.countDown();
            assertTrue(store.completed.await(2, TimeUnit.SECONDS));
            workers.submit(() -> { }).get(2, TimeUnit.SECONDS);
            assertEquals(WorkflowModel.Status.COMPLETED, store.workflow.get().getStatus());
            assertEquals("test-io-worker", mappingThreads.get("after"),
                    "completion must decide on the same I/O worker without another executor");
            assertEquals(Set.of("test-start-caller", "test-io-worker"), store.taskWriteThreads);
            assertEquals(WorkflowModel.Status.RUNNING, running.getStatus());
        } finally {
            release.countDown();
            request.shutdownNow();
            workers.shutdownNow();
        }
    }

    @Test
    void asyncFailurePersistsFailedWorkflowWithoutSchedulingSuccessor() throws Exception {
        AtomicInteger executions = new AtomicInteger();
        WorkflowSystemTask failing = new WorkflowSystemTask(TaskType.TASK_TYPE_HTTP) {
            @Override
            public boolean isAsync() { return true; }

            @Override
            public void start(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
                executions.incrementAndGet();
                task.setReasonForInCompletion("remote endpoint unavailable");
                task.setStatus(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR);
            }
        };
        ParametersUtils parameters = new ParametersUtils(new ObjectMapper());
        WorkflowDef definition = definition("async-failure", List.of(
                task("failed", TaskType.TASK_TYPE_HTTP), task("after", TaskType.TASK_TYPE_HTTP)));
        MemoryStore store = new MemoryStore();
        TestWorkflowExecutor executor = executor(definition, new SystemTaskRegistry(Set.of(failing)),
                Map.of(TaskType.TASK_TYPE_HTTP, new HttpTaskMapper(parameters)), parameters, store);
        ExecutorService workers = Executors.newSingleThreadExecutor();
        executor.useTaskExecutor(workers);
        try {
            executor.startWorkflow(input(definition));
            assertTrue(store.completed.await(2, TimeUnit.SECONDS));
            workers.submit(() -> { }).get(2, TimeUnit.SECONDS);
            WorkflowModel failed = store.workflow.get();
            assertEquals(WorkflowModel.Status.FAILED, failed.getStatus());
            assertTrue(failed.getReasonForInCompletion().contains("remote endpoint unavailable"));
            assertEquals(store.tasks.get("failed").getTaskId(), failed.getFailedTaskId());
            assertFalse(store.tasks.containsKey("after"));
            assertEquals(1, executions.get());
        } finally {
            workers.shutdownNow();
        }
    }

    @Test
    void interruptingSynchronousWaitDoesNotCancelRunningWorkflow() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        WorkflowSystemTask http = new WorkflowSystemTask(TaskType.TASK_TYPE_HTTP) {
            @Override
            public boolean isAsync() { return true; }

            @Override
            public void start(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
                entered.countDown();
                await(release);
                task.setOutputData("finished after caller left");
                task.setStatus(TaskModel.Status.COMPLETED);
            }
        };
        ParametersUtils parameters = new ParametersUtils(new ObjectMapper());
        WorkflowDef definition = definition("interrupted-wait", List.of(task("http", TaskType.TASK_TYPE_HTTP)));
        MemoryStore store = new MemoryStore();
        TestWorkflowExecutor executor = executor(definition, new SystemTaskRegistry(Set.of(http)),
                Map.of(TaskType.TASK_TYPE_HTTP, new HttpTaskMapper(parameters)), parameters, store);
        ExecutorService workers = Executors.newSingleThreadExecutor();
        ExecutorService caller = Executors.newSingleThreadExecutor();
        AtomicReference<Thread> callerThread = new AtomicReference<>();
        executor.useTaskExecutor(workers);
        try {
            var result = caller.submit(() -> {
                callerThread.set(Thread.currentThread());
                assertThrows(IllegalStateException.class, () -> executor.startWorkflowSync(input(definition)));
                assertTrue(Thread.currentThread().isInterrupted());
            });
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            callerThread.get().interrupt();
            result.get(2, TimeUnit.SECONDS);
            assertEquals(WorkflowModel.Status.RUNNING, store.workflow.get().getStatus());
            assertEquals(TaskModel.Status.IN_PROGRESS, store.tasks.get("http").getStatus());

            release.countDown();
            assertTrue(store.completed.await(2, TimeUnit.SECONDS));
            workers.submit(() -> { }).get(2, TimeUnit.SECONDS);
            assertEquals(WorkflowModel.Status.COMPLETED, store.workflow.get().getStatus());
            assertEquals("finished after caller left", store.workflow.get().getOutput());
        } finally {
            release.countDown();
            caller.shutdownNow();
            workers.shutdownNow();
        }
    }

    @Test
    void synchronousStartDoesNotSubmitAnyWorkToTaskExecutor() {
        AtomicInteger executions = new AtomicInteger();
        ParametersUtils parameters = new ParametersUtils(new ObjectMapper());
        WorkflowDef definition = definition("synchronous-start", List.of(
                mappingTask("first", "one"), mappingTask("second", "two")));
        MemoryStore store = new MemoryStore();
        TestWorkflowExecutor executor = executor(definition,
                new SystemTaskRegistry(Set.of(completedMapping(executions))),
                Map.of(TaskType.TASK_TYPE_MAPPING, new ParamMappingTaskMapper(parameters)), parameters, store);
        executor.useTaskExecutor(command -> { throw new AssertionError("synchronous work must stay on caller"); });

        String workflowId = executor.startWorkflow(input(definition));
        WorkflowModel completed = store.facade().getWorkflowModel(workflowId);

        assertEquals(WorkflowModel.Status.COMPLETED, completed.getStatus());
        assertEquals("two", completed.getOutput());
        assertEquals(2, executions.get());
        assertEquals(Set.of(Thread.currentThread().getName()), store.taskWriteThreads);
    }

    @Test
    void skipsQueuedTaskThatWasTerminatedBeforeWorkerStarts() {
        AtomicInteger executions = new AtomicInteger();
        WorkflowSystemTask http = new WorkflowSystemTask(TaskType.TASK_TYPE_HTTP) {
            @Override
            public boolean isAsync() { return true; }

            @Override
            public void start(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
                executions.incrementAndGet();
                task.setStatus(TaskModel.Status.COMPLETED);
            }
        };
        ParametersUtils parameters = new ParametersUtils(new ObjectMapper());
        WorkflowDef definition = definition("terminated-before-io", List.of(task("http", TaskType.TASK_TYPE_HTTP)));
        MemoryStore store = new MemoryStore();
        TestWorkflowExecutor executor = executor(definition, new SystemTaskRegistry(Set.of(http)),
                Map.of(TaskType.TASK_TYPE_HTTP, new HttpTaskMapper(parameters)), parameters, store);
        AtomicReference<Runnable> queued = new AtomicReference<>();
        executor.useTaskExecutor(queued::set);
        String workflowId = executor.startWorkflow(input(definition));
        WorkflowModel response = store.facade().getWorkflowModel(workflowId);
        assertTrue(executor.queue().containsMessage("HTTP", response.getTaskByRefName("http").getTaskId()));
        executor.terminateWorkflow(response, "cancelled before worker starts");

        queued.get().run();

        assertEquals(WorkflowModel.Status.TERMINATED, store.workflow.get().getStatus());
        assertEquals(TaskModel.Status.CANCELED, store.tasks.get("http").getStatus());
        assertEquals(0, executions.get());
        assertFalse(executor.queue().containsMessage("HTTP", response.getTaskByRefName("http").getTaskId()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void usesBlockingOrTimedLockAccordingToEntryPoint(boolean synchronous) {
        AtomicInteger executions = new AtomicInteger();
        ParametersUtils parameters = new ParametersUtils(new ObjectMapper());
        WorkflowDef definition = definition("temporarily-busy-lock", List.of(mappingTask("mapping", "value")));
        MemoryStore store = new MemoryStore();
        Lock lock = mock(Lock.class);
        ExecutionLockService locks = new ExecutionLockService(lock);
        TestWorkflowExecutor executor = executor(definition,
                new SystemTaskRegistry(Set.of(completedMapping(executions))),
                Map.of(TaskType.TASK_TYPE_MAPPING, new ParamMappingTaskMapper(parameters)), parameters, store, locks);

        if (synchronous) {
            WorkflowModel result = executor.startWorkflowSync(input(definition));
            assertEquals(WorkflowModel.Status.COMPLETED, result.getStatus());
            verify(lock).acquireLock(anyString(), eq(60_000L), eq(TimeUnit.MILLISECONDS));
            verify(lock).releaseLock(anyString());
            assertEquals(1, executions.get());
            assertEquals(Set.of(Thread.currentThread().getName()), store.taskWriteThreads);
        } else {
            assertThrows(TransientException.class, () -> executor.startWorkflow(input(definition)));
            verify(lock, never()).acquireLock(anyString(), eq(60_000L), eq(TimeUnit.MILLISECONDS));
            verify(lock, never()).releaseLock(anyString());
            assertEquals(0, executions.get());
            assertNull(store.workflow.get());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void isolatesStoredInputAndOutputFromCallerMutations() {
        AtomicInteger executions = new AtomicInteger();
        ParametersUtils parameters = new ParametersUtils(new ObjectMapper());
        WorkflowDef definition = definition("isolated-input", List.of(mappingTask("mapping", "${workflow.input.payload}")));
        MemoryStore store = new MemoryStore();
        TestWorkflowExecutor executor = executor(definition,
                new SystemTaskRegistry(Set.of(completedMapping(executions))),
                Map.of(TaskType.TASK_TYPE_MAPPING, new ParamMappingTaskMapper(parameters)), parameters, store);
        Map<String, Object> callerPayload = new HashMap<>(Map.of("value", "original"));
        StartWorkflowInput startInput = input(definition);
        startInput.setWorkflowInput(Map.of("payload", callerPayload));
        String workflowId = executor.startWorkflow(startInput);
        callerPayload.put("value", "changed by caller");

        assertEquals(workflowId, store.workflow.get().getWorkflowId());
        assertEquals(WorkflowModel.Status.COMPLETED, store.workflow.get().getStatus());
        assertEquals(Map.of("value", "original"), store.workflow.get().getOutput());
        assertEquals(Map.of("payload", Map.of("value", "original")), store.workflow.get().getInput());
        assertEquals(1, executions.get());
    }

    private static WorkflowSystemTask completedMapping(AtomicInteger executions) {
        return new WorkflowSystemTask(TaskType.TASK_TYPE_MAPPING) {
            @Override
            public void start(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
                executions.incrementAndGet();
                task.setOutputData(task.getInputData().get("payload"));
                task.setStatus(TaskModel.Status.COMPLETED);
            }
        };
    }

    private static TestWorkflowExecutor executor(WorkflowDef definition, SystemTaskRegistry registry,
            Map<String, TaskMapper> mappers, ParametersUtils parameters, MemoryStore store,
            WorkflowStatusListener... listeners) {
        return executor(definition, registry, mappers, parameters, store,
                new ExecutionLockService(new InMemoryLock()), listeners);
    }

    private static TestWorkflowExecutor executor(WorkflowDef definition, SystemTaskRegistry registry,
            Map<String, TaskMapper> mappers, ParametersUtils parameters, MemoryStore store,
            ExecutionLockService executionLockService, WorkflowStatusListener... listeners) {
        MetadataService metadata = (MetadataService) Proxy.newProxyInstance(
                MetadataService.class.getClassLoader(), new Class<?>[] {MetadataService.class},
                (proxy, method, args) -> "getWorkflowDef".equals(method.getName())
                        ? definition : defaultValue(method.getReturnType()));
        DeciderService decider = new DeciderService(new IDGenerator(), parameters, registry, mappers) {
            private int rounds;

            @Override
            public DeciderOutcome decide(WorkflowModel workflow) {
                assertTrue(++rounds <= 100, "workflow must not spin while async tasks are outstanding");
                return super.decide(workflow);
            }
        };
        return new TestWorkflowExecutor(decider, registry, store.facade(), metadata,
                new IDGenerator(), parameters, executionLockService, listeners);
    }

    private static TaskMapper businessMapper(String type, ParametersUtils parameters) {
        return switch (type) {
            case TaskType.TASK_TYPE_HTTP -> new HttpTaskMapper(parameters);
            case TaskType.TASK_TYPE_JDBC -> new JdbcTaskMapper(parameters);
            case TaskType.TASK_TYPE_WEBSERVICE -> new WebServiceTaskMapper(parameters);
            // SPI's production mapper belongs to the sibling plugin; core tests only supply its port.
            case TaskType.TASK_TYPE_SPI -> new TaskMapper() {
                @Override
                public String getTaskType() {
                    return TaskType.TASK_TYPE_SPI;
                }

                @Override
                public List<TaskModel> getMappedTasks(TaskMapperContext context) {
                    TaskModel task = context.createTaskModel();
                    task.setInputData(context.getTaskInput());
                    task.setStatus(TaskModel.Status.SCHEDULED);
                    return List.of(task);
                }
            };
            default -> throw new IllegalArgumentException(type);
        };
    }

    private static WorkflowDef definition(String route, List<WorkflowTask> tasks) {
        return WorkflowDef.builder().route(route).tasks(tasks).build();
    }

    private static WorkflowTask task(String reference, String type) {
        return WorkflowTask.builder().name(reference).taskReferenceName(reference).type(type).build();
    }

    private static WorkflowTask mappingTask(String reference, Object payload) {
        WorkflowTask task = task(reference, TaskType.TASK_TYPE_MAPPING);
        task.setInputParameters(Map.of("payload", payload,
                "paramsConfig", List.of(Map.of("field", "value"))));
        return task;
    }

    private static StartWorkflowInput input(WorkflowDef definition) {
        StartWorkflowInput input = new StartWorkflowInput();
        input.setRoute(definition.getRoute());
        return input;
    }

    private static void await(CountDownLatch signal) {
        try {
            if (!signal.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("test task was not released");
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(error);
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == Optional.class) {
            return Optional.empty();
        }
        return null;
    }

    private static final class MemoryStore {
        private final Map<String, TaskModel> tasks = new ConcurrentHashMap<>();
        private final Map<String, TaskModel> attempts = new ConcurrentHashMap<>();
        private final Set<String> taskWriteThreads = ConcurrentHashMap.newKeySet();
        private final AtomicReference<WorkflowModel> workflow = new AtomicReference<>();
        private final CountDownLatch scheduled = new CountDownLatch(1);
        private final CountDownLatch inProgress = new CountDownLatch(1);
        private final CountDownLatch completed = new CountDownLatch(1);
        private final CountDownLatch retryScheduled = new CountDownLatch(1);

        private ExecutionDAOFacade facade() {
            ExecutionDAO dao = (ExecutionDAO) Proxy.newProxyInstance(ExecutionDAO.class.getClassLoader(),
                    new Class<?>[] {ExecutionDAO.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "createTasks" -> {
                            for (Object task : (List<?>) args[0]) {
                                saveTask((TaskModel) task);
                            }
                            scheduled.countDown();
                            yield args[0];
                        }
                        case "updateTask" -> {
                            saveTask((TaskModel) args[0]);
                            yield null;
                        }
                        case "getTask" -> Optional.ofNullable(attempts.get(args[0])).map(MemoryStore::copyTask);
                        case "getWorkflow" -> Optional.ofNullable(workflow.get()).map(source -> {
                            WorkflowModel snapshot = copyWorkflow(source);
                            if ((boolean) args[1]) {
                                snapshot.setTasks(attempts.values().stream()
                                        .sorted(java.util.Comparator.comparingInt(TaskModel::getSeq))
                                        .map(MemoryStore::copyTask)
                                        .collect(Collectors.toCollection(ArrayList::new)));
                            }
                            return snapshot;
                        });
                        case "createWorkflow", "updateWorkflow" -> {
                            WorkflowModel source = (WorkflowModel) args[0];
                            WorkflowModel snapshot = copyWorkflow(source);
                            workflow.set(snapshot);
                            if (snapshot.getStatus().isTerminal()) {
                                completed.countDown();
                            }
                            yield source.getWorkflowId();
                        }
                        default -> defaultValue(method.getReturnType());
                    });
            return new ExecutionDAOFacade(dao, new ObjectMapper());
        }

        @SuppressWarnings("unchecked")
        private static WorkflowModel copyWorkflow(WorkflowModel source) {
            WorkflowModel snapshot = WorkflowExecutionMapper.toModel(
                    WorkflowExecutionMapper.toWorkflowHeader(source));
            snapshot.setInput((Map<String, Object>) WorkflowExecutionMapper.copyOutput(source.getInput()));
            snapshot.setTasks(source.getTasks().stream().map(MemoryStore::copyTask)
                    .collect(Collectors.toCollection(ArrayList::new)));
            return snapshot;
        }

        private void saveTask(TaskModel source) {
            taskWriteThreads.add(Thread.currentThread().getName());
            TaskModel snapshot = copyTask(source);
            tasks.compute(snapshot.getReferenceTaskName(), (reference, current) ->
                    current != null && current.getRetryCount() > snapshot.getRetryCount() ? current : snapshot);
            attempts.put(snapshot.getTaskId(), snapshot);
            if (snapshot.getRetryCount() > 0 && snapshot.getStatus() == TaskModel.Status.SCHEDULED) {
                retryScheduled.countDown();
            }
            if (snapshot.getStatus() == TaskModel.Status.IN_PROGRESS) {
                inProgress.countDown();
            }
        }

        @SuppressWarnings("unchecked")
        private static TaskModel copyTask(TaskModel source) {
            TaskModel snapshot = TaskModel.fromTask(source.toTask());
            snapshot.setInputData((Map<String, Object>) WorkflowExecutionMapper.copyOutput(source.getInputData()));
            return snapshot;
        }
    }
}
