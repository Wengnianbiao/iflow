package com.helianhealth.iflow.core.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.iflow.common.domain.metadata.task.TaskType;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowDef;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowTask;
import com.helianhealth.iflow.core.dal.ExecutionDAOFacade;
import com.helianhealth.iflow.core.dao.ExecutionDAO;
import com.helianhealth.iflow.core.execution.mapper.TaskMapper;
import com.helianhealth.iflow.core.execution.mapper.TerminateTaskMapper;
import com.helianhealth.iflow.core.execution.tasks.SystemTaskRegistry;
import com.helianhealth.iflow.core.execution.tasks.Terminate;
import com.helianhealth.iflow.core.execution.tasks.WorkflowSystemTask;
import com.helianhealth.iflow.core.lock.InMemoryExecutionLockService;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import com.helianhealth.iflow.core.service.MetadataService;
import com.helianhealth.iflow.core.utils.IDGenerator;
import com.helianhealth.iflow.core.utils.ParametersUtils;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowExecutorOpsEndExecutionTest {

    @Test
    void usesFailedStatusAndReasonFromTerminateInput() {
        WorkflowModel result = executeTerminateWorkflow(WorkflowModel.Status.FAILED, "rejected");

        assertEquals(WorkflowModel.Status.FAILED, result.getStatus());
        assertEquals("rejected", result.getReasonForInCompletion());
        assertEquals(result.getTaskByRefName("terminate_ref").getTaskId(), result.getFailedTaskId());
        assertTrue(result.getEndTime() > 0);
    }

    @Test
    void usesTerminatedStatusAndReasonFromTerminateInput() {
        WorkflowModel result =
                executeTerminateWorkflow(WorkflowModel.Status.TERMINATED, "stopped by rule");

        assertEquals(WorkflowModel.Status.TERMINATED, result.getStatus());
        assertEquals("stopped by rule", result.getReasonForInCompletion());
        assertEquals(result.getTaskByRefName("terminate_ref").getTaskId(), result.getFailedTaskId());
    }

    @Test
    void completesWithTerminateOutputAndGeneratedReason() {
        WorkflowModel result = executeTerminateWorkflow(WorkflowModel.Status.COMPLETED, null);

        assertEquals(WorkflowModel.Status.COMPLETED, result.getStatus());
        assertEquals(
                Map.of("result", "done"),
                result.getTaskByRefName("terminate_ref").getOutputData());
        assertEquals(Map.of("result", "done"), result.getOutput());
        assertTrue(
                result.getReasonForInCompletion()
                        .startsWith("Workflow is COMPLETED by TERMINATE task:"));
    }

    @Test
    void cancelsNonTerminalTasksAfterTerminateFinalizesWorkflow() {
        AtomicBoolean cancelCalled = new AtomicBoolean();
        WorkflowSystemTask noop =
                new WorkflowSystemTask(TaskType.TASK_TYPE_NOOP) {
                    @Override
                    public void cancel(
                            WorkflowModel workflow,
                            TaskModel task,
                            WorkflowExecutor workflowExecutor) {
                        cancelCalled.set(true);
                    }
                };
        SystemTaskRegistry registry = new SystemTaskRegistry(Set.of(noop));
        WorkflowExecutorOps executor = newExecutor(registry, Map.of(), null);

        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("workflow-id");
        workflow.setWorkflowDefinition(
                WorkflowDef.builder().route("terminate-with-pending-task").build());

        TaskModel terminateTask = new TaskModel();
        terminateTask.setTaskId("terminate-task-id");
        terminateTask.setReferenceTaskName("terminate_ref");
        terminateTask.setTaskType(TaskType.TASK_TYPE_TERMINATE);
        terminateTask.setStatus(TaskModel.Status.COMPLETED);
        terminateTask.setExecuted(true);
        terminateTask.setInputData(
                Map.of(
                        Terminate.getTerminationStatusParameter(),
                        WorkflowModel.Status.COMPLETED.name()));

        TaskModel pendingTask = new TaskModel();
        pendingTask.setTaskId("pending-task-id");
        pendingTask.setReferenceTaskName("pending_ref");
        pendingTask.setTaskType(TaskType.TASK_TYPE_NOOP);
        pendingTask.setStatus(TaskModel.Status.IN_PROGRESS);
        workflow.setTasks(List.of(terminateTask, pendingTask));

        WorkflowModel result = executor.decideWithLock(workflow);

        assertEquals(WorkflowModel.Status.COMPLETED, result.getStatus());
        assertEquals(TaskModel.Status.CANCELED, pendingTask.getStatus());
        assertTrue(pendingTask.isExecuted());
        assertTrue(cancelCalled.get());
    }

    private WorkflowModel executeTerminateWorkflow(
            WorkflowModel.Status terminationStatus, String reason) {
        ParametersUtils parametersUtils = new ParametersUtils(new ObjectMapper());
        Terminate terminate = new Terminate();
        SystemTaskRegistry registry = new SystemTaskRegistry(Set.of(terminate));
        Map<String, TaskMapper> mappers =
                Map.of(
                        TaskType.TASK_TYPE_TERMINATE,
                        new TerminateTaskMapper(parametersUtils));

        Map<String, Object> input = new HashMap<>();
        input.put(Terminate.getTerminationStatusParameter(), terminationStatus.name());
        input.put(Terminate.getTerminationWorkflowOutputParameter(), Map.of("result", "done"));
        if (reason != null) {
            input.put(Terminate.getTerminationReasonParameter(), reason);
        }

        WorkflowTask terminateDefinition =
                WorkflowTask.builder()
                        .name("terminate")
                        .taskReferenceName("terminate_ref")
                        .type(TaskType.TASK_TYPE_TERMINATE)
                        .inputParameters(input)
                        .build();
        WorkflowDef definition =
                WorkflowDef.builder()
                        .route("terminate-workflow")
                        .tasks(List.of(terminateDefinition))
                        .build();

        WorkflowExecutorOps executor = newExecutor(registry, mappers, definition);
        WorkflowModel result =
                executor.startWorkflow(
                        StartWorkflowInput.builder().route(definition.getRoute()).build());

        assertNotNull(result.getTaskByRefName("terminate_ref"));
        return result;
    }

    private WorkflowExecutorOps newExecutor(
            SystemTaskRegistry registry,
            Map<String, TaskMapper> mappers,
            WorkflowDef definition) {
        ParametersUtils parametersUtils = new ParametersUtils(new ObjectMapper());
        DeciderService decider =
                new DeciderService(new IDGenerator(), parametersUtils, registry, mappers);
        ExecutionDAO executionDAO =
                (ExecutionDAO)
                        Proxy.newProxyInstance(
                                ExecutionDAO.class.getClassLoader(),
                                new Class<?>[] {ExecutionDAO.class},
                                (proxy, method, args) ->
                                        switch (method.getName()) {
                                            case "createTasks" -> args[0];
                                            case "updateWorkflow" ->
                                                    ((WorkflowModel) args[0]).getWorkflowId();
                                            default -> defaultValue(method.getReturnType());
                                        });
        ExecutionDAOFacade executionDAOFacade =
                new ExecutionDAOFacade(executionDAO, new ObjectMapper());
        MetadataService metadataService =
                (MetadataService)
                        Proxy.newProxyInstance(
                                MetadataService.class.getClassLoader(),
                                new Class<?>[] {MetadataService.class},
                                (proxy, method, args) -> {
                                    if (definition != null
                                            && "getWorkflowDef".equals(method.getName())) {
                                        return definition;
                                    }
                                    return defaultValue(method.getReturnType());
                                });
        return new WorkflowExecutorOps(
                decider,
                registry,
                executionDAOFacade,
                metadataService,
                new IDGenerator(),
                parametersUtils,
                new InMemoryExecutionLockService());
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
