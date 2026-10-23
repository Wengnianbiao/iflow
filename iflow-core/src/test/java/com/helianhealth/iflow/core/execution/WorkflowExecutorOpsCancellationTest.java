package com.helianhealth.iflow.core.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.iflow.common.domain.metadata.task.TaskType;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowDef;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowTask;
import com.helianhealth.iflow.core.dal.ExecutionDAOFacade;
import com.helianhealth.iflow.core.dao.ExecutionDAO;
import com.helianhealth.iflow.core.execution.mapper.TaskMapper;
import com.helianhealth.iflow.core.execution.mapper.TaskMapperContext;
import com.helianhealth.iflow.core.execution.tasks.SystemTaskRegistry;
import com.helianhealth.iflow.core.execution.tasks.WorkflowSystemTask;
import com.helianhealth.iflow.core.lock.InMemoryExecutionLockService;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowExecutionMapper;
import com.helianhealth.iflow.core.model.WorkflowModel;
import com.helianhealth.iflow.core.service.MetadataService;
import com.helianhealth.iflow.core.utils.IDGenerator;
import com.helianhealth.iflow.core.utils.ParametersUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Proxy;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowExecutorOpsCancellationTest {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void observesExternalTerminalSnapshotWhileWaitingAndDiscardsLateWorker(boolean timeout)
            throws Exception {
        CountDownLatch releaseWorker = new CountDownLatch(1);
        CountDownLatch executing = new CountDownLatch(1);
        AtomicInteger cancellations = new AtomicInteger();
        AtomicReference<WorkflowModel> workerWorkflow = new AtomicReference<>();
        WorkflowSystemTask remote = new WorkflowSystemTask(TaskType.TASK_TYPE_HTTP) {
            @Override
            public boolean isAsync() {
                return true;
            }

            @Override
            public void start(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
                workerWorkflow.set(workflow);
                executing.countDown();
                try {
                    assertTrue(releaseWorker.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(error);
                }
                task.setOutputData("late result");
                task.setStatus(TaskModel.Status.COMPLETED);
            }

            @Override
            public void cancel(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
                cancellations.incrementAndGet();
            }
        };
        SystemTaskRegistry registry = new SystemTaskRegistry(Set.of(remote));
        WorkflowDef definition = WorkflowDef.builder().route("external-termination")
                .tasks(List.of(WorkflowTask.builder().name("remote").taskReferenceName("remote")
                        .type(TaskType.TASK_TYPE_HTTP).build())).build();
        definition.setTimeoutSeconds(1);
        ParametersUtils parameters = new ParametersUtils(new ObjectMapper());
        TaskMapper mapper = new TaskMapper() {
            @Override
            public String getTaskType() {
                return TaskType.TASK_TYPE_HTTP;
            }

            @Override
            public List<TaskModel> getMappedTasks(TaskMapperContext context) {
                TaskModel task = context.createTaskModel();
                task.setInputData(context.getTaskInput());
                task.setStatus(TaskModel.Status.SCHEDULED);
                return List.of(task);
            }
        };
        MetadataService metadata = (MetadataService) Proxy.newProxyInstance(
                MetadataService.class.getClassLoader(), new Class<?>[] {MetadataService.class},
                (proxy, method, args) -> "getWorkflowDef".equals(method.getName())
                        ? definition : null);
        SnapshotStore store = new SnapshotStore();
        ExecutionDAOFacade dao = store.facade();
        InMemoryExecutionLockService locks = new InMemoryExecutionLockService();
        AtomicInteger terminalNotifications = new AtomicInteger();
        TestWorkflowExecutor executor = new TestWorkflowExecutor(
                new DeciderService(new IDGenerator(), parameters, registry,
                        Map.of(TaskType.TASK_TYPE_HTTP, mapper)),
                registry, dao, metadata, new IDGenerator(), parameters, locks,
                new TestWorkflowStatusListener((workflowId, status) -> {
                    WorkflowModel persisted = dao.getWorkflowModel(workflowId);
                    assertEquals(timeout ? WorkflowModel.Status.TIMED_OUT : WorkflowModel.Status.TERMINATED,
                            status);
                    assertEquals(status, persisted.getStatus());
                    assertTrue(persisted.getTasks().stream().allMatch(task -> task.getStatus().isTerminal()));
                    terminalNotifications.incrementAndGet();
                }));
        ExecutorService workers = Executors.newSingleThreadExecutor();
        ExecutorService caller = Executors.newSingleThreadExecutor();
        executor.useTaskExecutor(workers);
        try {
            StartWorkflowInput startInput = new StartWorkflowInput();
            startInput.setRoute(definition.getRoute());
            var result = caller.submit(() -> executor.startWorkflowSync(startInput));
            assertTrue(executing.await(2, TimeUnit.SECONDS));
            assertTrue(store.inProgress.await(2, TimeUnit.SECONDS));
            WorkflowModel external = dao.getWorkflowModel(store.workflow.get().getWorkflowId());
            assertNotSame(workerWorkflow.get(), external,
                    "cancellation must use an independent persisted snapshot");
            if (timeout) {
                // Expire the stored snapshot without delaying the test for the execution deadline.
                external.setStartTime(System.currentTimeMillis() - 5_000);
                dao.updateWorkflow(external);
                assertTrue(new WorkflowSweeper(registry, dao, locks, 1)
                        .sweepStored(external.getWorkflowId(), executor));
            } else {
                executor.terminateWorkflow(external, "external cancellation");
            }

            WorkflowModel completed = result.get(2, TimeUnit.SECONDS);
            WorkflowModel.Status expectedWorkflow = timeout
                    ? WorkflowModel.Status.TIMED_OUT : WorkflowModel.Status.TERMINATED;
            TaskModel.Status expectedTask = timeout
                    ? TaskModel.Status.TIMED_OUT : TaskModel.Status.CANCELED;
            assertEquals(expectedWorkflow, completed.getStatus());
            assertEquals(expectedTask, completed.getTaskByRefName("remote").getStatus());
            assertEquals(1, cancellations.get());
            assertTrue(completed.getEndTime() > 0);
            assertEquals(Map.of(), completed.getTaskByRefName("remote").getOutputData());
            assertEquals(1, terminalNotifications.get());

            releaseWorker.countDown();
            workers.shutdown();
            assertTrue(workers.awaitTermination(2, TimeUnit.SECONDS));
            WorkflowModel stored = dao.getWorkflowModel(completed.getWorkflowId());
            assertEquals(expectedWorkflow, stored.getStatus());
            assertEquals(expectedTask, stored.getTaskByRefName("remote").getStatus());
            assertEquals(Map.of(), stored.getTaskByRefName("remote").getOutputData());
            executor.terminateWorkflow(stored, "already terminal");
            assertEquals(1, terminalNotifications.get(), "observing terminal state must not notify again");
        } finally {
            releaseWorker.countDown();
            caller.shutdownNow();
            workers.shutdownNow();
        }
    }

    /** The DAO always returns independent snapshots, as the persistent repository does. */
    private static final class SnapshotStore {
        private final AtomicReference<WorkflowModel> workflow = new AtomicReference<>();
        private final Map<String, TaskModel> tasks = new ConcurrentHashMap<>();
        private final CountDownLatch inProgress = new CountDownLatch(1);

        private ExecutionDAOFacade facade() {
            ExecutionDAO dao = (ExecutionDAO) Proxy.newProxyInstance(
                    ExecutionDAO.class.getClassLoader(), new Class<?>[] {ExecutionDAO.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "createWorkflow", "updateWorkflow" -> {
                            WorkflowModel source = (WorkflowModel) args[0];
                            workflow.set(copyHeader(source));
                            yield source.getWorkflowId();
                        }
                        case "createTasks" -> {
                            for (Object task : (List<?>) args[0]) {
                                saveTask((TaskModel) task);
                            }
                            yield args[0];
                        }
                        case "updateTask" -> {
                            saveTask((TaskModel) args[0]);
                            yield null;
                        }
                        case "getTask" -> Optional.ofNullable(tasks.get(args[0])).map(SnapshotStore::copyTask);
                        case "getWorkflow" -> Optional.ofNullable(workflow.get()).map(source -> {
                            WorkflowModel snapshot = copyHeader(source);
                            if ((boolean) args[1]) {
                                snapshot.setTasks(tasks.values().stream()
                                        .sorted(Comparator.comparingInt(TaskModel::getSeq))
                                        .map(SnapshotStore::copyTask).toList());
                            }
                            return snapshot;
                        });
                        default -> null;
                    });
            return new ExecutionDAOFacade(dao, new ObjectMapper());
        }

        private void saveTask(TaskModel task) {
            tasks.put(task.getTaskId(), copyTask(task));
            if (task.getStatus() == TaskModel.Status.IN_PROGRESS) {
                inProgress.countDown();
            }
        }

        private static WorkflowModel copyHeader(WorkflowModel source) {
            return WorkflowExecutionMapper.toModel(WorkflowExecutionMapper.toWorkflowHeader(source));
        }

        private static TaskModel copyTask(TaskModel source) {
            return TaskModel.fromTask(source.toTask());
        }
    }
}
