package com.helianhealth.iflow.core.execution.mapper;

import com.helianhealth.iflow.common.domain.metadata.task.TaskType;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowTask;
import com.helianhealth.iflow.core.exception.TerminateWorkflowException;
import com.helianhealth.iflow.core.execution.DeciderService;
import com.helianhealth.iflow.core.execution.evaluators.Evaluator;
import com.helianhealth.iflow.core.execution.evaluators.ValueParamEvaluator;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import com.helianhealth.iflow.core.utils.ParametersUtils;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SwitchTaskMapperTest {

    private final DeciderService decider = mock(DeciderService.class);
    private final ParametersUtils parameters = new ParametersUtils();

    @Test
    void schedulesOnlyFirstTaskOfMatchedCaseAndCarriesRetryAndForkScope() {
        WorkflowTask first = child("first");
        WorkflowTask second = child("second");
        TaskMapperContext context = context("route", Map.of("route", "accepted"));
        context.getWorkflowTask().setDecisionCases(Map.of("accepted", List.of(first, second)));
        context.getWorkflowTask().setDefaultCase(List.of(child("fallback")));
        TaskModel branch = new TaskModel();
        when(decider.getTasksToBeScheduled(context.getWorkflowModel(), first, 2, "previous", "outer-fork"))
                .thenReturn(List.of(branch));

        List<TaskModel> mapped = mapper().getMappedTasks(context);

        assertEquals(TaskType.TASK_TYPE_SWITCH, mapper().getTaskType());
        assertEquals(List.of("accepted"), mapped.get(0).outputAsMap().get("evaluationResult"));
        assertEquals("accepted", mapped.get(0).outputAsMap().get("selectedCase"));
        assertEquals("accepted", mapped.get(0).getInputData().get("case"));
        assertEquals("true", mapped.get(0).getInputData().get("hasChildren"));
        assertEquals(TaskModel.Status.IN_PROGRESS, mapped.get(0).getStatus());
        assertSame(branch, mapped.get(1));
        verify(decider).getTasksToBeScheduled(context.getWorkflowModel(), first, 2, "previous", "outer-fork");
        verifyNoMoreInteractions(decider);
        assertEquals(Map.of("route", "accepted"), context.getTaskInput());
    }

    @Test
    void unmatchedCaseSchedulesDefaultBranch() {
        WorkflowTask fallback = child("fallback");
        TaskMapperContext context = context("route", Map.of("route", "missing"));
        context.getWorkflowTask().setDecisionCases(Map.of("accepted", List.of(child("accepted"))));
        context.getWorkflowTask().setDefaultCase(List.of(fallback));
        when(decider.getTasksToBeScheduled(context.getWorkflowModel(), fallback, 2, "previous", "outer-fork"))
                .thenReturn(List.of(new TaskModel()));

        List<TaskModel> mapped = mapper().getMappedTasks(context);

        assertEquals(2, mapped.size());
        assertEquals("missing", mapped.get(0).outputAsMap().get("selectedCase"));
        verify(decider).getTasksToBeScheduled(context.getWorkflowModel(), fallback, 2, "previous", "outer-fork");
    }

    @Test
    void emptyMatchedCaseUsesDefaultBranch() {
        WorkflowTask fallback = child("fallback");
        TaskMapperContext context = context("route", Map.of("route", "empty"));
        context.getWorkflowTask().setDecisionCases(Map.of("empty", List.of()));
        context.getWorkflowTask().setDefaultCase(List.of(fallback));
        when(decider.getTasksToBeScheduled(context.getWorkflowModel(), fallback, 2, "previous", "outer-fork"))
                .thenReturn(List.of());

        mapper().getMappedTasks(context);

        verify(decider).getTasksToBeScheduled(context.getWorkflowModel(), fallback, 2, "previous", "outer-fork");
    }

    @Test
    void missingValueWithNoDefaultCreatesOnlySwitchTask() {
        TaskMapperContext context = context("missing", Map.of());

        List<TaskModel> mapped = mapper().getMappedTasks(context);

        assertEquals(1, mapped.size());
        assertEquals("null", mapped.get(0).outputAsMap().get("selectedCase"));
        assertFalse(mapped.get(0).getInputData().containsKey("hasChildren"));
        verifyNoInteractions(decider);
    }

    @Test
    void directOrchestrationExpressionResolvesTypedValueBeforeSelectingCase() {
        TaskMapperContext context = context("${workflow.input.code}", Map.of());
        context.getWorkflowModel().setInput(Map.of("code", 7));
        Evaluator shouldNotRun = (expression, input) -> { throw new AssertionError("key evaluator must be bypassed"); };
        SwitchTaskMapper mapper = new SwitchTaskMapper(Map.of(ValueParamEvaluator.NAME, shouldNotRun), parameters);

        TaskModel task = mapper.getMappedTasks(context).get(0);

        assertEquals("7", task.outputAsMap().get("selectedCase"));
        verifyNoInteractions(decider);
    }

    @Test
    void evaluatorFailureProducesFailedTaskWithoutSchedulingBranches() {
        TaskMapperContext context = context("route", Map.of("route", "accepted"));
        Evaluator failure = (expression, input) -> { throw new IllegalArgumentException("invalid decision"); };
        SwitchTaskMapper mapper = new SwitchTaskMapper(Map.of(ValueParamEvaluator.NAME, failure), parameters);

        TaskModel task = mapper.getMappedTasks(context).get(0);

        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertEquals("invalid decision", task.getReasonForInCompletion());
        assertTrue(task.outputAsMap().isEmpty());
        assertEquals(context.getTaskInput(), task.getInputData());
        verifyNoInteractions(decider);
    }

    @Test
    void unknownEvaluatorTerminatesMappingWithUsefulDiagnostic() {
        TaskMapperContext context = context("route", Map.of());
        context.getWorkflowTask().setEvaluatorType("unknown");

        TerminateWorkflowException error = assertThrows(TerminateWorkflowException.class,
                () -> mapper().getMappedTasks(context));

        assertTrue(error.getMessage().contains("unknown"));
        verifyNoInteractions(decider);
    }

    @Test
    void rejectsMissingPreResolvedContextInput() {
        TaskMapperContext context = context("route", Map.of()).toBuilder().taskInput(null).build();

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> mapper().getMappedTasks(context));

        assertTrue(error.getMessage().contains("pre-resolved taskInput"));
        verifyNoInteractions(decider);
    }

    private SwitchTaskMapper mapper() {
        return new SwitchTaskMapper(Map.of(ValueParamEvaluator.NAME, new ValueParamEvaluator()), parameters);
    }

    private WorkflowTask child(String ref) {
        return WorkflowTask.builder().name(ref).taskReferenceName(ref).type(TaskType.TASK_TYPE_NOOP).build();
    }

    private TaskMapperContext context(String expression, Map<String, Object> input) {
        WorkflowTask definition = WorkflowTask.builder().name("switch").taskReferenceName("switch_ref")
                .type(TaskType.TASK_TYPE_SWITCH).evaluatorType(ValueParamEvaluator.NAME).expression(expression).build();
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("workflow-1");
        return TaskMapperContext.builder().workflowModel(workflow).workflowTask(definition)
                .taskInput(input).taskId("switch-1").retryCount(2).retryTaskId("previous")
                .forkTaskId("outer-fork").deciderService(decider).build();
    }
}
