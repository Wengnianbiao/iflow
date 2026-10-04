package com.helianhealth.iflow.core.execution.mapper;

import com.helianhealth.iflow.common.domain.metadata.task.TaskType;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowTask;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void preservesNullAndLiteralBusinessTextWithoutResolvingAgain() {
        Map<String, Object> input = new HashMap<>();
        input.put("function", "JSON_STRINGIFY");
        input.put("value", null);
        input.put("literal", "${business.value}");
        TaskModel task = new FunctionTaskMapper().getMappedTasks(context(input)).get(0);

        assertEquals(input, task.getInputData());
        task.getInputData().put("literal", "changed");
        assertEquals("${business.value}", input.get("literal"));
        assertTrue(task.getInputData().containsKey("value"));
    }

    @Test
    void createsMutableEmptyInputWhenPreparedInputIsMissing() {
        FunctionTaskMapper mapper = new FunctionTaskMapper();
        TaskModel task = mapper.getMappedTasks(context(null)).get(0);

        assertEquals(TaskType.TASK_TYPE_FUNCTION, mapper.getTaskType());
        assertTrue(task.getInputData().isEmpty());
        task.getInputData().put("value", 42);
        assertEquals(42, task.getInputData().get("value"));
    }

    private TaskMapperContext context(Map<String, Object> input) {
        return TaskMapperContext.builder()
                .workflowModel(new WorkflowModel())
                .workflowTask(WorkflowTask.builder().name("function")
                        .taskReferenceName("function_ref").type(TaskType.TASK_TYPE_FUNCTION).build())
                .taskId("function-1").taskInput(input).build();
    }
}
