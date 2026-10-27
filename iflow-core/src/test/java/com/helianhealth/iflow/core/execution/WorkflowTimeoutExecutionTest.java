package com.helianhealth.iflow.core.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowDef;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowTask;
import com.helianhealth.iflow.core.config.IflowProperties;
import com.helianhealth.iflow.core.dal.ExecutionDAOFacade;
import com.helianhealth.iflow.core.execution.mapper.TaskMapper;
import com.helianhealth.iflow.core.execution.tasks.SystemTaskRegistry;
import com.helianhealth.iflow.core.listener.WorkflowStatusListener;
import com.helianhealth.iflow.core.lock.InMemoryExecutionLockService;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import com.helianhealth.iflow.core.service.MetadataService;
import com.helianhealth.iflow.core.utils.IDGenerator;
import com.helianhealth.iflow.core.utils.ParametersUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WorkflowTimeoutExecutionTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final IflowProperties properties = new IflowProperties();
    private final TaskMapper taskMapper = mock(TaskMapper.class);
    private final SystemTaskRegistry registry = new SystemTaskRegistry(Set.of());
    private final ExecutionDAOFacade dao = mock(ExecutionDAOFacade.class);
    private final MetadataService metadata = mock(MetadataService.class);
    private final WorkflowStatusListener listener = mock(WorkflowStatusListener.class);
    private final AtomicReference<WorkflowModel> created = new AtomicReference<>();

    @Test
    void alertOnlyKeepsRunningWithoutExtraPersistenceOrTimeoutCallback() {
        WorkflowDef definition = definition();
        definition.setTimeoutPolicy(WorkflowDef.TimeoutPolicy.ALERT_ONLY);
        WorkflowExecutor executor = executor(definition);
        when(dao.createWorkflow(any())).thenAnswer(invocation -> {
            WorkflowModel workflow = invocation.getArgument(0);
            workflow.setTasks(List.of(task("remote", TaskModel.Status.IN_PROGRESS)));
            workflow.setStartTime(System.currentTimeMillis() - 120_000);
            created.set(workflow);
            return workflow.getWorkflowId();
        });
        assertNotNull(executor.startWorkflow(input()));
        WorkflowModel workflow = created.get();
        assertEquals(WorkflowModel.Status.RUNNING, workflow.getStatus());
        assertEquals(1, workflow.getTasks().size());
        assertEquals(TaskModel.Status.IN_PROGRESS, workflow.getTasks().getFirst().getStatus());
        verify(dao, never()).updateWorkflow(any());
        verify(listener, never()).onWorkflowTimedOut(any());
        verifyNoInteractions(taskMapper);
        assertNotSame(definition, workflow.getWorkflowDefinition());
        assertEquals(60, workflow.getWorkflowDefinition().getTimeoutSeconds());
        assertEquals(0, definition.getTimeoutSeconds());
    }

    @Test
    void snapshotCopiesNestedDefinitionAndFreezesDefaultBudget() {
        WorkflowDef definition = definition();
        definition.setTimeoutPolicy(null);
        definition.getTasks().getFirst().setInputParameters(Map.of("value", List.of("original")));
        WorkflowExecutor executor = executor(definition);
        when(dao.createWorkflow(any())).thenAnswer(invocation -> {
            WorkflowModel workflow = invocation.getArgument(0);
            workflow.setTasks(List.of(task("remote", TaskModel.Status.IN_PROGRESS)));
            created.set(workflow);
            return workflow.getWorkflowId();
        });
        executor.startWorkflow(input());
        WorkflowDef snapshot = created.get().getWorkflowDefinition();
        assertEquals(60, snapshot.getTimeoutSeconds());
        assertEquals(WorkflowDef.TimeoutPolicy.TIME_OUT_WF, snapshot.getTimeoutPolicy());
        assertEquals(0, definition.getTimeoutSeconds());
        assertNull(definition.getTimeoutPolicy());
        assertNotSame(definition.getTasks().getFirst(), snapshot.getTasks().getFirst());
        snapshot.getTasks().getFirst().getInputParameters().put("value", "changed");
        assertEquals(List.of("original"), definition.getTasks().getFirst().getInputParameters().get("value"));
        properties.getWorkflowExecution().setDefaultTimeoutSeconds(120);
        assertEquals(60, snapshot.getTimeoutSeconds());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void bothStartEntriesPersistTimeoutAndEndOnlyOpenTasks(boolean synchronous) {
        WorkflowExecutor executor = executor(definition());
        TaskModel done = task("done", TaskModel.Status.COMPLETED);
        TaskModel active = task("active", TaskModel.Status.IN_PROGRESS);
        TaskModel queued = task("queued", TaskModel.Status.SCHEDULED);
        when(dao.createWorkflow(any())).thenAnswer(invocation -> {
            WorkflowModel workflow = invocation.getArgument(0);
            workflow.setStartTime(System.currentTimeMillis() - 120_000);
            workflow.setTasks(List.of(done, active, queued));
            created.set(workflow);
            return workflow.getWorkflowId();
        });
        if (synchronous) {
            assertEquals(WorkflowModel.Status.TIMED_OUT, executor.startWorkflowSync(input()).getStatus());
        } else {
            assertNotNull(executor.startWorkflow(input()));
        }
        WorkflowModel workflow = created.get();
        assertEquals(WorkflowModel.Status.TIMED_OUT, workflow.getStatus());
        assertEquals(TaskModel.Status.COMPLETED, done.getStatus());
        assertEquals(TaskModel.Status.TIMED_OUT, active.getStatus());
        assertEquals(TaskModel.Status.CANCELED, queued.getStatus());
        assertEquals("active", workflow.getFailedTaskId());
        assertTrue(active.getEndTime() > 0);
        var order = inOrder(dao, listener);
        order.verify(dao).createWorkflow(workflow);
        order.verify(listener).onWorkflowStarted(workflow);
        order.verify(dao).updateTask(active);
        order.verify(dao).updateTask(queued);
        order.verify(dao).updateWorkflow(workflow);
        order.verify(listener).onWorkflowTimedOut(workflow);
        verifyNoInteractions(taskMapper);
    }

    private WorkflowExecutor executor(WorkflowDef definition) {
        when(metadata.getWorkflowDef("test", null)).thenReturn(definition);
        ParametersUtils parameters = new ParametersUtils(mapper);
        WorkflowCompletionRegistry completions = new WorkflowCompletionRegistry();
        return new WorkflowExecutorOps(new DeciderService(new IDGenerator(), parameters, registry,
                Map.of("HTTP", taskMapper)), registry, dao, metadata, new IDGenerator(),
                parameters, new InMemoryExecutionLockService(), null, completions,
                List.of(listener, completions), properties, mapper);
    }

    private static WorkflowDef definition() {
        return WorkflowDef.builder().route("test").tasks(List.of(WorkflowTask.builder()
                .name("remote").taskReferenceName("remote").type("HTTP").build())).build();
    }

    private static StartWorkflowInput input() {
        StartWorkflowInput input = new StartWorkflowInput();
        input.setRoute("test");
        return input;
    }

    private static TaskModel task(String id, TaskModel.Status status) {
        TaskModel task = new TaskModel();
        task.setTaskId(id);
        task.setReferenceTaskName(id);
        task.setTaskType("HTTP");
        task.setStatus(status);
        return task;
    }
}
