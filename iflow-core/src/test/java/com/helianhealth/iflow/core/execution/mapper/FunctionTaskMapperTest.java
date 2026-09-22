package com.helianhealth.iflow.core.execution.mapper;

import com.helianhealth.iflow.common.domain.metadata.task.TaskType;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowTask;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class FunctionTaskMapperTest {

    @Test
    void mapsResolvedFunctionInput() {
        Map<String, Object> resolvedInput =
                Map.of("function", "JSON_PARSE", "value", "{\"code\":\"200\"}");
        WorkflowTask workflowTask =
                WorkflowTask.builder()
                        .name("parse request")
                        .taskReferenceName("parse_request_ref")
                        .type(TaskType.TASK_TYPE_FUNCTION)
                        .build();
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("workflow-1");
        TaskMapperContext context =
                TaskMapperContext.builder()
                        .workflowModel(workflow)
                        .workflowTask(workflowTask)
                        .taskInput(resolvedInput)
                        .taskId("task-1")
                        .build();

        TaskModel task = new FunctionTaskMapper().getMappedTasks(context).get(0);

        assertEquals(TaskType.TASK_TYPE_FUNCTION, task.getTaskType());
        assertEquals(TaskModel.Status.IN_PROGRESS, task.getStatus());
        assertEquals(resolvedInput, task.getInputData());
        assertNotSame(resolvedInput, task.getInputData());
    }
}
