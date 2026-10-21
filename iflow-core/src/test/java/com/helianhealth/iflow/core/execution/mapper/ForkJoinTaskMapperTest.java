package com.helianhealth.iflow.core.execution.mapper;

import com.helianhealth.iflow.common.domain.metadata.task.TaskType;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowDef;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowTask;
import com.helianhealth.iflow.core.exception.TerminateWorkflowException;
import com.helianhealth.iflow.core.execution.DeciderService;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ForkJoinTaskMapperTest {

    private final DeciderService decider = mock(DeciderService.class);

    @Test
    void mapsBranchHeadsAndAssignsParentScopeWhileJoinRetainsOuterScope() {
        WorkflowTask left = definition("left", TaskType.TASK_TYPE_NOOP);
        WorkflowTask leftNext = definition("left_next", TaskType.TASK_TYPE_NOOP);
        WorkflowTask right = definition("right", TaskType.TASK_TYPE_NOOP);
        WorkflowTask fork = definition("fork_ref", TaskType.TASK_TYPE_FORK_JOIN);
        fork.setForkTasks(List.of(List.of(left, leftNext), List.of(right)));
        WorkflowTask join = definition("join_ref", TaskType.TASK_TYPE_JOIN);
        TaskMapperContext context = context(fork, List.of(fork, join));
        TaskModel leftRuntime = new TaskModel();
        TaskModel rightRuntime = new TaskModel();
        TaskModel joinRuntime = new TaskModel();
        when(decider.getTasksToBeScheduled(context.getWorkflowModel(), left, 3, null, "fork-1"))
                .thenReturn(List.of(leftRuntime));
        when(decider.getTasksToBeScheduled(context.getWorkflowModel(), right, 3, null, "fork-1"))
                .thenReturn(List.of(rightRuntime));
        when(decider.getTasksToBeScheduled(context.getWorkflowModel(), join, 3, null, "outer-fork"))
                .thenReturn(List.of(joinRuntime));

        List<TaskModel> mapped = new ForkJoinTaskMapper().getMappedTasks(context);

        assertEquals(TaskType.TASK_TYPE_FORK_JOIN, new ForkJoinTaskMapper().getTaskType());
        assertEquals(4, mapped.size());
        assertEquals(TaskType.TASK_TYPE_FORK, mapped.get(0).getTaskType());
        assertEquals(TaskType.TASK_TYPE_FORK, mapped.get(0).getTaskDefName());
        assertEquals(TaskModel.Status.SCHEDULED, mapped.get(0).getStatus());
        assertEquals(Map.of("payload", 42), mapped.get(0).getInputData());
        assertEquals("fork-1", leftRuntime.getParentTaskId());
        assertEquals("fork-1", rightRuntime.getParentTaskId());
        assertNull(joinRuntime.getParentTaskId());
        assertEquals(List.of(leftRuntime, rightRuntime, joinRuntime), mapped.subList(1, 4));
        verify(decider).getTasksToBeScheduled(context.getWorkflowModel(), left, 3, null, "fork-1");
        verify(decider).getTasksToBeScheduled(context.getWorkflowModel(), right, 3, null, "fork-1");
        verify(decider).getTasksToBeScheduled(context.getWorkflowModel(), join, 3, null, "outer-fork");
        verifyNoMoreInteractions(decider);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectsMissingOrNonJoinSuccessor(boolean hasSuccessor) {
        WorkflowTask fork = definition("fork_ref", TaskType.TASK_TYPE_FORK_JOIN);
        List<WorkflowTask> tasks = hasSuccessor
                ? List.of(fork, definition("next", TaskType.TASK_TYPE_NOOP)) : List.of(fork);
        TaskMapperContext context = context(fork, tasks);

        TerminateWorkflowException error = assertThrows(TerminateWorkflowException.class,
                () -> new ForkJoinTaskMapper().getMappedTasks(context));

        assertTrue(error.getMessage().contains("not followed by a join task"));
        verifyNoInteractions(decider);
    }

    private WorkflowTask definition(String ref, String type) {
        return WorkflowTask.builder().name(ref).type(type).taskReferenceName(ref).build();
    }

    private TaskMapperContext context(WorkflowTask fork, List<WorkflowTask> tasks) {
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("workflow-1");
        workflow.setWorkflowDefinition(WorkflowDef.builder().route("fork-test").tasks(tasks).build());
        return TaskMapperContext.builder().workflowModel(workflow).workflowTask(fork)
                .taskInput(Map.of("payload", 42)).taskId("fork-1").retryCount(3)
                .forkTaskId("outer-fork").deciderService(decider).build();
    }
}
