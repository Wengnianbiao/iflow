package com.helianhealth.iflow.core.model;

import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowTask;
import com.helianhealth.iflow.core.execution.mapper.TaskMapperContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RuntimeModelContractTest {

    @Test
    void statusSettersRetainPreviousStatusOnlyOnTransitions() {
        WorkflowModel workflow = new WorkflowModel();
        workflow.setStatus(WorkflowModel.Status.RUNNING);
        assertNull(workflow.getPreviousStatus());
        workflow.setStatus(WorkflowModel.Status.FAILED);
        workflow.setStatus(WorkflowModel.Status.FAILED);
        assertEquals(WorkflowModel.Status.RUNNING, workflow.getPreviousStatus());

        TaskModel task = new TaskModel();
        task.setStatus(TaskModel.Status.SCHEDULED);
        task.setStatus(TaskModel.Status.IN_PROGRESS);
        task.setStatus(TaskModel.Status.IN_PROGRESS);
        assertEquals(TaskModel.Status.SCHEDULED, task.getPreviousStatus());
        task.setTaskType("HTTP");
        assertEquals("HTTP", task.getTaskDefName());
        task.setTaskDefName("patient-request");
        assertEquals("patient-request", task.getTaskDefName());
    }

    @Test
    void copyingMapperContextPreservesAllFieldsWithoutMutatingOriginal() {
        WorkflowModel workflow = new WorkflowModel();
        WorkflowTask definition = new WorkflowTask();
        Map<String, Object> input = Map.of("patientId", "P001");
        TaskMapperContext original = TaskMapperContext.builder().workflowModel(workflow)
                .workflowTask(definition).taskInput(input).taskId("task-1")
                .retryCount(2).retryTaskId("task-0").forkTaskId("fork-1").build();

        TaskMapperContext copy = original.toBuilder().taskId("task-2").build();

        assertEquals("task-1", original.getTaskId());
        assertEquals("task-2", copy.getTaskId());
        assertSame(workflow, copy.getWorkflowModel());
        assertSame(definition, copy.getWorkflowTask());
        assertSame(input, copy.getTaskInput());
        assertEquals(2, copy.getRetryCount());
        assertEquals("task-0", copy.getRetryTaskId());
        assertEquals("fork-1", copy.getForkTaskId());
    }
}
