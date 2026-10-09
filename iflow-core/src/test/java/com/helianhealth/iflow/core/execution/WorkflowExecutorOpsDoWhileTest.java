package com.helianhealth.iflow.core.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.iflow.common.domain.metadata.task.TaskType;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowDef;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowTask;
import com.helianhealth.iflow.core.dal.ExecutionDAOFacade;
import com.helianhealth.iflow.core.dao.ExecutionDAO;
import com.helianhealth.iflow.core.exception.TerminateWorkflowException;
import com.helianhealth.iflow.core.execution.evaluators.Evaluator;
import com.helianhealth.iflow.core.execution.evaluators.ValueParamEvaluator;
import com.helianhealth.iflow.core.execution.mapper.DoWhileTaskMapper;
import com.helianhealth.iflow.core.execution.mapper.ForkJoinTaskMapper;
import com.helianhealth.iflow.core.execution.mapper.JoinTaskMapper;
import com.helianhealth.iflow.core.execution.mapper.InlineTaskMapper;
import com.helianhealth.iflow.core.execution.mapper.TaskMapper;
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
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class WorkflowExecutorOpsDoWhileTest {

    @Test
    void completesSequentialBodyInEveryIterationAndCreatesEachTaskOnce() {
        WorkflowTask first = task("first", TaskType.TASK_TYPE_INLINE);
        WorkflowTask tail = task("tail", TaskType.TASK_TYPE_INLINE);
        tail.setInputParameters(Map.of("value", "${first.output}"));
        WorkflowTask loop = loop(first, tail);
        WorkflowTask after = task("after", TaskType.TASK_TYPE_INLINE);
        AtomicInteger evaluations = new AtomicInteger();
        Fixture fixture = new Fixture(
                definition(loop, after),
                (expression, input) -> {
                    int iteration = ((Number) input.get("iteration")).intValue();
                    assertEquals(iteration, input.get("tail"));
                    evaluations.incrementAndGet();
                    return iteration < 3;
                },
                (workflow, runtime) -> {
                    if (runtime.getReferenceTaskName().startsWith("tail__")) {
                        assertEquals(runtime.getIteration(), runtime.getInputData().get("value"));
                    }
                    runtime.setOutputData(runtime.getIteration());
                    runtime.setStatus(TaskModel.Status.COMPLETED);
                });

        WorkflowModel result = fixture.start(Map.of());

        assertEquals(WorkflowModel.Status.COMPLETED, result.getStatus());
        assertEquals(List.of("first__1", "tail__1", "first__2", "tail__2", "first__3", "tail__3", "after"),
                fixture.executions);
        assertEquals(3, evaluations.get());
        assertEquals(3, result.getTaskByRefName("loop").getIteration());
        assertEquals(Set.of(Thread.currentThread().getName()), Set.copyOf(fixture.executionThreads.values()));
        fixture.assertCreatedOnceAndPersisted(result);
        // A persisted snapshot must include the controller's final state and the final task's executed flag.
        assertEquals(3, fixture.persistedTasks.get("loop").getIteration());
        assertTrue(fixture.persistedTasks.get("after").isExecuted());
    }

    @Test
    void resolvesListItemAndIndexForEveryIterationIncludingNull() {
        WorkflowTask body = task("body", TaskType.TASK_TYPE_INLINE);
        body.setInputParameters(Map.of("item", "${loop.output.loopItem}", "index", "${loop.output.loopIndex}"));
        WorkflowTask loop = loop(body);
        loop.setLoopCondition(null);
        loop.setItems("${workflow.input.items}");
        List<Object> items = Arrays.asList(Map.of("id", 7), "second", null);
        List<Map<String, Object>> inputs = new ArrayList<>();
        Fixture fixture = new Fixture(
                definition(loop),
                (expression, input) -> { throw new AssertionError("list loop needs no condition"); },
                (workflow, runtime) -> {
                    inputs.add(new HashMap<>(runtime.getInputData()));
                    runtime.setOutputData(runtime.getInputData().get("item"));
                    runtime.setStatus(TaskModel.Status.COMPLETED);
                });

        WorkflowModel result = fixture.start(Map.of("items", items));

        assertEquals(WorkflowModel.Status.COMPLETED, result.getStatus());
        assertEquals(List.of("body__1", "body__2", "body__3"), fixture.executions);
        assertEquals(items, inputs.stream().map(input -> input.get("item")).toList());
        assertEquals(List.of(0, 1, 2), inputs.stream().map(input -> input.get("index")).toList());
        assertNull(result.getTaskByRefName("body__3").getOutputData());
        fixture.assertCreatedOnceAndPersisted(result);
    }

    @Test
    void emptyListSkipsBodyAndContinuesAfterLoop() {
        WorkflowTask loop = loop(task("body", TaskType.TASK_TYPE_INLINE));
        loop.setItems("${workflow.input.items}");
        Fixture fixture = new Fixture(
                definition(loop, task("after", TaskType.TASK_TYPE_INLINE)),
                (expression, input) -> { throw new AssertionError("empty list must not evaluate condition"); },
                (workflow, runtime) -> runtime.setStatus(TaskModel.Status.COMPLETED));

        WorkflowModel result = fixture.start(Map.of("items", List.of()));

        assertEquals(WorkflowModel.Status.COMPLETED, result.getStatus());
        assertEquals(List.of("after"), fixture.executions);
        assertEquals(0, result.getTaskByRefName("loop").getIteration());
        assertNull(result.getTaskByRefName("body__1"));
        fixture.assertCreatedOnceAndPersisted(result);
    }

    @Test
    void reevaluatesExistingTasksUntilBodyCompletesWithoutRecreatingThem() {
        AtomicInteger bodyCalls = new AtomicInteger();
        AtomicInteger evaluations = new AtomicInteger();
        Fixture fixture = new Fixture(
                definition(loop(task("body", TaskType.TASK_TYPE_INLINE))),
                (expression, input) -> {
                    assertEquals(2, bodyCalls.get(), "condition must wait for the body");
                    evaluations.incrementAndGet();
                    return false;
                },
                (workflow, runtime) -> {
                    if (bodyCalls.incrementAndGet() == 2) {
                        runtime.setStatus(TaskModel.Status.COMPLETED);
                    }
                });

        WorkflowModel result = fixture.start(Map.of());

        assertEquals(WorkflowModel.Status.COMPLETED, result.getStatus());
        assertEquals(List.of("body__1", "body__1"), fixture.executions);
        assertEquals(1, evaluations.get());
        fixture.assertCreatedOnceAndPersisted(result);
    }

    @Test
    void loopBodyKeepsForkScopeAndJoinCompletesOnCallerThread() {
        WorkflowTask loop = loop(task("body", TaskType.TASK_TYPE_INLINE));
        WorkflowTask sibling = task("sibling", TaskType.TASK_TYPE_INLINE);
        WorkflowTask fork = task("fork", TaskType.TASK_TYPE_FORK_JOIN);
        fork.setForkTasks(List.of(List.of(loop), List.of(sibling)));
        WorkflowTask join = task("join", TaskType.TASK_TYPE_JOIN);
        join.setJoinOn(List.of("loop", "sibling"));
        Fixture fixture = new Fixture(
                definition(fork, join, task("after", TaskType.TASK_TYPE_INLINE)),
                (expression, input) -> ((Number) input.get("iteration")).intValue() < 2,
                (workflow, runtime) -> runtime.setStatus(TaskModel.Status.COMPLETED));
        ExecutorService pool = Executors.newFixedThreadPool(2, runnable -> new Thread(runnable, "loop-branch-pool"));
        fixture.executor.setTaskScheduler(new CallerAndForkTaskScheduler(pool, fixture.registry));

        try {
            WorkflowModel result = fixture.start(Map.of());

            assertEquals(WorkflowModel.Status.COMPLETED, result.getStatus());
            String forkId = result.getTaskByRefName("fork").getTaskId();
            for (String ref : List.of("loop", "body__1", "body__2", "sibling")) {
                assertEquals(forkId, result.getTaskByRefName(ref).getForkTaskId());
            }
            assertNull(result.getTaskByRefName("join").getForkTaskId());
            assertNull(result.getTaskByRefName("after").getForkTaskId());
            assertEquals("loop-branch-pool", fixture.executionThreads.get("body__1"));
            assertEquals("loop-branch-pool", fixture.executionThreads.get("body__2"));
            assertEquals(Thread.currentThread().getName(), fixture.executionThreads.get("after"));
            fixture.assertCreatedOnceAndPersisted(result);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void loopContainingForkWaitsForBranchTailAndJoinBeforeAdvancing() {
        WorkflowTask fork = task("fork", TaskType.TASK_TYPE_FORK_JOIN);
        fork.setForkTasks(List.of(
                List.of(task("branch_first", TaskType.TASK_TYPE_INLINE), task("branch_tail", TaskType.TASK_TYPE_INLINE)),
                List.of(task("sibling", TaskType.TASK_TYPE_INLINE))));
        WorkflowTask join = task("join", TaskType.TASK_TYPE_JOIN);
        join.setJoinOn(List.of("branch_tail", "sibling"));
        WorkflowTask loop = loop(fork, join, task("tail", TaskType.TASK_TYPE_INLINE));
        AtomicInteger evaluations = new AtomicInteger();
        Fixture fixture = new Fixture(
                definition(loop, task("after", TaskType.TASK_TYPE_INLINE)),
                (expression, input) -> {
                    int iteration = ((Number) input.get("iteration")).intValue();
                    assertEquals(iteration, input.get("tail"));
                    assertEquals(Map.of("branch_tail__" + iteration, iteration, "sibling__" + iteration, iteration), input.get("join"));
                    evaluations.incrementAndGet();
                    return iteration < 2;
                },
                (workflow, runtime) -> {
                    runtime.setOutputData(runtime.getIteration());
                    runtime.setStatus(TaskModel.Status.COMPLETED);
                });
        ExecutorService pool = Executors.newFixedThreadPool(2, runnable -> new Thread(runnable, "loop-inner-fork-pool"));
        fixture.executor.setTaskScheduler(new CallerAndForkTaskScheduler(pool, fixture.registry));

        try {
            WorkflowModel result = fixture.start(Map.of());

            assertEquals(WorkflowModel.Status.COMPLETED, result.getStatus());
            assertEquals(2, evaluations.get());
            for (int iteration = 1; iteration <= 2; iteration++) {
                String forkId = result.getTaskByRefName("fork__" + iteration).getTaskId();
                for (String ref : List.of("branch_first", "branch_tail", "sibling")) {
                    String runtimeRef = ref + "__" + iteration;
                    assertEquals(forkId, result.getTaskByRefName(runtimeRef).getForkTaskId());
                    assertEquals("loop-inner-fork-pool", fixture.executionThreads.get(runtimeRef));
                }
                assertNull(result.getTaskByRefName("join__" + iteration).getForkTaskId());
                assertEquals(Thread.currentThread().getName(), fixture.executionThreads.get("tail__" + iteration));
            }
            assertEquals(1, Collections.frequency(fixture.executions, "after"));
            fixture.assertCreatedOnceAndPersisted(result);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void failedIterationStopsWorkflowWithoutSchedulingNextIterationOrFollowingTask() {
        Fixture fixture = new Fixture(
                definition(loop(task("body", TaskType.TASK_TYPE_INLINE)), task("after", TaskType.TASK_TYPE_INLINE)),
                (expression, input) -> true,
                (workflow, runtime) -> {
                    runtime.setStatus(runtime.getIteration() == 2 ? TaskModel.Status.FAILED : TaskModel.Status.COMPLETED);
                    runtime.setReasonForInCompletion(runtime.getIteration() == 2 ? "body failed" : null);
                });

        TerminateWorkflowException failure = assertThrows(TerminateWorkflowException.class, () -> fixture.start(Map.of()));

        assertEquals(WorkflowModel.Status.FAILED, failure.getWorkflowStatus());
        assertEquals(WorkflowModel.Status.FAILED, fixture.persistedWorkflowStatus);
        assertEquals(List.of("body__1", "body__2"), fixture.executions);
        assertEquals(List.of("loop", "body__1", "body__2"), fixture.createdTasks.stream()
                .map(TaskModel::getReferenceTaskName).toList());
        assertEquals(TaskModel.Status.FAILED, fixture.persistedTasks.get("body__2").getStatus());
        assertEquals("body failed", fixture.persistedTasks.get("body__2").getReasonForInCompletion());
        verify(fixture.dao, never()).removeWorkflow(any());
    }

    private static WorkflowTask task(String ref, String type) {
        return WorkflowTask.builder().name(ref).taskReferenceName(ref).type(type).build();
    }

    private static WorkflowTask loop(WorkflowTask... body) {
        WorkflowTask loop = task("loop", TaskType.TASK_TYPE_DO_WHILE);
        loop.setLoopOver(List.of(body));
        loop.setLoopCondition("continue");
        loop.setEvaluatorType(ValueParamEvaluator.NAME);
        return loop;
    }

    private static WorkflowDef definition(WorkflowTask... tasks) {
        return WorkflowDef.builder().route("do-while-regression").tasks(List.of(tasks)).build();
    }

    private static class Fixture {
        final WorkflowDef definition;
        final ExecutionDAO dao = mock(ExecutionDAO.class);
        final List<TaskModel> createdTasks = new ArrayList<>();
        final Map<String, TaskModel> persistedTasks = new LinkedHashMap<>();
        final List<String> executions = Collections.synchronizedList(new ArrayList<>());
        final Map<String, String> executionThreads = new ConcurrentHashMap<>();
        final SystemTaskRegistry registry;
        final WorkflowExecutorOps executor;
        WorkflowModel.Status persistedWorkflowStatus;

        Fixture(WorkflowDef definition, Evaluator evaluator, BiConsumer<WorkflowModel, TaskModel> body) {
            this.definition = definition;
            ParametersUtils parameters = new ParametersUtils();
            WorkflowSystemTask inline = new WorkflowSystemTask(TaskType.TASK_TYPE_INLINE) {
                @Override
                public void execute(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
                    executions.add(task.getReferenceTaskName());
                    executionThreads.put(task.getReferenceTaskName(), Thread.currentThread().getName());
                    body.accept(workflow, task);
                }
            };
            registry = new SystemTaskRegistry(Set.of(inline,
                    new DoWhile(Map.of(ValueParamEvaluator.NAME, evaluator), parameters), new Fork(), new Join()));
            Map<String, TaskMapper> mappers = Map.of(
                    TaskType.TASK_TYPE_INLINE, new InlineTaskMapper(parameters),
                    TaskType.TASK_TYPE_DO_WHILE, new DoWhileTaskMapper(parameters),
                    TaskType.TASK_TYPE_FORK_JOIN, new ForkJoinTaskMapper(),
                    TaskType.TASK_TYPE_JOIN, new JoinTaskMapper());
            DeciderService decider = new DeciderService(new IDGenerator(), parameters, registry, mappers) {
                private int rounds;

                @Override
                public DeciderOutcome decide(WorkflowModel workflow) {
                    assertTrue(++rounds <= 100, "workflow did not finish within 100 decision rounds");
                    return super.decide(workflow);
                }
            };
            when(dao.createTasks(anyList())).thenAnswer(invocation -> {
                List<TaskModel> tasks = invocation.getArgument(0);
                tasks.forEach(task -> createdTasks.add(TaskModel.fromTask(task.toTask())));
                return tasks;
            });
            doAnswer(invocation -> {
                TaskModel task = invocation.getArgument(0);
                persistedTasks.put(task.getReferenceTaskName(), TaskModel.fromTask(task.toTask()));
                return null;
            }).when(dao).updateTask(any());
            when(dao.updateWorkflow(any())).thenAnswer(invocation -> {
                WorkflowModel workflow = invocation.getArgument(0);
                persistedWorkflowStatus = workflow.getStatus();
                return workflow.getWorkflowId();
            });
            MetadataService metadata = mock(MetadataService.class);
            when(metadata.getWorkflowDef(definition.getRoute(), null)).thenReturn(definition);
            executor = new WorkflowExecutorOps(decider, registry, new ExecutionDAOFacade(dao, new ObjectMapper()),
                    metadata, new IDGenerator(), parameters, new InMemoryExecutionLockService());
            executor.setTaskScheduler(new CallerAndForkTaskScheduler(Runnable::run, registry));
        }

        WorkflowModel start(Map<String, Object> input) {
            return executor.startWorkflow(StartWorkflowInput.builder().route(definition.getRoute()).workflowInput(input).build());
        }

        void assertCreatedOnceAndPersisted(WorkflowModel result) {
            assertEquals(result.getTasks().size(), createdTasks.size());
            assertEquals(createdTasks.size(), createdTasks.stream().map(TaskModel::getTaskId).distinct().count());
            assertEquals(createdTasks.size(), createdTasks.stream().map(TaskModel::getSeq).distinct().count());
            assertEquals(WorkflowModel.Status.COMPLETED, persistedWorkflowStatus);
            for (TaskModel created : createdTasks) {
                TaskModel persisted = persistedTasks.get(created.getReferenceTaskName());
                assertNotNull(persisted, created.getReferenceTaskName());
                assertEquals(created.getTaskId(), persisted.getTaskId());
                assertEquals(created.getSeq(), persisted.getSeq());
                assertTrue(created.getScheduledTime() > 0);
                assertEquals(created.getFirstScheduledTime(), persisted.getFirstScheduledTime());
                assertEquals(TaskModel.Status.COMPLETED, persisted.getStatus(), created.getReferenceTaskName());
                assertTrue(persisted.isExecuted(), created.getReferenceTaskName());
                assertTrue(persisted.getEndTime() > 0);
            }
        }
    }
}
