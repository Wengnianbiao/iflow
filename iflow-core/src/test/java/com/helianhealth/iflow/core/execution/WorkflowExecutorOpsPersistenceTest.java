package com.helianhealth.iflow.core.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.iflow.common.domain.metadata.task.TaskType;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowDef;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowTask;
import com.helianhealth.iflow.core.dal.ExecutionDAOFacade;
import com.helianhealth.iflow.core.dao.ExecutionDAO;
import com.helianhealth.iflow.core.exception.TerminateWorkflowException;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkflowExecutorOpsPersistenceTest {

    @Test
    void persistsFailedTaskAndWorkflowBeforePropagatingTermination() {
        WorkflowSystemTask failingTask =
                new WorkflowSystemTask(TaskType.TASK_TYPE_NOOP) {
                    @Override
                    public void start(
                            WorkflowModel workflow,
                            TaskModel task,
                            WorkflowExecutor workflowExecutor) {
                        task.setStatus(TaskModel.Status.FAILED);
                        task.setReasonForInCompletion("remote endpoint unavailable");
                    }
                };
        SystemTaskRegistry registry = new SystemTaskRegistry(Set.of(failingTask));
        ParametersUtils parametersUtils = new ParametersUtils(new ObjectMapper());
        DeciderService deciderService =
                new DeciderService(
                        new IDGenerator(),
                        parametersUtils,
                        registry,
                        Map.of(TaskType.TASK_TYPE_NOOP, new NoopTaskMapper()));

        WorkflowTask taskDefinition =
                WorkflowTask.builder()
                        .name("failing-task")
                        .taskReferenceName("failing_ref")
                        .type(TaskType.TASK_TYPE_NOOP)
                        .build();
        WorkflowDef workflowDefinition =
                WorkflowDef.builder()
                        .route("failed-workflow-persistence")
                        .version(1)
                        .tasks(List.of(taskDefinition))
                        .build();

        AtomicReference<TaskModel> persistedTask = new AtomicReference<>();
        AtomicReference<WorkflowModel> persistedWorkflow = new AtomicReference<>();
        AtomicBoolean workflowRemoved = new AtomicBoolean();
        ExecutionDAO executionDAO =
                (ExecutionDAO)
                        Proxy.newProxyInstance(
                                ExecutionDAO.class.getClassLoader(),
                                new Class<?>[] {ExecutionDAO.class},
                                (proxy, method, args) ->
                                        switch (method.getName()) {
                                            case "createTasks" -> args[0];
                                            case "updateTask" -> {
                                                persistedTask.set((TaskModel) args[0]);
                                                yield null;
                                            }
                                            case "updateWorkflow" -> {
                                                WorkflowModel workflow = (WorkflowModel) args[0];
                                                persistedWorkflow.set(workflow);
                                                yield workflow.getWorkflowId();
                                            }
                                            case "removeWorkflow" -> {
                                                workflowRemoved.set(true);
                                                yield null;
                                            }
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

        TerminateWorkflowException termination =
                assertThrows(
                        TerminateWorkflowException.class,
                        () ->
                                executor.startWorkflow(
                                        StartWorkflowInput.builder()
                                                .route(workflowDefinition.getRoute())
                                                .build()));

        assertEquals(WorkflowModel.Status.FAILED, termination.getWorkflowStatus());
        assertNotNull(persistedTask.get());
        assertEquals(TaskModel.Status.FAILED, persistedTask.get().getStatus());
        assertEquals(
                "remote endpoint unavailable",
                persistedTask.get().getReasonForInCompletion());
        assertNotNull(persistedWorkflow.get());
        assertEquals(WorkflowModel.Status.FAILED, persistedWorkflow.get().getStatus());
        assertNotNull(persistedWorkflow.get().getFailedTaskId());
        assertEquals(
                termination.getMessage(),
                persistedWorkflow.get().getReasonForInCompletion());
        assertFalse(workflowRemoved.get());
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
        return null;
    }
}
