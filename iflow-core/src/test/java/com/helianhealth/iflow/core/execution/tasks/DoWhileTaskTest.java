package com.helianhealth.iflow.core.execution.tasks;

import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowTask;
import com.helianhealth.iflow.core.dal.ExecutionDAOFacade;
import com.helianhealth.iflow.core.execution.WorkflowExecutor;
import com.helianhealth.iflow.core.execution.evaluators.Evaluator;
import com.helianhealth.iflow.core.execution.evaluators.GraalJSEvaluator;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import com.helianhealth.iflow.core.utils.ParametersUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.iflow.common.domain.run.Task;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DoWhileTaskTest {

    private final ParametersUtils parameters = new ParametersUtils(new ObjectMapper());
    private final WorkflowExecutor workflowExecutor = mock(WorkflowExecutor.class);
    private final ExecutionDAOFacade dao = mock(ExecutionDAOFacade.class);

    @ParameterizedTest
    @MethodSource("invalidDefinitions")
    void invalidDefinitionFailsBeforeScheduling(WorkflowTask definition) {
        TaskModel loop = loop(definition, 0);
        task().execute(workflow(loop), loop, workflowExecutor);
        assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, loop.getStatus());
        assertFalse(loop.getReasonForInCompletion().isBlank());
        assertEquals(0, loop.getIteration());
        verifyNoInteractions(workflowExecutor);
    }

    static Stream<Arguments> invalidDefinitions() {
        WorkflowTask missing = definition(); missing.setLoopCondition(null);
        WorkflowTask both = arrayDefinition(); both.setLoopCondition("false");
        WorkflowTask blank = definition(); blank.setLoopCondition(" ");
        WorkflowTask emptyArray = arrayDefinition(); emptyArray.setLoopItems("");
        WorkflowTask literal = arrayDefinition(); literal.setLoopItems("[1,2]");
        WorkflowTask embedded = arrayDefinition(); embedded.setLoopItems("prefix-${workflow.input.items}");
        WorkflowTask two = arrayDefinition(); two.setLoopItems("${workflow.input.a}${workflow.input.b}");
        WorkflowTask reserved = arrayDefinition(); reserved.setInputParameters(Map.of("loopItems", List.of(1)));
        WorkflowTask unsupported = definition(); unsupported.setEvaluatorType("value-param");
        WorkflowTask emptyBody = definition(); emptyBody.setLoopOver(List.of());
        return Stream.of(null, missing, both, blank, emptyArray, literal, embedded, two, reserved, unsupported, emptyBody)
                .map(value -> Arguments.of((Object) value));
    }

    @Test
    void firstExecutionStartsIterationWithoutEvaluatingCondition() {
        TaskModel loop = loop(definition(), 0);
        Evaluator shouldNotRun = (expression, input) -> { throw new AssertionError("body must execute first"); };

        WorkflowModel workflow = workflow(loop);
        task(shouldNotRun)
                .execute(workflow, loop, workflowExecutor);

        assertEquals(TaskModel.Status.IN_PROGRESS, loop.getStatus());
        assertEquals(1, loop.getIteration());
        assertEquals(1, loop.outputAsMap().get("iteration"));
        verify(workflowExecutor).scheduleNextIteration(loop, workflow);
    }

    @Test
    void unfinishedBodyDoesNotEvaluateConditionOrAdvanceIteration() {
        TaskModel loop = loop(definition(), 1);
        TaskModel running = child("body", 1, TaskModel.Status.IN_PROGRESS, Map.of());
        Evaluator shouldNotRun = (expression, input) -> { throw new AssertionError("body is unfinished"); };

        task(shouldNotRun)
                .execute(workflow(loop, running), loop, workflowExecutor);

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

        task(shouldNotRun)
                .execute(workflow(loop, first), loop, workflowExecutor);

        assertEquals(TaskModel.Status.IN_PROGRESS, loop.getStatus());
        assertEquals(1, loop.getIteration());
    }

    @Test
    void failedBodyCarriesReasonAndRawOutputWithoutEvaluatingCondition() {
        TaskModel loop = loop(definition(), 1);
        TaskModel failed = child("body", 1, TaskModel.Status.FAILED, Map.of("error", "business error"));
        failed.setReasonForInCompletion("body failed");

        task().execute(workflow(loop, failed), loop, workflowExecutor);

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

        task((expression, input) -> false)
                .execute(workflow(loop, failed, retried), loop, workflowExecutor);

        assertEquals(TaskModel.Status.COMPLETED, loop.getStatus());
        assertEquals(Map.of("body", "new"), loop.outputAsMap().get("1"));
    }

    @ParameterizedTest
    @MethodSource("conditionValues")
    void coercesConditionResultAndAdvancesOnlyWhenTrue(Object conditionValue, boolean shouldContinue) {
        TaskModel loop = loop(definition(), 1);
        TaskModel body = child("body", 1, TaskModel.Status.COMPLETED, List.of(7));
        Evaluator evaluator = (expression, input) -> conditionValue;

        task(evaluator)
                .execute(workflow(loop, body), loop, workflowExecutor);

        verify(workflowExecutor, times(shouldContinue ? 1 : 0)).scheduleNextIteration(eq(loop), any());
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
        definition.setInputParameters(Map.of("limit", "${workflow.input.limit}", "evaluatorType", "value-param"));
        TaskModel loop = loop(definition, 1);
        TaskModel body = child("body", 1, TaskModel.Status.COMPLETED, Map.of("count", 3));
        WorkflowModel workflow = workflow(loop, body);
        workflow.setInput(Map.of("limit", 3));
        AtomicReference<Map<String, Object>> seen = new AtomicReference<>();
        Evaluator evaluator = (expression, input) -> { seen.set(input); return false; };

        task(evaluator).execute(workflow, loop, workflowExecutor);

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

        task(failure)
                .execute(workflow(loop, body), loop, workflowExecutor);

        assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, loop.getStatus());
        assertTrue(loop.getReasonForInCompletion().contains("root failure"));
        assertEquals(Map.of("body", "done"), loop.outputAsMap().get("1"));
    }

    @Test
    void emptyItemsCompleteWithoutSchedulingBody() {
        WorkflowTask definition = definition();
        definition.setLoopCondition(null);
        definition.setLoopItems("${workflow.input.items}");
        TaskModel loop = loop(definition, 0);
        WorkflowModel workflow = workflow(loop);
        workflow.setInput(Map.of("items", List.of()));

        task().execute(workflow, loop, workflowExecutor);

        assertEquals(TaskModel.Status.COMPLETED, loop.getStatus());
        assertEquals(0, loop.outputAsMap().get("iteration"));
        verifyNoInteractions(workflowExecutor);
    }

    @ParameterizedTest
    @MethodSource("singleItemValues")
    void nonArrayItemsFailBeforeScheduling(Object items) {
        WorkflowTask definition = definition();
        definition.setLoopCondition(null);
        definition.setLoopItems("${workflow.input.items}");
        TaskModel loop = loop(definition, 0);
        WorkflowModel workflow = workflow(loop);
        workflow.getInput().put("items", items);

        task().execute(workflow, loop, workflowExecutor);

        assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, loop.getStatus());
        assertTrue(loop.getReasonForInCompletion().contains("array or collection"));
        assertEquals(0, loop.getIteration());
        assertTrue(loop.outputAsMap().isEmpty());
        verifyNoInteractions(workflowExecutor);
    }

    static Stream<Arguments> singleItemValues() {
        return Stream.of(Arguments.of(3), Arguments.of("single item"), Arguments.of(Map.of("id", 7)), Arguments.of((Object) null));
    }

    @Test
    void nullItemsDoesNotFallBackToInputParameters() {
        WorkflowTask definition = definition();
        definition.setLoopCondition(null);
        definition.setLoopItems("${workflow.input.items}");
        definition.setInputParameters(Map.of("_items", List.of("fallback")));
        TaskModel loop = loop(definition, 0);
        WorkflowModel workflow = workflow(loop);
        workflow.setInput(Map.of());

        task().execute(workflow, loop, workflowExecutor);

        assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, loop.getStatus());
        assertEquals(0, loop.getIteration());
        verifyNoInteractions(workflowExecutor);
        assertFalse(loop.outputAsMap().containsKey("loopItem"));
    }

    @Test
    void listLoopInjectsEachTypedItemAndCompletesAfterLastItem() {
        WorkflowTask definition = definition();
        definition.setLoopCondition(null);
        definition.setLoopItems("${workflow.input.items}");
        TaskModel loop = loop(definition, 0);
        WorkflowModel workflow = workflow(loop);
        workflow.setInput(Map.of("items", List.of(Map.of("id", 1), Map.of("id", 2))));
        DoWhile task = task();

        task.execute(workflow, loop, workflowExecutor);
        assertEquals(0, loop.outputAsMap().get("loopIndex"));
        assertEquals(Map.of("id", 1), loop.outputAsMap().get("loopItem"));
        workflow.getTasks().add(child("body", 1, TaskModel.Status.COMPLETED, "first"));
        task.execute(workflow, loop, workflowExecutor);
        assertEquals(2, loop.getIteration());
        assertEquals(1, loop.outputAsMap().get("loopIndex"));
        assertEquals(Map.of("id", 2), loop.outputAsMap().get("loopItem"));
        workflow.getTasks().add(child("body", 2, TaskModel.Status.COMPLETED, "second"));
        task.execute(workflow, loop, workflowExecutor);

        assertEquals(TaskModel.Status.COMPLETED, loop.getStatus());
        assertEquals(Map.of("body", "first"), loop.outputAsMap().get("1"));
        assertEquals(Map.of("body", "second"), loop.outputAsMap().get("2"));
        verify(workflowExecutor, times(2)).scheduleNextIteration(loop, workflow);
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
        DoWhile task = task((expression, input) -> false);

        task.execute(workflow, loop, workflowExecutor);

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

        task().cancel(workflow(loop), loop, workflowExecutor);

        assertEquals(TaskModel.Status.CANCELED, loop.getStatus());
    }

    @ParameterizedTest
    @MethodSource("arrayValues")
    void collectionAndArrayValuesNormalizeToSnapshot(Object value) {
        TaskModel loop = loop(arrayDefinition(), 0);
        WorkflowModel workflow = workflow(loop);
        workflow.setInput(Map.of("items", value));
        task().execute(workflow, loop, workflowExecutor);
        assertEquals(List.of(1, 2), loop.getInputData().get("loopItems"));
        assertEquals(1, loop.outputAsMap().get("loopItem"));
        verify(workflowExecutor).scheduleNextIteration(loop, workflow);
    }

    static Stream<Arguments> arrayValues() {
        return Stream.of(Arguments.of(List.of(1, 2)), Arguments.of(new java.util.LinkedHashSet<>(List.of(1, 2))),
                Arguments.of((Object) new Integer[]{1, 2}), Arguments.of((Object) new int[]{1, 2}));
    }

    @Test
    void snapshotSurvivesSourceMutationElementMutationAndSerializedRuntime() throws Exception {
        WorkflowTask definition = arrayDefinition();
        definition.setLoopItems("  ${workflow.input.items}  ");
        TaskModel loop = loop(definition, 0);
        Map<String, Object> first = new HashMap<>(Map.of("id", 1));
        Map<String, Object> second = new HashMap<>(Map.of("id", 2));
        List<Object> source = new ArrayList<>(Arrays.asList(first, second, null));
        WorkflowModel workflow = workflow(loop);
        workflow.setInput(Map.of("items", source));
        DoWhile task = task((expression, input) -> { throw new AssertionError("arrays must never evaluate JS"); });
        task.execute(workflow, loop, workflowExecutor);
        first.put("id", 99); second.put("id", 88); source.clear();
        ((Map<String, Object>) loop.outputAsMap().get("loopItem")).put("id", 77);
        assertEquals(Arrays.asList(Map.of("id", 1), Map.of("id", 2), null), loop.getInputData().get("loopItems"));
        workflow.getTasks().add(child("body", 1, TaskModel.Status.COMPLETED, "first"));
        task.execute(workflow, loop, workflowExecutor);
        assertEquals(Map.of("id", 2), loop.outputAsMap().get("loopItem"));
        ObjectMapper mapper = new ObjectMapper();
        TaskModel restored = TaskModel.fromTask(mapper.readValue(mapper.writeValueAsString(loop.toTask()), Task.class));
        workflow.setTasks(List.of(restored, child("body", 1, TaskModel.Status.COMPLETED, "first"),
                child("body", 2, TaskModel.Status.COMPLETED, "second")));
        task.execute(workflow, restored, workflowExecutor);
        assertEquals(3, restored.getIteration());
        assertNull(restored.outputAsMap().get("loopItem"));
        workflow.getTasks().add(child("body", 3, TaskModel.Status.COMPLETED, "last"));
        task.execute(workflow, restored, workflowExecutor);
        assertEquals(TaskModel.Status.COMPLETED, restored.getStatus());
        assertEquals(3, restored.getIteration());
        verify(workflowExecutor, times(3)).scheduleNextIteration(any(), eq(workflow));
    }

    @Test
    void actualGraalJsConditionControlsPostTestLoop() {
        WorkflowTask definition = definition();
        definition.setLoopCondition("$.iteration < $.workflow.input.limit");
        TaskModel loop = loop(definition, 0);
        WorkflowModel workflow = workflow(loop);
        workflow.setInput(Map.of("limit", 2));
        DoWhile task = new DoWhile(new GraalJSEvaluator(), parameters, dao);
        task.execute(workflow, loop, workflowExecutor);
        workflow.getTasks().add(child("body", 1, TaskModel.Status.COMPLETED, "one"));
        task.execute(workflow, loop, workflowExecutor);
        assertEquals(2, loop.getIteration());
        workflow.getTasks().add(child("body", 2, TaskModel.Status.COMPLETED, "two"));
        task.execute(workflow, loop, workflowExecutor);
        assertEquals(TaskModel.Status.COMPLETED, loop.getStatus());
        verify(workflowExecutor, times(2)).scheduleNextIteration(loop, workflow);
    }

    @Test
    void invalidJsFailsAfterFirstBodyWithoutStartingAnotherIteration() {
        WorkflowTask definition = definition();
        definition.setLoopCondition("return ???");
        TaskModel loop = loop(definition, 0);
        WorkflowModel workflow = workflow(loop);
        DoWhile task = new DoWhile(new GraalJSEvaluator(), parameters, dao);
        task.execute(workflow, loop, workflowExecutor);
        workflow.getTasks().add(child("body", 1, TaskModel.Status.COMPLETED, "done"));
        task.execute(workflow, loop, workflowExecutor);
        assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, loop.getStatus());
        assertEquals(1, loop.getIteration());
        assertFalse(loop.getReasonForInCompletion().isBlank());
        verify(workflowExecutor).scheduleNextIteration(loop, workflow);
    }

    private DoWhile task() {
        return task((expression, input) -> { throw new AssertionError("JS should not be evaluated"); });
    }

    private DoWhile task(Evaluator evaluator) {
        GraalJSEvaluator graal = mock(GraalJSEvaluator.class);
        when(graal.evaluate(anyString(), anyMap())).thenAnswer(invocation -> evaluator.evaluate(invocation.getArgument(0), invocation.getArgument(1)));
        return new DoWhile(graal, parameters, dao);
    }

    private static WorkflowTask arrayDefinition() {
        WorkflowTask definition = definition();
        definition.setLoopCondition(null);
        definition.setLoopItems("${workflow.input.items}");
        return definition;
    }

    private static WorkflowTask definition() {
        return WorkflowTask.builder().type("DO_WHILE").taskReferenceName("loop")
                .loopCondition("iteration < limit").loopOver(List.of(childDefinition("body"))).build();
    }

    private static WorkflowTask childDefinition(String ref) {
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
