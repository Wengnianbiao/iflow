package com.helianhealth.iflow.core.execution.tasks;

import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowTask;
import com.helianhealth.iflow.core.dal.ExecutionDAOFacade;
import com.helianhealth.iflow.core.execution.evaluators.Evaluator;
import com.helianhealth.iflow.core.execution.evaluators.GraalJSEvaluator;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import com.helianhealth.iflow.core.utils.ParametersUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DoWhileTaskTest {

    private final ParametersUtils parameters = new ParametersUtils();

    @Test
    void missingDefinitionOrEmptyLoopBodyFailsWithTerminalError() {
        TaskModel emptyLoop = loop(WorkflowTask.builder().type("DO_WHILE").build(), 0);
        new DoWhile(Map.of(), parameters).execute(workflow(emptyLoop), emptyLoop, null);
        assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, emptyLoop.getStatus());
        assertTrue(emptyLoop.getReasonForInCompletion().contains("loopOver"));
        TaskModel withoutDefinition = loop(null, 0);
        new DoWhile(Map.of(), parameters).execute(workflow(withoutDefinition), withoutDefinition, null);
        assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, withoutDefinition.getStatus());
    }

    @Test
    void conditionLoopRequiresNonBlankCondition() {
        WorkflowTask definition = definition();
        definition.setLoopCondition(" ");
        TaskModel loop = loop(definition, 0);

        new DoWhile(Map.of(), parameters).execute(workflow(loop), loop, null);

        assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, loop.getStatus());
        assertTrue(loop.getReasonForInCompletion().contains("loopCondition is required"));
    }

    @Test
    void firstExecutionInitializesIterationWithoutEvaluatingCondition() {
        TaskModel loop = loop(definition(), 0);
        Evaluator shouldNotRun = (expression, input) -> { throw new AssertionError("body must execute first"); };

        new DoWhile(Map.of(GraalJSEvaluator.NAME, shouldNotRun), parameters)
                .execute(workflow(loop), loop, null);

        assertEquals(TaskModel.Status.IN_PROGRESS, loop.getStatus());
        assertEquals(1, loop.getIteration());
        assertEquals(1, loop.outputAsMap().get("iteration"));
    }

    @Test
    void unfinishedBodyDoesNotEvaluateConditionOrAdvanceIteration() {
        TaskModel loop = loop(definition(), 1);
        TaskModel running = child("body", 1, TaskModel.Status.IN_PROGRESS, Map.of());
        Evaluator shouldNotRun = (expression, input) -> { throw new AssertionError("body is unfinished"); };

        new DoWhile(Map.of(GraalJSEvaluator.NAME, shouldNotRun), parameters)
                .execute(workflow(loop, running), loop, null);

        assertEquals(TaskModel.Status.IN_PROGRESS, loop.getStatus());
        assertEquals(1, loop.getIteration());
    }

    @Test
    void waitsForUnscheduledTailOfMultiTaskBody() {
        WorkflowTask definition = definition();
        definition.setLoopOver(List.of(childDefinition("body"), childDefinition("tail")));
        TaskModel loop = loop(definition, 1);
        TaskModel first = child("body", 1, TaskModel.Status.COMPLETED, 7);
        Evaluator shouldNotRun = (expression, input) -> { throw new AssertionError("tail is not scheduled"); };

        new DoWhile(Map.of(GraalJSEvaluator.NAME, shouldNotRun), parameters)
                .execute(workflow(loop, first), loop, null);

        assertEquals(TaskModel.Status.IN_PROGRESS, loop.getStatus());
        assertEquals(1, loop.getIteration());
    }

    @Test
    void failedBodyCarriesReasonAndRawOutputWithoutEvaluatingCondition() {
        TaskModel loop = loop(definition(), 1);
        TaskModel failed = child("body", 1, TaskModel.Status.FAILED, Map.of("error", "business error"));
        failed.setReasonForInCompletion("body failed");

        new DoWhile(Map.of(), parameters).execute(workflow(loop, failed), loop, null);

        assertEquals(TaskModel.Status.FAILED, loop.getStatus());
        assertTrue(loop.getReasonForInCompletion().contains("body failed"));
        assertEquals(Map.of("body", Map.of("error", "business error")), loop.outputAsMap().get("1"));
    }

    @Test
    void successfulLatestRetryReplacesEarlierFailureWithinIteration() {
        TaskModel loop = loop(definition(), 1);
        TaskModel failed = child("body", 1, TaskModel.Status.FAILED, "old");
        TaskModel retried = child("body", 1, TaskModel.Status.COMPLETED, "new");
        retried.setRetryCount(1);

        new DoWhile(Map.of(GraalJSEvaluator.NAME, (Evaluator) (expression, input) -> false), parameters)
                .execute(workflow(loop, failed, retried), loop, null);

        assertEquals(TaskModel.Status.COMPLETED, loop.getStatus());
        assertEquals(Map.of("body", "new"), loop.outputAsMap().get("1"));
    }

    @ParameterizedTest
    @MethodSource("conditionValues")
    void coercesConditionResultAndAdvancesOnlyWhenTrue(Object conditionValue, boolean shouldContinue) {
        TaskModel loop = loop(definition(), 1);
        TaskModel body = child("body", 1, TaskModel.Status.COMPLETED, List.of(7));
        Evaluator evaluator = (expression, input) -> conditionValue;

        new DoWhile(Map.of(GraalJSEvaluator.NAME, evaluator), parameters)
                .execute(workflow(loop, body), loop, null);

        assertEquals(shouldContinue ? TaskModel.Status.IN_PROGRESS : TaskModel.Status.COMPLETED, loop.getStatus());
        assertEquals(shouldContinue ? 2 : 1, loop.getIteration());
        assertEquals(Map.of("body", List.of(7)), loop.outputAsMap().get("1"));
    }

    static Stream<Arguments> conditionValues() {
        return Stream.of(Arguments.of(true, true), Arguments.of(false, false),
                Arguments.of(1, true), Arguments.of(0, false), Arguments.of("true", true),
                Arguments.of("false", false), Arguments.of(null, false));
    }

    @Test
    void conditionSeesResolvedInputsCurrentBodyOutputAndWorkflowContext() {
        WorkflowTask definition = definition();
        definition.setEvaluatorType("custom");
        definition.setInputParameters(Map.of("limit", "${workflow.input.limit}"));
        TaskModel loop = loop(definition, 1);
        TaskModel body = child("body", 1, TaskModel.Status.COMPLETED, Map.of("count", 3));
        WorkflowModel workflow = workflow(loop, body);
        workflow.setInput(Map.of("limit", 3));
        AtomicReference<Map<String, Object>> seen = new AtomicReference<>();
        Evaluator evaluator = (expression, input) -> { seen.set(input); return false; };

        new DoWhile(Map.of("custom", evaluator), parameters).execute(workflow, loop, null);

        assertEquals(TaskModel.Status.COMPLETED, loop.getStatus());
        assertEquals(3, seen.get().get("limit"));
        assertEquals(1, seen.get().get("iteration"));
        assertEquals(Map.of("count", 3), seen.get().get("body"));
        assertEquals(workflow.getInput(), ((Map<?, ?>) seen.get().get("workflow")).get("input"));
    }

    @Test
    void conditionFailureIsTerminalAndReportsRootCause() {
        TaskModel loop = loop(definition(), 1);
        TaskModel body = child("body", 1, TaskModel.Status.COMPLETED, "done");
        Evaluator failure = (expression, input) -> {
            throw new IllegalArgumentException("wrapper", new IllegalStateException("root failure"));
        };

        new DoWhile(Map.of(GraalJSEvaluator.NAME, failure), parameters)
                .execute(workflow(loop, body), loop, null);

        assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, loop.getStatus());
        assertTrue(loop.getReasonForInCompletion().contains("root failure"));
        assertEquals(Map.of("body", "done"), loop.outputAsMap().get("1"));
    }

    @Test
    void emptyItemsCompleteWithoutSchedulingBody() {
        WorkflowTask definition = definition();
        definition.setLoopCondition(null);
        definition.setItems("${workflow.input.items}");
        TaskModel loop = loop(definition, 0);
        WorkflowModel workflow = workflow(loop);
        workflow.setInput(Map.of("items", List.of()));

        new DoWhile(Map.of(), parameters).execute(workflow, loop, null);

        assertEquals(TaskModel.Status.COMPLETED, loop.getStatus());
        assertEquals(0, loop.outputAsMap().get("iteration"));
    }

    @Test
    void listLoopInjectsEachTypedItemAndCompletesAfterLastItem() {
        WorkflowTask definition = definition();
        definition.setLoopCondition(null);
        definition.setInputParameters(Map.of("_items", List.of(Map.of("id", 1), Map.of("id", 2))));
        TaskModel loop = loop(definition, 0);
        WorkflowModel workflow = workflow(loop);
        DoWhile task = new DoWhile(Map.of(), parameters);

        task.execute(workflow, loop, null);
        assertEquals(0, loop.outputAsMap().get("loopIndex"));
        assertEquals(Map.of("id", 1), loop.outputAsMap().get("loopItem"));
        workflow.getTasks().add(child("body", 1, TaskModel.Status.COMPLETED, "first"));
        task.execute(workflow, loop, null);
        assertEquals(2, loop.getIteration());
        assertEquals(1, loop.outputAsMap().get("loopIndex"));
        assertEquals(Map.of("id", 2), loop.outputAsMap().get("loopItem"));
        workflow.getTasks().add(child("body", 2, TaskModel.Status.COMPLETED, "second"));
        task.execute(workflow, loop, null);

        assertEquals(TaskModel.Status.COMPLETED, loop.getStatus());
        assertEquals(Map.of("body", "first"), loop.outputAsMap().get("1"));
        assertEquals(Map.of("body", "second"), loop.outputAsMap().get("2"));
    }

    @Test
    void retentionRemovesOnlyOldLoopTasksAndOutput() {
        WorkflowTask definition = definition();
        definition.setInputParameters(Map.of("keepLastN", 1));
        TaskModel loop = loop(definition, 2);
        loop.addOutput("1", Map.of("body", "old"));
        TaskModel oldBody = child("body", 1, TaskModel.Status.COMPLETED, "old");
        TaskModel current = child("body", 2, TaskModel.Status.COMPLETED, "current");
        TaskModel unrelated = child("unrelated", 1, TaskModel.Status.COMPLETED, "unrelated");
        WorkflowModel workflow = workflow(loop, oldBody, current, unrelated);
        ExecutionDAOFacade dao = mock(ExecutionDAOFacade.class);
        DoWhile task = new DoWhile(Map.of(GraalJSEvaluator.NAME, (Evaluator) (expression, input) -> false), parameters, dao);

        task.execute(workflow, loop, null);

        assertEquals(TaskModel.Status.COMPLETED, loop.getStatus());
        assertFalse(loop.outputAsMap().containsKey("1"));
        assertEquals(Map.of("body", "current"), loop.outputAsMap().get("2"));
        assertEquals(List.of(loop, current, unrelated), workflow.getTasks());
        verify(dao).removeTask(oldBody.getTaskId());
        verifyNoMoreInteractions(dao);
    }

    @Test
    void cancelMarksLoopCanceled() {
        TaskModel loop = loop(definition(), 1);

        new DoWhile(Map.of(), parameters).cancel(workflow(loop), loop, null);

        assertEquals(TaskModel.Status.CANCELED, loop.getStatus());
    }

    private WorkflowTask definition() {
        return WorkflowTask.builder().type("DO_WHILE").taskReferenceName("loop")
                .loopCondition("iteration < limit").loopOver(List.of(childDefinition("body"))).build();
    }

    private WorkflowTask childDefinition(String ref) {
        return WorkflowTask.builder().name(ref).type("NOOP").taskReferenceName(ref).build();
    }

    private TaskModel loop(WorkflowTask definition, int iteration) {
        TaskModel task = new TaskModel();
        task.setTaskId("loop-1");
        task.setReferenceTaskName("loop");
        task.setWorkflowTask(definition);
        task.setStatus(TaskModel.Status.IN_PROGRESS);
        task.setIteration(iteration);
        return task;
    }

    private TaskModel child(String ref, int iteration, TaskModel.Status status, Object output) {
        TaskModel task = new TaskModel();
        task.setTaskId(ref + "-" + iteration);
        task.setReferenceTaskName(ref + "__" + iteration);
        task.setIteration(iteration);
        task.setStatus(status);
        task.setOutputData(output);
        return task;
    }

    private WorkflowModel workflow(TaskModel... tasks) {
        WorkflowModel workflow = new WorkflowModel();
        workflow.setTasks(new ArrayList<>(List.of(tasks)));
        return workflow;
    }
}
