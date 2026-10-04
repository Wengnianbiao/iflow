package com.helianhealth.iflow.core.execution.mapper;

import com.helianhealth.iflow.common.domain.metadata.task.TaskType;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowDef;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowTask;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import com.helianhealth.iflow.core.utils.ParametersUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BasicTaskMappersTest {

    private final ParametersUtils parameters = new ParametersUtils();

    @Test
    void httpResolvesTypedWorkflowInputsAndRetainsLocalExpressions() {
        Map<String, Object> body = Map.of("id", 42, "text", "${business.literal}");
        TaskMapperContext context = context(TaskType.TASK_TYPE_HTTP, Map.of(
                "uri", "${workflow.input.endpoint}",
                "body", "${workflow.input.body}",
                "localPath", "$.body.id"));
        context.getWorkflowModel().setInput(Map.of("endpoint", "http://example.test", "body", body));

        TaskModel task = new HttpTaskMapper(parameters).getMappedTasks(context).get(0);

        assertEquals(TaskType.TASK_TYPE_HTTP, new HttpTaskMapper(parameters).getTaskType());
        assertEquals(Map.of("uri", "http://example.test", "body", body, "localPath", "$.body.id"),
                task.getInputData());
        assertEquals(TaskModel.Status.SCHEDULED, task.getStatus());
        assertEquals(2, task.getRetryCount());
        assertEquals("${workflow.input.body}", context.getWorkflowTask().getInputParameters().get("body"));
    }

    @Test
    void inlineResolvesParametersBeforeStartingTask() {
        TaskMapperContext context = context(TaskType.TASK_TYPE_INLINE, Map.of(
                "expression", "$.value + 1", "evaluatorType", "graaljs", "value", "${workflow.input.value}"));
        context.getWorkflowModel().setInput(Map.of("value", 41));

        TaskModel task = new InlineTaskMapper(parameters).getMappedTasks(context).get(0);

        assertEquals(TaskType.TASK_TYPE_INLINE, new InlineTaskMapper(parameters).getTaskType());
        assertEquals(Map.of("expression", "$.value + 1", "evaluatorType", "graaljs", "value", 41),
                task.getInputData());
        assertStarted(task, TaskType.TASK_TYPE_INLINE);
    }

    @Test
    void joinUsesDefinitionJoinReferencesRatherThanGenericTaskInput() {
        TaskMapperContext context = context(TaskType.TASK_TYPE_JOIN, Map.of("ignored", true));
        context.getWorkflowTask().setJoinOn(List.of("left", "right"));

        TaskModel task = new JoinTaskMapper().getMappedTasks(context).get(0);

        assertEquals(TaskType.TASK_TYPE_JOIN, new JoinTaskMapper().getTaskType());
        assertEquals(Map.of("joinOn", List.of("left", "right")), task.getInputData());
        assertEquals(TaskType.TASK_TYPE_JOIN, task.getTaskDefName());
        assertStarted(task, TaskType.TASK_TYPE_JOIN);
    }

    @Test
    void exclusiveJoinIncludesConfiguredFallbackReferences() {
        TaskMapperContext context = context(TaskType.TASK_TYPE_EXCLUSIVE_JOIN, Map.of());
        context.getWorkflowTask().setJoinOn(List.of("selected"));
        context.getWorkflowTask().setDefaultExclusiveJoinTask(List.of("fallback"));

        TaskModel task = new ExclusiveJoinTaskMapper().getMappedTasks(context).get(0);

        assertEquals(TaskType.TASK_TYPE_EXCLUSIVE_JOIN, new ExclusiveJoinTaskMapper().getTaskType());
        assertEquals(Map.of("joinOn", List.of("selected"),
                "defaultExclusiveJoinTask", List.of("fallback")), task.getInputData());
        assertStarted(task, TaskType.TASK_TYPE_EXCLUSIVE_JOIN);
        context.getWorkflowTask().setDefaultExclusiveJoinTask(null);
        TaskModel withoutFallback = new ExclusiveJoinTaskMapper().getMappedTasks(context).get(0);
        assertFalse(withoutFallback.getInputData().containsKey("defaultExclusiveJoinTask"));
    }

    @Test
    void terminateResolvesInputWhenContextHasNoResolvedInput() {
        TaskMapperContext context = context(TaskType.TASK_TYPE_TERMINATE, Map.of(
                "terminationStatus", "COMPLETED", "workflowOutput", "${workflow.input.result}"));
        context.getWorkflowModel().setInput(Map.of("result", List.of(1, 2)));

        TaskModel task = new TerminateTaskMapper(parameters).getMappedTasks(context).get(0);

        assertEquals(TaskType.TASK_TYPE_TERMINATE, new TerminateTaskMapper(parameters).getTaskType());
        assertEquals(Map.of("terminationStatus", "COMPLETED", "workflowOutput", List.of(1, 2)),
                task.getInputData());
        assertStarted(task, TaskType.TASK_TYPE_TERMINATE);
    }

    @Test
    void terminatePreservesAlreadyResolvedBusinessText() {
        Map<String, Object> resolved = Map.of("workflowOutput", "${business.literal}");
        TaskMapperContext context = context(TaskType.TASK_TYPE_TERMINATE, Map.of("workflowOutput", "wrong"))
                .toBuilder().taskInput(resolved).build();

        TaskModel task = new TerminateTaskMapper(parameters).getMappedTasks(context).get(0);

        assertEquals(resolved, task.getInputData());
    }

    @Test
    void doWhileResolvesInputAndKeepsRetryCount() {
        TaskMapperContext context = context(TaskType.TASK_TYPE_DO_WHILE, Map.of("limit", "${workflow.input.limit}"));
        context.getWorkflowModel().setInput(Map.of("limit", 3));

        TaskModel task = new DoWhileTaskMapper(parameters).getMappedTasks(context).get(0);

        assertEquals(TaskType.TASK_TYPE_DO_WHILE, new DoWhileTaskMapper(parameters).getTaskType());
        assertEquals(Map.of("limit", 3), task.getInputData());
        assertEquals(2, task.getRetryCount());
        assertStarted(task, TaskType.TASK_TYPE_DO_WHILE);
    }

    @ParameterizedTest
    @EnumSource(value = TaskModel.Status.class, names = {"COMPLETED", "FAILED", "CANCELED"})
    void doWhileDoesNotRescheduleTerminalLoop(TaskModel.Status status) {
        TaskMapperContext context = context(TaskType.TASK_TYPE_DO_WHILE, Map.of());
        TaskModel previous = context.createTaskModel();
        previous.setStatus(status);
        context.getWorkflowModel().setTasks(List.of(previous));

        assertTrue(new DoWhileTaskMapper(parameters).getMappedTasks(context).isEmpty());
    }

    @Test
    void noopStartsWithoutAddingDefinitionInputToRuntimeInput() {
        TaskMapperContext context = context(TaskType.TASK_TYPE_NOOP, Map.of("unused", true));

        TaskModel task = new NoopTaskMapper().getMappedTasks(context).get(0);

        assertEquals(TaskType.TASK_TYPE_NOOP, new NoopTaskMapper().getTaskType());
        assertTrue(task.getInputData().isEmpty());
        assertStarted(task, TaskType.TASK_TYPE_NOOP);
    }

    private void assertStarted(TaskModel task, String type) {
        assertEquals(type, task.getTaskType());
        assertEquals(TaskModel.Status.IN_PROGRESS, task.getStatus());
        assertTrue(task.getStartTime() > 0);
        assertEquals("task-1", task.getTaskId());
        assertEquals("ref", task.getReferenceTaskName());
    }

    private TaskMapperContext context(String type, Map<String, Object> input) {
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("workflow-1");
        workflow.setWorkflowDefinition(WorkflowDef.builder().name("test-workflow").build());
        WorkflowTask definition = WorkflowTask.builder().name("task").type(type)
                .taskReferenceName("ref").inputParameters(input).build();
        return TaskMapperContext.builder().workflowModel(workflow).workflowTask(definition)
                .taskId("task-1").retryCount(2).build();
    }
}
