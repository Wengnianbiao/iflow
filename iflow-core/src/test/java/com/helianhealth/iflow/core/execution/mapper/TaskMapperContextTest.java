package com.helianhealth.iflow.core.execution.mapper;

import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowDef;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowTask;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TaskMapperContextTest {

    @Test
    void carriesWorkflowIdentityRetryLineageAndForkScopeIntoTask() {
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("workflow-1");
        workflow.setWorkflowDefinition(WorkflowDef.builder().route("patient-sync").version(2).build());
        workflow.setCorrelationId("patient-1");
        WorkflowTask definition = WorkflowTask.builder().name("request").type("HTTP")
                .taskReferenceName("request_ref").build();
        TaskMapperContext context = TaskMapperContext.builder().workflowModel(workflow)
                .workflowTask(definition).taskId("task-2").retryCount(1)
                .retryTaskId("task-1").forkTaskId("fork-1").build();
        long before = System.currentTimeMillis();

        TaskModel task = context.createTaskModel();

        assertEquals("workflow-1", task.getWorkflowInstanceId());
        assertEquals("patient-sync", task.getWorkflowType());
        assertEquals("patient-1", task.getCorrelationId());
        assertEquals("task-2", task.getTaskId());
        assertEquals("request_ref", task.getReferenceTaskName());
        assertEquals("request", task.getTaskDefName());
        assertEquals("HTTP", task.getTaskType());
        assertEquals("task-1", task.getRetriedTaskId());
        assertEquals("fork-1", task.getForkTaskId());
        assertSame(definition, task.getWorkflowTask());
        assertTrue(task.getScheduledTime() >= before);
        assertTrue(task.getScheduledTime() <= System.currentTimeMillis());
    }

    @Test
    void createsIndependentTaskInstancesAndLeavesAbsentContextUnset() {
        TaskMapperContext context = TaskMapperContext.builder().workflowModel(new WorkflowModel())
                .workflowTask(WorkflowTask.builder().name("noop").type("NOOP")
                        .taskReferenceName("noop_ref").build()).taskId("task-1").build();

        TaskModel first = context.createTaskModel();
        TaskModel second = context.createTaskModel();
        first.getInputData().put("value", "changed");

        assertNotSame(first, second);
        assertTrue(second.getInputData().isEmpty());
        assertNull(first.getRetriedTaskId());
        assertNull(first.getForkTaskId());
        assertNull(first.getWorkflowType());
    }
}
