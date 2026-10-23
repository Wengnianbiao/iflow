package com.helianhealth.iflow.core.execution.tasks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.iflow.common.domain.metadata.task.TaskType;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowDef;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowTask;
import com.helianhealth.iflow.core.dal.ExecutionDAOFacade;
import com.helianhealth.iflow.core.execution.DeciderService;
import com.helianhealth.iflow.core.execution.StartWorkflowInput;
import com.helianhealth.iflow.core.execution.WorkflowExecutor;
import com.helianhealth.iflow.core.execution.TestWorkflowExecutor;
import com.helianhealth.iflow.core.execution.mapper.DoWhileTaskMapper;
import com.helianhealth.iflow.core.execution.mapper.ForkJoinTaskMapper;
import com.helianhealth.iflow.core.execution.mapper.JoinTaskMapper;
import com.helianhealth.iflow.core.execution.mapper.NoopTaskMapper;
import com.helianhealth.iflow.core.lock.InMemoryExecutionLockService;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import com.helianhealth.iflow.core.model.WorkflowExecutionMapper;
import com.helianhealth.iflow.core.service.MetadataService;
import com.helianhealth.iflow.core.utils.IDGenerator;
import com.helianhealth.iflow.core.utils.ParametersUtils;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 直接评估 DO_WHILE 控制任务的单元测试。
 * 覆盖循环状态与迭代任务创建，使用真实参数解析和 JS 求值，持久化边界使用测试替身。
 */
class DoWhileTaskTest {

    private final ParametersUtils parameters = new ParametersUtils(new ObjectMapper());
    private final WorkflowExecutor workflowExecutor = mock(WorkflowExecutor.class);
    private final ExecutionDAOFacade dao = mock(ExecutionDAOFacade.class);
    private final DoWhile doWhile = new DoWhile(parameters, dao);

    @Nested
    class IterationLifecycle {

        @Test
        void firstExecutionStartsBodyBeforeEvaluatingCondition() {
            TaskModel loop = loop(jsDefinition("return ???"), 0);
            WorkflowModel workflow = workflow(loop);

            assertTrue(doWhile.execute(workflow, loop, workflowExecutor));

            assertEquals("DO_WHILE", doWhile.getTaskType());
            assertEquals(TaskModel.Status.IN_PROGRESS, loop.getStatus());
            assertEquals(1, loop.getIteration());
            assertEquals(Map.of("iteration", 1), loop.getOutputData());
            assertNull(loop.getReasonForInCompletion());
            verify(workflowExecutor).scheduleNextIteration(loop, workflow);
            verifyNoMoreInteractions(dao);
        }

        @ParameterizedTest
        @EnumSource(value = TaskModel.Status.class, names = {"SCHEDULED", "IN_PROGRESS"})
        void waitsForNonTerminalBodyWithoutEvaluatingCondition(TaskModel.Status status) {
            TaskModel loop = loop(jsDefinition("return ???"), 1);
            TaskModel body = child("body", 1, status, "partial");

            assertFalse(doWhile.execute(workflow(loop, body), loop, workflowExecutor));
            assertFalse(doWhile.execute(workflow(loop, body), loop, workflowExecutor));

            assertEquals(TaskModel.Status.IN_PROGRESS, loop.getStatus());
            assertEquals(1, loop.getIteration());
            assertNull(loop.getReasonForInCompletion());
            assertEquals(Map.of("body", "partial"), iterationOutput(loop, 1));
            verifyNoInteractions(workflowExecutor, dao);
        }

        @Test
        void waitsForUnscheduledTailOfSequentialBody() {
            WorkflowTask definition = jsDefinition("true", bodyDefinition("first"), bodyDefinition("tail"));
            TaskModel loop = loop(definition, 1);
            TaskModel first = child("first", 1, TaskModel.Status.COMPLETED, 7);
            WorkflowModel workflow = workflow(loop, first);

            assertFalse(doWhile.execute(workflow, loop, workflowExecutor));
            assertEquals(TaskModel.Status.IN_PROGRESS, loop.getStatus());
            assertEquals(1, loop.getIteration());
            verifyNoInteractions(workflowExecutor);

            workflow.getTasks().add(child("tail", 1, TaskModel.Status.COMPLETED, "done"));
            assertTrue(doWhile.execute(workflow, loop, workflowExecutor));

            assertEquals(2, loop.getIteration());
            assertEquals(Map.of("first", 7, "tail", "done"), iterationOutput(loop, 1));
            verify(workflowExecutor).scheduleNextIteration(loop, workflow);
        }

        @ParameterizedTest
        @EnumSource(value = TaskModel.Status.class,
                names = {"COMPLETED", "COMPLETED_WITH_ERRORS", "SKIPPED"})
        void successfulTerminalBodyAllowsLoopToComplete(TaskModel.Status status) {
            TaskModel loop = loop(jsDefinition("false"), 1);
            TaskModel body = child("body", 1, status, List.of(7));

            assertTrue(doWhile.execute(workflow(loop, body), loop, workflowExecutor));

            assertEquals(TaskModel.Status.COMPLETED, loop.getStatus());
            assertEquals(1, loop.getIteration());
            assertEquals(Map.of("body", List.of(7)), iterationOutput(loop, 1));
            verifyNoInteractions(workflowExecutor, dao);
        }

        @ParameterizedTest
        @EnumSource(value = TaskModel.Status.class,
                names = {"FAILED", "FAILED_WITH_TERMINAL_ERROR", "TIMED_OUT", "CANCELED"})
        void failedBodyStopsLoopAndPreservesReasonAndRawOutput(TaskModel.Status status) {
            TaskModel loop = loop(jsDefinition("return ???"), 1);
            TaskModel body = child("body", 1, status, Map.of("businessError", "preserved"));
            body.setReasonForInCompletion("body failed");

            assertTrue(doWhile.execute(workflow(loop, body), loop, workflowExecutor));

            assertEquals(TaskModel.Status.FAILED, loop.getStatus());
            assertEquals(1, loop.getIteration());
            assertEquals("body failed", loop.getReasonForInCompletion().trim());
            assertEquals(Map.of("body", Map.of("businessError", "preserved")), iterationOutput(loop, 1));
            assertFalse(loop.outputAsMap().containsKey("error"));
            verifyNoInteractions(workflowExecutor, dao);
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void highestRetryWinsRegardlessOfTaskOrder(boolean newestFirst) {
            TaskModel loop = loop(jsDefinition("false"), 1);
            TaskModel failed = child("body", 1, TaskModel.Status.FAILED, "old");
            failed.setReasonForInCompletion("old failure");
            TaskModel retried = child("body", 1, TaskModel.Status.COMPLETED, "new");
            retried.setTaskId("body-1-retry");
            retried.setRetryCount(2);
            WorkflowModel workflow = newestFirst
                    ? workflow(loop, retried, failed) : workflow(loop, failed, retried);

            doWhile.execute(workflow, loop, workflowExecutor);

            assertEquals(TaskModel.Status.COMPLETED, loop.getStatus());
            assertEquals(Map.of("body", "new"), iterationOutput(loop, 1));
            assertNull(loop.getReasonForInCompletion());
            verifyNoInteractions(workflowExecutor, dao);
        }

        @Test
        void failedLatestRetryOverridesEarlierSuccess() {
            TaskModel loop = loop(jsDefinition("true"), 1);
            TaskModel completed = child("body", 1, TaskModel.Status.COMPLETED, "old");
            TaskModel failed = child("body", 1, TaskModel.Status.FAILED, "new");
            failed.setTaskId("body-1-retry");
            failed.setRetryCount(1);
            failed.setReasonForInCompletion("retry failed");

            doWhile.execute(workflow(loop, completed, failed), loop, workflowExecutor);

            assertEquals(TaskModel.Status.FAILED, loop.getStatus());
            assertEquals("retry failed", loop.getReasonForInCompletion().trim());
            assertEquals(Map.of("body", "new"), iterationOutput(loop, 1));
            verifyNoInteractions(workflowExecutor, dao);
        }

        @Test
        void ignoresPreviousIterationsAndTasksOutsideLoop() {
            TaskModel loop = loop(jsDefinition("false"), 2);
            TaskModel previous = child("body", 1, TaskModel.Status.FAILED, "old");
            TaskModel unrelated = child("unrelated", 2, TaskModel.Status.FAILED, "outside");
            TaskModel current = child("body", 2, TaskModel.Status.COMPLETED, "current");

            doWhile.execute(workflow(loop, previous, unrelated, current), loop, workflowExecutor);

            assertEquals(TaskModel.Status.COMPLETED, loop.getStatus());
            assertEquals(2, loop.getIteration());
            assertEquals(Map.of("body", "current"), iterationOutput(loop, 2));
            assertFalse(loop.outputAsMap().containsKey("1"));
            assertNull(loop.getReasonForInCompletion());
            verifyNoInteractions(workflowExecutor, dao);
        }

        @Test
        void aggregatesObjectArrayScalarAndNullOutputsWithoutWrapping() {
            WorkflowTask definition = jsDefinition("false", bodyDefinition("object"), bodyDefinition("array"),
                    bodyDefinition("scalar"), bodyDefinition("empty"));
            TaskModel loop = loop(definition, 1);
            Map<String, Object> expected = new HashMap<>();
            expected.put("object", Map.of("id", 7));
            expected.put("array", List.of(1, 2));
            expected.put("scalar", "done");
            expected.put("empty", null);

            doWhile.execute(workflow(loop,
                    child("object", 1, TaskModel.Status.COMPLETED, expected.get("object")),
                    child("array", 1, TaskModel.Status.COMPLETED, expected.get("array")),
                    child("scalar", 1, TaskModel.Status.COMPLETED, expected.get("scalar")),
                    child("empty", 1, TaskModel.Status.COMPLETED, null)), loop, workflowExecutor);

            assertEquals(TaskModel.Status.COMPLETED, loop.getStatus());
            assertEquals(expected, iterationOutput(loop, 1));
            assertTrue(iterationOutput(loop, 1).containsKey("empty"));
            verifyNoInteractions(workflowExecutor, dao);
        }

        @Test
        void cancelPreservesIterationAndBusinessData() {
            TaskModel loop = loop(jsDefinition("true"), 2);
            loop.addInput("limit", 3);
            loop.addOutput("1", Map.of("body", "previous"));
            Object output = new HashMap<>(loop.outputAsMap());

            doWhile.cancel(workflow(loop), loop, workflowExecutor);

            assertEquals(TaskModel.Status.CANCELED, loop.getStatus());
            assertEquals(2, loop.getIteration());
            assertEquals(Map.of("limit", 3), loop.getInputData());
            assertEquals(output, loop.getOutputData());
            verifyNoInteractions(workflowExecutor, dao);
        }
    }

    @Nested
    class ConditionEvaluation {

        @ParameterizedTest
        @MethodSource("conditionResults")
        void conditionUsesCurrentBooleanConversion(String script, boolean continues) {
            TaskModel loop = loop(jsDefinition(script), 1);
            WorkflowModel workflow = workflow(loop, child("body", 1, TaskModel.Status.COMPLETED, "done"));

            doWhile.execute(workflow, loop, workflowExecutor);

            assertEquals(continues ? TaskModel.Status.IN_PROGRESS : TaskModel.Status.COMPLETED, loop.getStatus());
            assertEquals(continues ? 2 : 1, loop.getIteration());
            assertEquals(continues ? 2 : 1, loop.outputAsMap().get("iteration"));
            assertEquals(Map.of("body", "done"), iterationOutput(loop, 1));
            assertNull(loop.getReasonForInCompletion());
            verify(workflowExecutor, times(continues ? 1 : 0)).scheduleNextIteration(loop, workflow);
            verifyNoMoreInteractions(dao);
        }

        static Stream<Arguments> conditionResults() {
            // ScriptEvaluator 当前仅将 Boolean 和正数转换为真。
            return Stream.of(
                    Arguments.of("true", true), Arguments.of("false", false),
                    Arguments.of("1", true), Arguments.of("0.5", true),
                    Arguments.of("0", false), Arguments.of("-1", false),
                    Arguments.of("'true'", false), Arguments.of("'TRUE'", false),
                    Arguments.of("'false'", false), Arguments.of("null", false),
                    Arguments.of("undefined", false), Arguments.of("({})", false),
                    Arguments.of("[]", false));
        }

        @Test
        void actualJsConditionContinuesThenCompletesPostTestLoop() {
            TaskModel loop = loop(jsDefinition("$.iteration < $.workflow.input.limit"), 0);
            WorkflowModel workflow = workflow(loop);
            workflow.setInput(Map.of("limit", 2));
            doWhile.execute(workflow, loop, workflowExecutor);
            workflow.getTasks().add(child("body", 1, TaskModel.Status.COMPLETED, "first"));

            doWhile.execute(workflow, loop, workflowExecutor);
            assertEquals(TaskModel.Status.IN_PROGRESS, loop.getStatus());
            assertEquals(2, loop.getIteration());

            workflow.getTasks().add(child("body", 2, TaskModel.Status.COMPLETED, "second"));
            doWhile.execute(workflow, loop, workflowExecutor);

            assertEquals(TaskModel.Status.COMPLETED, loop.getStatus());
            assertEquals(2, loop.getIteration());
            assertEquals(Map.of("body", "first"), iterationOutput(loop, 1));
            assertEquals(Map.of("body", "second"), iterationOutput(loop, 2));
            verify(workflowExecutor, times(2)).scheduleNextIteration(loop, workflow);
            verifyNoMoreInteractions(workflowExecutor);
        }

        @Test
        void conditionSeesResolvedInputsLoopOutputBodyOutputAndWorkflowContext() {
            WorkflowTask definition = jsDefinition("$.iteration === 1 && $.loop.iteration === 1"
                    + " && $.limit === 2 && $.runtimeOnly === 'preserved' && $.body.count === 3"
                    + " && $.workflow.input.limit === 2 && $.workflow.output.previous === 7"
                    + " && String($.workflow.status) === 'RUNNING'");
            definition.setInputParameters(Map.of("limit", "${workflow.input.limit}"));
            TaskModel loop = loop(definition, 1);
            loop.setInputData(Map.of("limit", 99, "runtimeOnly", "preserved"));
            TaskModel body = child("body", 1, TaskModel.Status.COMPLETED, Map.of("count", 3));
            WorkflowModel workflow = workflow(loop, body);
            workflow.setInput(Map.of("limit", 2));
            workflow.setOutput(Map.of("previous", 7));

            doWhile.execute(workflow, loop, workflowExecutor);

            assertEquals(TaskModel.Status.IN_PROGRESS, loop.getStatus());
            assertEquals(2, loop.getIteration());
            assertEquals(Map.of("limit", 99, "runtimeOnly", "preserved"), loop.getInputData());
            assertEquals(Map.of("body", Map.of("count", 3)), iterationOutput(loop, 1));
            verify(workflowExecutor).scheduleNextIteration(loop, workflow);
        }

        @ParameterizedTest
        @ValueSource(strings = {"return ???", "throw new Error('condition failure')"})
        void invalidOrThrowingConditionFailsAfterFirstBody(String condition) {
            TaskModel loop = loop(jsDefinition(condition), 0);
            WorkflowModel workflow = workflow(loop);
            doWhile.execute(workflow, loop, workflowExecutor);
            workflow.getTasks().add(child("body", 1, TaskModel.Status.COMPLETED, "done"));

            doWhile.execute(workflow, loop, workflowExecutor);

            assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, loop.getStatus());
            assertEquals(1, loop.getIteration());
            assertEquals(Map.of("body", "done"), iterationOutput(loop, 1));
            assertTrue(loop.getReasonForInCompletion().contains("Unable to evaluate condition " + condition));
            if (condition.contains("condition failure")) {
                assertTrue(loop.getReasonForInCompletion().contains("condition failure"));
            }
            assertFalse(loop.outputAsMap().containsKey("error"));
            verify(workflowExecutor).scheduleNextIteration(loop, workflow);
            verifyNoMoreInteractions(workflowExecutor);
        }
    }

    @Nested
    class ListIteration {

        @ParameterizedTest
        @MethodSource("collectionValues")
        void traversesListsCollectionsAndObjectArrays(Object items) {
            TaskModel loop = loop(listDefinition(), 0);
            WorkflowModel workflow = workflow(loop);
            workflow.setInput(Map.of("items", items));

            doWhile.execute(workflow, loop, workflowExecutor);
            assertEquals(1, loop.getIteration());
            assertEquals(0, loop.outputAsMap().get("loopIndex"));
            assertEquals(1, loop.outputAsMap().get("loopItem"));

            workflow.getTasks().add(child("body", 1, TaskModel.Status.COMPLETED, "first"));
            doWhile.execute(workflow, loop, workflowExecutor);
            assertEquals(2, loop.getIteration());
            assertEquals(1, loop.outputAsMap().get("loopIndex"));
            assertEquals(2, loop.outputAsMap().get("loopItem"));

            workflow.getTasks().add(child("body", 2, TaskModel.Status.COMPLETED, "second"));
            doWhile.execute(workflow, loop, workflowExecutor);

            assertEquals(TaskModel.Status.COMPLETED, loop.getStatus());
            assertEquals(2, loop.getIteration());
            assertEquals(Map.of("body", "first"), iterationOutput(loop, 1));
            assertEquals(Map.of("body", "second"), iterationOutput(loop, 2));
            verify(workflowExecutor, times(2)).scheduleNextIteration(loop, workflow);
        }

        static Stream<Arguments> collectionValues() {
            return Stream.of(Arguments.of(List.of(1, 2)),
                    Arguments.of(new LinkedHashSet<>(List.of(1, 2))),
                    Arguments.of((Object) new Integer[]{1, 2}));
        }

        @ParameterizedTest
        @MethodSource("emptyValues")
        void emptyOrNullItemsCompleteWithoutScheduling(Object items) {
            TaskModel loop = loop(listDefinition(), 0);
            WorkflowModel workflow = workflow(loop);
            workflow.getInput().put("items", items);

            doWhile.execute(workflow, loop, workflowExecutor);

            assertEquals(TaskModel.Status.COMPLETED, loop.getStatus());
            assertEquals(0, loop.getIteration());
            assertEquals(Map.of("iteration", 0), loop.getOutputData());
            assertNull(loop.getReasonForInCompletion());
            verifyNoInteractions(workflowExecutor, dao);
        }

        static Stream<Arguments> emptyValues() {
            return Stream.of(Arguments.of(List.of()), Arguments.of(Set.of()),
                    Arguments.of((Object) new Object[0]), Arguments.of((Object) null));
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   ", "${workflow.input.missing}"})
        void blankOrUnresolvedItemsExpressionCompletesWithoutScheduling(String expression) {
            WorkflowTask definition = listDefinition();
            definition.setLoopItems(expression);
            TaskModel loop = loop(definition, 0);

            doWhile.execute(workflow(loop), loop, workflowExecutor);

            assertEquals(TaskModel.Status.COMPLETED, loop.getStatus());
            assertEquals(Map.of("iteration", 0), loop.getOutputData());
            verifyNoInteractions(workflowExecutor, dao);
        }

        @ParameterizedTest
        @MethodSource("scalarValues")
        void scalarItemsAreTraversedAsSingleElement(Object item) {
            TaskModel loop = loop(listDefinition(), 0);
            WorkflowModel workflow = workflow(loop);
            workflow.setInput(Map.of("items", item));

            doWhile.execute(workflow, loop, workflowExecutor);
            assertEquals(1, loop.getIteration());
            assertEquals(0, loop.outputAsMap().get("loopIndex"));
            assertEquals(item, loop.outputAsMap().get("loopItem"));

            workflow.getTasks().add(child("body", 1, TaskModel.Status.COMPLETED, "done"));
            doWhile.execute(workflow, loop, workflowExecutor);

            assertEquals(TaskModel.Status.COMPLETED, loop.getStatus());
            assertEquals(1, loop.getIteration());
            verify(workflowExecutor).scheduleNextIteration(loop, workflow);
            verifyNoMoreInteractions(workflowExecutor);
        }

        static Stream<Arguments> scalarValues() {
            return Stream.of(Arguments.of(3), Arguments.of("single"), Arguments.of(Map.of("id", 7)));
        }

        @Test
        void preservesTypedItemsIncludingNullAndStopsAfterLastElement() {
            List<Object> items = Arrays.asList(Map.of("id", 7), "second", null);
            TaskModel loop = loop(listDefinition(), 0);
            WorkflowModel workflow = workflow(loop);
            workflow.setInput(Map.of("items", items));
            doWhile.execute(workflow, loop, workflowExecutor);

            for (int index = 0; index < items.size(); index++) {
                assertEquals(index + 1, loop.getIteration());
                assertEquals(index, loop.outputAsMap().get("loopIndex"));
                assertTrue(loop.outputAsMap().containsKey("loopItem"));
                assertEquals(items.get(index), loop.outputAsMap().get("loopItem"));
                workflow.getTasks().add(child("body", index + 1, TaskModel.Status.COMPLETED, items.get(index)));
                doWhile.execute(workflow, loop, workflowExecutor);
            }

            assertEquals(TaskModel.Status.COMPLETED, loop.getStatus());
            assertEquals(3, loop.getIteration());
            assertTrue(iterationOutput(loop, 3).containsKey("body"));
            assertNull(iterationOutput(loop, 3).get("body"));
            verify(workflowExecutor, times(3)).scheduleNextIteration(loop, workflow);
        }

        @Test
        @SuppressWarnings("unchecked")
        void injectedLoopItemIsDeepCopiedFromSource() {
            Map<String, Object> item = new HashMap<>(Map.of("id", 1,
                    "values", new ArrayList<>(List.of("original"))));
            TaskModel loop = loop(listDefinition(), 0);
            WorkflowModel workflow = workflow(loop);
            workflow.setInput(Map.of("items", List.of(item)));

            doWhile.execute(workflow, loop, workflowExecutor);

            Map<String, Object> injected = (Map<String, Object>) loop.outputAsMap().get("loopItem");
            assertNotSame(item, injected);
            assertNotSame(item.get("values"), injected.get("values"));
            injected.put("id", 99);
            ((List<Object>) injected.get("values")).add("changed");
            assertEquals(Map.of("id", 1, "values", List.of("original")), item);
        }

        @Test
        void reevaluatesItemsSourceBeforeNextIteration() {
            TaskModel loop = loop(listDefinition(), 0);
            WorkflowModel workflow = workflow(loop);
            workflow.getInput().put("items", List.of("first", "second"));
            doWhile.execute(workflow, loop, workflowExecutor);
            assertEquals("first", loop.outputAsMap().get("loopItem"));

            // 当前实现每轮读取来源，新增元素会参与后续迭代。
            workflow.getInput().put("items", List.of("changed-first", "changed-second", "third"));
            workflow.getTasks().add(child("body", 1, TaskModel.Status.COMPLETED, "first result"));
            doWhile.execute(workflow, loop, workflowExecutor);
            assertEquals(2, loop.getIteration());
            assertEquals("changed-second", loop.outputAsMap().get("loopItem"));

            workflow.getTasks().add(child("body", 2, TaskModel.Status.COMPLETED, "second result"));
            doWhile.execute(workflow, loop, workflowExecutor);
            assertEquals(3, loop.getIteration());
            assertEquals("third", loop.outputAsMap().get("loopItem"));

            workflow.getTasks().add(child("body", 3, TaskModel.Status.COMPLETED, "third result"));
            doWhile.execute(workflow, loop, workflowExecutor);
            assertEquals(TaskModel.Status.COMPLETED, loop.getStatus());
            verify(workflowExecutor, times(3)).scheduleNextIteration(loop, workflow);
        }

        @Test
        void listModeTakesPrecedenceOverConfiguredJsCondition() {
            WorkflowTask definition = listDefinition();
            definition.setLoopCondition("throw new Error('JS must not run in list mode')");
            TaskModel loop = loop(definition, 1);
            WorkflowModel workflow = workflow(loop, child("body", 1, TaskModel.Status.COMPLETED, "done"));
            workflow.setInput(Map.of("items", List.of(1, 2)));

            doWhile.execute(workflow, loop, workflowExecutor);

            assertEquals(TaskModel.Status.IN_PROGRESS, loop.getStatus());
            assertEquals(2, loop.getIteration());
            assertEquals(2, loop.outputAsMap().get("loopItem"));
            assertNull(loop.getReasonForInCompletion());
            verify(workflowExecutor).scheduleNextIteration(loop, workflow);
        }

        @Test
        void inputParametersAloneDoNotEnableListMode() {
            WorkflowTask definition = jsDefinition("false");
            definition.setInputParameters(Map.of("_items", "${workflow.input.items}"));
            TaskModel loop = loop(definition, 0);
            WorkflowModel workflow = workflow(loop);
            workflow.setInput(Map.of("items", List.of(1, 2)));
            doWhile.execute(workflow, loop, workflowExecutor);
            workflow.getTasks().add(child("body", 1, TaskModel.Status.COMPLETED, "done"));

            doWhile.execute(workflow, loop, workflowExecutor);

            assertEquals(TaskModel.Status.COMPLETED, loop.getStatus());
            assertEquals(1, loop.getIteration());
            assertFalse(loop.outputAsMap().containsKey("loopItem"));
            assertFalse(loop.outputAsMap().containsKey("loopIndex"));
            verify(workflowExecutor).scheduleNextIteration(loop, workflow);
        }

        @Test
        void missingItemsDoNotFallBackToInputParameters() {
            WorkflowTask definition = listDefinition();
            definition.setInputParameters(Map.of("_items", List.of("fallback")));
            TaskModel loop = loop(definition, 0);

            doWhile.execute(workflow(loop), loop, workflowExecutor);

            assertEquals(TaskModel.Status.COMPLETED, loop.getStatus());
            assertEquals(0, loop.getIteration());
            assertEquals(Map.of("iteration", 0), loop.getOutputData());
            verifyNoInteractions(workflowExecutor, dao);
        }

        @Test
        void resolvesItemsFromAnotherTaskOutput() {
            WorkflowTask definition = listDefinition();
            definition.setLoopItems("${source.output}");
            TaskModel loop = loop(definition, 0);
            TaskModel source = child("source", 0, TaskModel.Status.COMPLETED, List.of(7, 8));
            source.setReferenceTaskName("source");
            WorkflowModel workflow = workflow(source, loop);
            doWhile.execute(workflow, loop, workflowExecutor);
            assertEquals(7, loop.outputAsMap().get("loopItem"));

            workflow.getTasks().add(child("body", 1, TaskModel.Status.COMPLETED, "done"));
            doWhile.execute(workflow, loop, workflowExecutor);

            assertEquals(2, loop.getIteration());
            assertEquals(8, loop.outputAsMap().get("loopItem"));
            assertEquals(Map.of("body", "done"), iterationOutput(loop, 1));
            verify(workflowExecutor, times(2)).scheduleNextIteration(loop, workflow);
        }

        @Test
        void itemsEvaluationFailureIsTerminalAndReportsRootCause() {
            TaskModel loop = loop(listDefinition(), 0);
            WorkflowModel workflow = workflow(loop);
            ParametersUtils failingParameters = mock(ParametersUtils.class);
            when(failingParameters.getTaskInput(Map.of("loopItems", "${workflow.input.items}"),
                    workflow, loop.getTaskId())).thenThrow(
                    new IllegalArgumentException("wrapper", new IllegalStateException("items failure")));

            new DoWhile(failingParameters, dao).execute(workflow, loop, workflowExecutor);

            assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, loop.getStatus());
            assertEquals(0, loop.getIteration());
            assertEquals("Unable to evaluate DO_WHILE loopItems: items failure", loop.getReasonForInCompletion());
            assertTrue(loop.outputAsMap().isEmpty());
            verifyNoInteractions(workflowExecutor, dao);
        }
    }

    @Nested
    class NestedBodyCompletion {

        @Test
        void switchWaitsForUnscheduledSuccessorBeforeAdvancing() {
            WorkflowTask choice = WorkflowTask.builder().type("SWITCH").taskReferenceName("choice")
                    .defaultCase(List.of(bodyDefinition("first"), bodyDefinition("tail"))).build();
            TaskModel loop = loop(jsDefinition("true", choice), 1);
            TaskModel choiceTask = child("choice", 1, TaskModel.Status.COMPLETED, Map.of("case", "default"));
            choiceTask.setWorkflowTask(choice);
            TaskModel first = child("first", 1, TaskModel.Status.COMPLETED, "first result");
            WorkflowModel workflow = workflow(loop, choiceTask, first);

            doWhile.execute(workflow, loop, workflowExecutor);
            assertEquals(TaskModel.Status.IN_PROGRESS, loop.getStatus());
            assertEquals(1, loop.getIteration());
            verifyNoInteractions(workflowExecutor);

            workflow.getTasks().add(child("tail", 1, TaskModel.Status.COMPLETED, "tail result"));
            doWhile.execute(workflow, loop, workflowExecutor);

            assertEquals(2, loop.getIteration());
            assertEquals(Map.of("choice", Map.of("case", "default"),
                    "first", "first result", "tail", "tail result"), iterationOutput(loop, 1));
            verify(workflowExecutor).scheduleNextIteration(loop, workflow);
        }

        @Test
        void forkBodyWaitsForAllBranchesAndJoinBeforeAdvancing() {
            WorkflowTask fork = WorkflowTask.builder().type("FORK_JOIN").taskReferenceName("fork")
                    .forkTasks(List.of(List.of(bodyDefinition("left")), List.of(bodyDefinition("right")))).build();
            WorkflowTask join = WorkflowTask.builder().type("JOIN").taskReferenceName("join")
                    .joinOn(List.of("left", "right")).build();
            TaskModel loop = loop(jsDefinition("true", fork, join), 1);
            TaskModel forkTask = child("fork", 1, TaskModel.Status.COMPLETED, Map.of());
            TaskModel left = child("left", 1, TaskModel.Status.COMPLETED, "left result");
            TaskModel right = child("right", 1, TaskModel.Status.IN_PROGRESS, "partial");
            TaskModel joinTask = child("join", 1, TaskModel.Status.IN_PROGRESS, Map.of());
            WorkflowModel workflow = workflow(loop, forkTask, left, right, joinTask);

            doWhile.execute(workflow, loop, workflowExecutor);
            assertEquals(1, loop.getIteration());
            verifyNoInteractions(workflowExecutor);

            right.setStatus(TaskModel.Status.COMPLETED);
            right.setOutputData("right result");
            doWhile.execute(workflow, loop, workflowExecutor);
            assertEquals(TaskModel.Status.IN_PROGRESS, loop.getStatus());
            assertEquals(1, loop.getIteration());
            verifyNoInteractions(workflowExecutor);

            joinTask.setStatus(TaskModel.Status.COMPLETED);
            doWhile.execute(workflow, loop, workflowExecutor);

            assertEquals(2, loop.getIteration());
            assertEquals(Map.of("fork", Map.of(), "left", "left result",
                    "right", "right result", "join", Map.of()), iterationOutput(loop, 1));
            verify(workflowExecutor).scheduleNextIteration(loop, workflow);
        }
    }

    @Nested
    class IterationRetention {

        @Test
        void removesOnlyOldLoopTasksAndIterationOutput() {
            WorkflowTask definition = jsDefinition("false", bodyDefinition("body"), bodyDefinition("tail"));
            definition.setInputParameters(Map.of("keepLastN", 2));
            TaskModel loop = loop(definition, 4);
            loop.addOutput("loopItem", "metadata");
            WorkflowModel workflow = workflow(loop);
            for (int iteration = 1; iteration <= 4; iteration++) {
                loop.addOutput(String.valueOf(iteration), Map.of("body", "body-" + iteration));
                workflow.getTasks().add(child("body", iteration, TaskModel.Status.COMPLETED, "body-" + iteration));
                workflow.getTasks().add(child("tail", iteration, TaskModel.Status.COMPLETED, "tail-" + iteration));
            }
            TaskModel unrelated = child("unrelated", 1, TaskModel.Status.COMPLETED, "outside");
            TaskModel uniterated = child("body", 0, TaskModel.Status.COMPLETED, "outside");
            workflow.getTasks().addAll(List.of(unrelated, uniterated));

            doWhile.execute(workflow, loop, workflowExecutor);

            assertEquals(TaskModel.Status.COMPLETED, loop.getStatus());
            assertEquals(List.of("loop", "body__3", "tail__3", "body__4", "tail__4", "unrelated__1", "body__0"),
                    workflow.getTasks().stream().map(TaskModel::getReferenceTaskName).toList());
            assertEquals(Set.of("iteration", "loopItem", "3", "4"), loop.outputAsMap().keySet());
            assertEquals("metadata", loop.outputAsMap().get("loopItem"));
            assertEquals(Map.of("body", "body-4", "tail", "tail-4"), iterationOutput(loop, 4));
            verify(dao).removeTask("body-1");
            verify(dao).removeTask("tail-1");
            verify(dao).removeTask("body-2");
            verify(dao).removeTask("tail-2");
            verifyNoMoreInteractions(dao);
            verifyNoInteractions(workflowExecutor);
        }

        @ParameterizedTest
        @MethodSource("retentionBoundaries")
        void retainsAllIterationsBeforeOrAtThreshold(int iteration, int keepLastN) {
            WorkflowTask definition = jsDefinition("false");
            definition.setInputParameters(Map.of("keepLastN", keepLastN));
            TaskModel loop = loop(definition, iteration);
            WorkflowModel workflow = workflow(loop);
            for (int index = 1; index <= iteration; index++) {
                loop.addOutput(String.valueOf(index), Map.of("body", index));
                workflow.getTasks().add(child("body", index, TaskModel.Status.COMPLETED, index));
            }
            List<TaskModel> originalTasks = List.copyOf(workflow.getTasks());
            Object originalOutput = new HashMap<>(loop.outputAsMap());

            doWhile.execute(workflow, loop, workflowExecutor);

            assertEquals(TaskModel.Status.COMPLETED, loop.getStatus());
            assertEquals(originalTasks, workflow.getTasks());
            assertEquals(originalOutput, loop.getOutputData());
            verifyNoInteractions(dao, workflowExecutor);
        }

        static Stream<Arguments> retentionBoundaries() {
            return Stream.of(Arguments.of(1, 3), Arguments.of(3, 5), Arguments.of(3, 3));
        }

        @ParameterizedTest
        @MethodSource("validRetentionValues")
        void keepLastOneAcceptsNumberOrNumericString(Object keepLastN) {
            WorkflowTask definition = jsDefinition("false");
            definition.setInputParameters(Map.of("keepLastN", keepLastN));
            TaskModel loop = loop(definition, 3);
            loop.addOutput("1", Map.of("body", "first"));
            loop.addOutput("2", Map.of("body", "second"));
            TaskModel current = child("body", 3, TaskModel.Status.COMPLETED, "third");
            WorkflowModel workflow = workflow(loop,
                    child("body", 1, TaskModel.Status.COMPLETED, "first"),
                    child("body", 2, TaskModel.Status.COMPLETED, "second"), current);

            doWhile.execute(workflow, loop, workflowExecutor);

            assertEquals(List.of(loop, current), workflow.getTasks());
            assertEquals(Set.of("iteration", "3"), loop.outputAsMap().keySet());
            verify(dao).removeTask("body-1");
            verify(dao).removeTask("body-2");
            verifyNoMoreInteractions(dao);
        }

        static Stream<Arguments> validRetentionValues() {
            return Stream.of(Arguments.of(1), Arguments.of("1"));
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void absentRetentionKeepsOldTasksAndOutput(boolean nullInputParameters) {
            WorkflowTask definition = jsDefinition("false");
            definition.setInputParameters(nullInputParameters ? null : new HashMap<>());
            TaskModel loop = loop(definition, 2);
            loop.addOutput("1", Map.of("body", "old"));
            WorkflowModel workflow = workflow(loop, child("body", 1, TaskModel.Status.COMPLETED, "old"),
                    child("body", 2, TaskModel.Status.COMPLETED, "current"));
            List<TaskModel> originalTasks = List.copyOf(workflow.getTasks());

            doWhile.execute(workflow, loop, workflowExecutor);

            assertEquals(TaskModel.Status.COMPLETED, loop.getStatus());
            assertEquals(originalTasks, workflow.getTasks());
            assertEquals(Map.of("body", "old"), iterationOutput(loop, 1));
            assertEquals(Map.of("body", "current"), iterationOutput(loop, 2));
            verifyNoInteractions(dao, workflowExecutor);
        }

        @ParameterizedTest
        @MethodSource("invalidRetentionValues")
        void invalidRetentionThrowsBeforeSchedulingOrDeleting(Object keepLastN) {
            WorkflowTask definition = jsDefinition("true");
            definition.setInputParameters(Map.of("keepLastN", keepLastN));
            TaskModel loop = loop(definition, 1);
            WorkflowModel workflow = workflow(loop, child("body", 1, TaskModel.Status.COMPLETED, "done"));

            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                    () -> doWhile.execute(workflow, loop, workflowExecutor));

            assertEquals("DO_WHILE keepLastN must be a positive integer", failure.getMessage());
            assertEquals(1, loop.getIteration());
            assertEquals(Map.of("body", "done"), iterationOutput(loop, 1));
            verifyNoInteractions(dao, workflowExecutor);
        }

        static Stream<Arguments> invalidRetentionValues() {
            return Stream.of(Arguments.of(0), Arguments.of(-1), Arguments.of("0"),
                    Arguments.of("-1"), Arguments.of("invalid"));
        }

        @Test
        void daoRemovalFailureDoesNotStopRemainingCleanup() {
            WorkflowTask definition = jsDefinition("false");
            definition.setInputParameters(Map.of("keepLastN", 1));
            TaskModel loop = loop(definition, 3);
            loop.addOutput("1", Map.of("body", "first"));
            loop.addOutput("2", Map.of("body", "second"));
            TaskModel current = child("body", 3, TaskModel.Status.COMPLETED, "third");
            WorkflowModel workflow = workflow(loop,
                    child("body", 1, TaskModel.Status.COMPLETED, "first"),
                    child("body", 2, TaskModel.Status.COMPLETED, "second"), current);
            doThrow(new IllegalStateException("database failure")).when(dao).removeTask("body-1");

            assertDoesNotThrow(() -> doWhile.execute(workflow, loop, workflowExecutor));

            assertEquals(TaskModel.Status.COMPLETED, loop.getStatus());
            assertEquals(List.of(loop, current), workflow.getTasks());
            assertEquals(Set.of("iteration", "3"), loop.outputAsMap().keySet());
            verify(dao).removeTask("body-1");
            verify(dao).removeTask("body-2");
            verifyNoMoreInteractions(dao);
            verifyNoInteractions(workflowExecutor);
        }

        @Test
        void listIterationContinuesAfterOldIterationsAreRemoved() {
            WorkflowTask definition = listDefinition();
            definition.setInputParameters(Map.of("keepLastN", 2));
            TaskModel loop = loop(definition, 3);
            loop.addOutput("1", Map.of("body", "first"));
            loop.addOutput("2", Map.of("body", "second"));
            TaskModel previous = child("body", 2, TaskModel.Status.COMPLETED, "second");
            TaskModel current = child("body", 3, TaskModel.Status.COMPLETED, "third");
            WorkflowModel workflow = workflow(loop,
                    child("body", 1, TaskModel.Status.COMPLETED, "first"), previous, current);
            workflow.setInput(Map.of("items", List.of("a", "b", "c", "d")));

            doWhile.execute(workflow, loop, workflowExecutor);

            assertEquals(List.of(loop, previous, current), workflow.getTasks());
            assertFalse(loop.outputAsMap().containsKey("1"));
            assertEquals(Map.of("body", "second"), iterationOutput(loop, 2));
            assertEquals(Map.of("body", "third"), iterationOutput(loop, 3));
            assertEquals(TaskModel.Status.IN_PROGRESS, loop.getStatus());
            assertEquals(4, loop.getIteration());
            assertEquals(3, loop.outputAsMap().get("loopIndex"));
            assertEquals("d", loop.outputAsMap().get("loopItem"));
            verify(dao).removeTask("body-1");
            verify(workflowExecutor).scheduleNextIteration(loop, workflow);
            verifyNoMoreInteractions(dao);
        }
    }

    @Nested
    class IterationTaskCreation {

        @Test
        void executesArrayIterationsOnCallerThread() throws Exception {
            Fixture fixture = new Fixture(definition(loop(
                    task("body", TaskType.TASK_TYPE_NOOP),
                    task("tail", TaskType.TASK_TYPE_NOOP))));

            WorkflowModel result = fixture.startInForkPool(1);

            assertEquals(List.of("body__1", "tail__1", "body__2", "tail__2"), fixture.executions);
            TaskModel loop = result.getTaskByRefName("loop");
            assertEquals(2, loop.getIteration());
            assertEquals(1, loop.outputAsMap().get("loopIndex"));
            assertEquals("two", loop.outputAsMap().get("loopItem"));
            assertTrue(fixture.executionThreads.values().stream().allMatch("iteration-caller"::equals));
            fixture.assertCompletedAndPersistedOnce(result);
        }

        @Test
        void schedulesIterationEntryImmediatelyAndLeavesSuccessorsForDecider() {
            WorkflowTask loopDefinition = loop(task("body", TaskType.TASK_TYPE_NOOP),
                    task("tail", TaskType.TASK_TYPE_NOOP));
            Fixture fixture = new Fixture(definition(loopDefinition));
            WorkflowModel workflow = new WorkflowModel();
            workflow.setWorkflowId("workflow-id");
            workflow.setWorkflowDefinition(fixture.definition);
            workflow.setInput(Map.of("items", List.of("one", "two")));
            TaskModel loop = new TaskModel();
            loop.setTaskId("loop-id");
            loop.setReferenceTaskName("loop");
            loop.setTaskType(TaskType.TASK_TYPE_DO_WHILE);
            loop.setWorkflowTask(loopDefinition);
            loop.setStatus(TaskModel.Status.IN_PROGRESS);
            loop.setSeq(7);
            loop.setForkTaskId("outer-fork-id");
            workflow.getTasks().add(loop);

            assertTrue(fixture.doWhileTask.execute(workflow, loop, fixture.executor));
            assertFalse(fixture.doWhileTask.execute(workflow, loop, fixture.executor));

            assertEquals(List.of("body__1"), fixture.executions);
            assertEquals(2, workflow.getTasks().size());
            assertEquals(1, fixture.created.size());
            TaskModel body = workflow.getTaskByRefName("body__1");
            assertNotNull(body);
            assertEquals(TaskModel.Status.COMPLETED, body.getStatus());
            assertTrue(body.getStartTime() > 0);
            assertEquals(1, loop.getIteration());
            assertEquals(1, body.getIteration());
            assertEquals("outer-fork-id", body.getForkTaskId());
            assertEquals("workflow-id", body.getWorkflowInstanceId());
            assertEquals(8, body.getSeq());
            assertTrue(body.getFirstScheduledTime() > 0);
            assertTrue(body.getScheduledTime() > 0);
            TaskModel persisted = fixture.created.get(body.getTaskId());
            assertEquals(TaskModel.Status.SCHEDULED, persisted.getStatus());
            assertEquals(0, persisted.getStartTime());
            assertEquals(body.getReferenceTaskName(), persisted.getReferenceTaskName());
            assertEquals(body.getSeq(), persisted.getSeq());
            assertEquals(body.getFirstScheduledTime(), persisted.getFirstScheduledTime());
            assertEquals(body.getForkTaskId(), persisted.getForkTaskId());
            assertNull(workflow.getTaskByRefName("tail__1"));

            fixture.decider.decide(workflow);

            assertSame(body, workflow.getTaskByRefName("body__1"));
            assertEquals(TaskModel.Status.COMPLETED, body.getStatus());
            assertEquals(List.of("body__1"), fixture.executions, "Decider selects the successor without starting it");
            assertEquals(1, fixture.created.size());
        }

        @Test
        void startsEachIterationInTheSameDecisionThatCreatesItsEntry() throws Exception {
            Fixture fixture = new Fixture(definition(loop(task("body", TaskType.TASK_TYPE_NOOP))));

            WorkflowModel result = fixture.startInForkPool(1);

            assertEquals(List.of("body__1", "body__2"), fixture.executions);
            for (String ref : List.of("body__1", "body__2")) {
                int registrationRound = fixture.registrationRounds.get(ref);
                int executionRound = fixture.executionRounds.get(ref);
                assertEquals(registrationRound, executionRound,
                        "scheduleNextIteration must start the entry immediately in the current decision");
                assertFalse(fixture.selectedRounds.containsKey(ref),
                        "a completed entry must not wait for a later decision to select it");
            }
            fixture.assertCompletedAndPersistedOnce(result);
        }

        @Test
        void queuesAsyncIterationEntryImmediatelyAndDoesNotScheduleItTwice() {
            Fixture fixture = new Fixture(definition(loop(task("body", TaskType.TASK_TYPE_NOOP))), true);
            java.util.Queue<Runnable> workers = new java.util.ArrayDeque<>();
            fixture.executor.useTaskExecutor(workers::add);

            StartWorkflowInput startInput = new StartWorkflowInput();
            startInput.setRoute(fixture.definition.getRoute());
            startInput.setWorkflowInput(Map.of("items", List.of("one", "two")));
            String workflowId = fixture.executor.startWorkflow(startInput);
            WorkflowModel running = fixture.dao.getWorkflowModel(workflowId);

            TaskModel entry = running.getTaskByRefName("body__1");
            assertNotNull(entry);
            assertEquals(TaskModel.Status.SCHEDULED, entry.getStatus());
            assertEquals(0, entry.getStartTime());
            assertTrue(fixture.executions.isEmpty());
            assertEquals(1, workers.size());
            assertTrue(fixture.executor.queue().containsMessage(TaskType.TASK_TYPE_NOOP, entry.getTaskId()));
            assertFalse(fixture.doWhileTask.execute(running, running.getTaskByRefName("loop"), fixture.executor));
            assertEquals(1, workers.size());

            while (!workers.isEmpty()) {
                workers.remove().run();
            }

            WorkflowModel completed = fixture.storedWorkflow.get();
            assertEquals(WorkflowModel.Status.COMPLETED, completed.getStatus());
            assertEquals(List.of("body__1", "body__2"), fixture.executions);
            assertEquals(3, fixture.created.size());
        }

        @Test
        void completesAsyncIterationsAndNestedForkWithOneWorker() throws Exception {
            WorkflowTask innerFork = task("inner_fork", TaskType.TASK_TYPE_FORK_JOIN);
            innerFork.setForkTasks(List.of(List.of(task("a", TaskType.TASK_TYPE_NOOP)),
                    List.of(task("b", TaskType.TASK_TYPE_NOOP))));
            WorkflowTask innerJoin = task("inner_join", TaskType.TASK_TYPE_JOIN);
            innerJoin.setJoinOn(List.of("a", "b"));
            Fixture fixture = new Fixture(outerFork(loop(innerFork, innerJoin)), true);

            WorkflowModel completed = fixture.startInForkPool(1);

            assertEquals(2, completed.getTaskByRefName("loop").getIteration());
            assertEquals(6, fixture.executions.size());
            assertTrue(fixture.executionThreads.values().stream().allMatch("iteration-fork-pool"::equals));
            fixture.assertCompletedAndPersistedOnce(completed);
        }

        @Test
        void executesSynchronousIterationsInsideForkOnCaller() throws Exception {
            WorkflowTask loop = loop(task("body", TaskType.TASK_TYPE_NOOP),
                    task("tail", TaskType.TASK_TYPE_NOOP));
            Fixture fixture = new Fixture(outerFork(loop));
            WorkflowModel result = fixture.startInForkPool(1);

            assertEquals(List.of("body__1", "tail__1", "body__2", "tail__2"),
                    fixture.executions.stream().filter(ref -> !List.of("sibling", "after").contains(ref)).toList());
            assertEquals(2, result.getTaskByRefName("loop").getIteration());
            String forkId = result.getTaskByRefName("outer_fork").getTaskId();
            for (String ref : List.of("loop", "body__1", "tail__1", "body__2", "tail__2")) {
                assertEquals(forkId, result.getTaskByRefName(ref).getForkTaskId());
            }
            assertEquals("iteration-caller", fixture.executionThreads.get("body__1"));
            assertEquals("iteration-caller", fixture.executionThreads.get("body__2"));
            assertEquals("iteration-caller", fixture.executionThreads.get("after"));
            assertNull(result.getTaskByRefName("outer_join").getForkTaskId());
            fixture.assertCompletedAndPersistedOnce(result);
        }

        @Test
        void generatesEntireForkAtIterationEntryAndRestoresOuterScopeAfterJoin() throws Exception {
            WorkflowTask innerFork = task("inner_fork", TaskType.TASK_TYPE_FORK_JOIN);
            innerFork.setForkTasks(List.of(
                    List.of(task("inner_a", TaskType.TASK_TYPE_NOOP), task("inner_tail", TaskType.TASK_TYPE_NOOP)),
                    List.of(task("inner_b", TaskType.TASK_TYPE_NOOP))));
            WorkflowTask innerJoin = task("inner_join", TaskType.TASK_TYPE_JOIN);
            innerJoin.setJoinOn(List.of("inner_tail", "inner_b"));
            Fixture fixture = new Fixture(outerFork(loop(innerFork, innerJoin)));
            WorkflowModel result = fixture.startInForkPool(2);

            String outerForkId = result.getTaskByRefName("outer_fork").getTaskId();
            for (int iteration = 1; iteration <= 2; iteration++) {
                TaskModel fork = result.getTaskByRefName("inner_fork__" + iteration);
                assertEquals(outerForkId, fork.getForkTaskId());
                for (String ref : List.of("inner_a", "inner_tail", "inner_b")) {
                    TaskModel task = result.getTaskByRefName(ref + "__" + iteration);
                    assertEquals(iteration, task.getIteration());
                    assertEquals(fork.getTaskId(), task.getForkTaskId());
                }
                assertEquals(outerForkId, result.getTaskByRefName("inner_join__" + iteration).getForkTaskId());
            }
            assertNotEquals(result.getTaskByRefName("inner_fork__1").getTaskId(),
                    result.getTaskByRefName("inner_fork__2").getTaskId());
            fixture.assertCompletedAndPersistedOnce(result);
        }

        private static WorkflowTask task(String ref, String type) {
            return WorkflowTask.builder().name(ref).taskReferenceName(ref).type(type).build();
        }

        private static WorkflowTask loop(WorkflowTask... body) {
            WorkflowTask loop = task("loop", TaskType.TASK_TYPE_DO_WHILE);
            loop.setLoopOver(List.of(body));
            loop.setLoopItems("${workflow.input.items}");
            return loop;
        }

        private static WorkflowDef definition(WorkflowTask... tasks) {
            return WorkflowDef.builder().route("iteration-workflow").tasks(List.of(tasks)).build();
        }

        private static WorkflowDef outerFork(WorkflowTask loop) {
            WorkflowTask fork = task("outer_fork", TaskType.TASK_TYPE_FORK_JOIN);
            fork.setForkTasks(List.of(List.of(loop), List.of(task("sibling", TaskType.TASK_TYPE_NOOP))));
            WorkflowTask join = task("outer_join", TaskType.TASK_TYPE_JOIN);
            join.setJoinOn(List.of("loop", "sibling"));
            return definition(fork, join, task("after", TaskType.TASK_TYPE_NOOP));
        }

        private static class Fixture {
            final WorkflowDef definition;
            final Map<String, TaskModel> created = new ConcurrentHashMap<>();
            final Map<String, TaskModel> updated = new ConcurrentHashMap<>();
            final List<String> executions = new CopyOnWriteArrayList<>();
            final Map<String, String> executionThreads = new ConcurrentHashMap<>();
            final AtomicInteger decisionRounds = new AtomicInteger();
            final Map<String, Integer> registrationRounds = new ConcurrentHashMap<>();
            final Map<String, Integer> selectedRounds = new ConcurrentHashMap<>();
            final Map<String, Integer> executionRounds = new ConcurrentHashMap<>();
            final DoWhile doWhileTask;
            final SystemTaskRegistry registry;
            final DeciderService decider;
            final TestWorkflowExecutor executor;
            final ExecutionDAOFacade dao;
            final AtomicReference<WorkflowModel> storedWorkflow = new AtomicReference<>();

            Fixture(WorkflowDef definition) {
                this(definition, false);
            }

            Fixture(WorkflowDef definition, boolean asyncBody) {
                this.definition = definition;
                ObjectMapper mapper = new ObjectMapper();
                ParametersUtils parameters = new ParametersUtils(mapper);
                dao = new ExecutionDAOFacade(null, mapper) {
                    @Override
                    public String createWorkflow(WorkflowModel workflow) {
                        storedWorkflow.set(WorkflowExecutionMapper.toModel(
                                WorkflowExecutionMapper.toWorkflowHeader(workflow)));
                        return workflow.getWorkflowId();
                    }

                    @Override
                    public WorkflowModel getWorkflowModel(String workflowId) {
                        return getWorkflow(workflowId, true).orElse(null);
                    }

                    @Override
                    public Optional<WorkflowModel> getWorkflow(String workflowId, boolean includeTasks) {
                        return Optional.ofNullable(storedWorkflow.get()).map(source -> {
                            WorkflowModel snapshot = WorkflowExecutionMapper.toModel(
                                    WorkflowExecutionMapper.toWorkflowHeader(source));
                            if (includeTasks) {
                                snapshot.setTasks(created.values().stream()
                                        .map(task -> updated.getOrDefault(task.getTaskId(), task))
                                        .sorted(java.util.Comparator.comparingInt(TaskModel::getSeq))
                                        .map(task -> TaskModel.fromTask(task.toTask())).toList());
                            }
                            return snapshot;
                        });
                    }

                    @Override
                    public List<TaskModel> createTasks(List<TaskModel> tasks) {
                        for (TaskModel task : tasks) {
                            assertNull(created.putIfAbsent(task.getTaskId(), TaskModel.fromTask(task.toTask())),
                                    "each runtime task must be created once");
                            registrationRounds.put(task.getReferenceTaskName(), decisionRounds.get());
                        }
                        return tasks;
                    }

                    @Override
                    public java.util.Optional<TaskModel> getTask(String taskId) {
                        TaskModel task = updated.getOrDefault(taskId, created.get(taskId));
                        return java.util.Optional.ofNullable(task).map(value -> TaskModel.fromTask(value.toTask()));
                    }

                    @Override
                    public void updateTask(TaskModel task) {
                        updated.put(task.getTaskId(), TaskModel.fromTask(task.toTask()));
                    }

                    @Override
                    public String updateWorkflow(WorkflowModel workflow) {
                        storedWorkflow.set(WorkflowExecutionMapper.toModel(
                                WorkflowExecutionMapper.toWorkflowHeader(workflow)));
                        return workflow.getWorkflowId();
                    }
                };
                WorkflowSystemTask noop = new WorkflowSystemTask(TaskType.TASK_TYPE_NOOP) {
                    @Override
                    public boolean isAsync() {
                        return asyncBody;
                    }

                    @Override
                    public void start(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
                        assertTrue(created.containsKey(task.getTaskId()), "persist tasks before executing them");
                        executions.add(task.getReferenceTaskName());
                        executionThreads.put(task.getReferenceTaskName(), Thread.currentThread().getName());
                        executionRounds.put(task.getReferenceTaskName(), decisionRounds.get());
                        task.setOutputData(task.getReferenceTaskName());
                        task.setStatus(TaskModel.Status.COMPLETED);
                    }
                };
                doWhileTask = new DoWhile(parameters, dao);
                registry = new SystemTaskRegistry(java.util.Set.of(noop, doWhileTask, new Fork(), new Join()));
                decider = new DeciderService(new IDGenerator(), parameters, registry, Map.of(
                        TaskType.TASK_TYPE_NOOP, new NoopTaskMapper(),
                        TaskType.TASK_TYPE_DO_WHILE, new DoWhileTaskMapper(parameters),
                        TaskType.TASK_TYPE_FORK_JOIN, new ForkJoinTaskMapper(),
                        TaskType.TASK_TYPE_JOIN, new JoinTaskMapper())) {
                    @Override
                    public DeciderOutcome decide(WorkflowModel workflow) {
                        int round = decisionRounds.incrementAndGet();
                        assertTrue(round <= 100, "workflow must complete within 100 decision rounds");
                        DeciderOutcome outcome = super.decide(workflow);
                        for (TaskModel task : workflow.getTasks()) {
                            if (task.getStatus() == TaskModel.Status.SCHEDULED) {
                                selectedRounds.putIfAbsent(task.getReferenceTaskName(), round);
                            }
                        }
                        return outcome;
                    }
                };
                MetadataService metadata = (MetadataService) Proxy.newProxyInstance(
                        MetadataService.class.getClassLoader(), new Class<?>[] {MetadataService.class},
                        (proxy, method, args) -> {
                            if ("getWorkflowDef".equals(method.getName())) {
                                return definition;
                            }
                            throw new UnsupportedOperationException(method.getName());
                        });
                executor = new TestWorkflowExecutor(decider, registry, dao, metadata,
                        new IDGenerator(), parameters, new InMemoryExecutionLockService());
            }

            void useForkPool(Executor pool) {
                executor.useTaskExecutor(pool);
            }

            WorkflowModel startInForkPool(int size) throws Exception {
                ExecutorService pool = Executors.newFixedThreadPool(size, runnable -> new Thread(runnable, "iteration-fork-pool"));
                ExecutorService caller = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "iteration-caller"));
                useForkPool(pool);
                try {
                    StartWorkflowInput startInput = new StartWorkflowInput();
                    startInput.setRoute(definition.getRoute());
                    startInput.setWorkflowInput(Map.of("items", List.of("one", "two")));
                    return caller.submit(() -> executor.startWorkflowSync(startInput))
                            .get(5, TimeUnit.SECONDS);
                } finally {
                    caller.shutdownNow();
                    pool.shutdownNow();
                }
            }

            void assertCompletedAndPersistedOnce(WorkflowModel result) {
                assertEquals(WorkflowModel.Status.COMPLETED, result.getStatus());
                assertEquals(result.getTasks().size(), created.size());
                assertEquals(result.getTasks().size(), result.getTasks().stream().map(TaskModel::getSeq).distinct().count());
                assertEquals(executions.size(), executions.stream().distinct().count());
                for (TaskModel task : result.getTasks()) {
                    assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
                    assertEquals(task.getStatus(), updated.get(task.getTaskId()).getStatus());
                }
            }
        }
    }

    private static WorkflowTask jsDefinition(String condition, WorkflowTask... body) {
        return WorkflowTask.builder().type("DO_WHILE").taskReferenceName("loop")
                .loopCondition(condition)
                .loopOver(body.length == 0 ? List.of(bodyDefinition("body")) : List.of(body)).build();
    }

    private static WorkflowTask listDefinition() {
        WorkflowTask definition = jsDefinition(null);
        definition.setLoopItems("${workflow.input.items}");
        return definition;
    }

    private static WorkflowTask bodyDefinition(String ref) {
        return WorkflowTask.builder().name(ref).type("NOOP").taskReferenceName(ref).build();
    }

    private static TaskModel loop(WorkflowTask definition, int iteration) {
        TaskModel task = new TaskModel();
        task.setTaskId("loop-1");
        task.setTaskType("DO_WHILE");
        task.setReferenceTaskName("loop");
        task.setWorkflowTask(definition);
        task.setStatus(TaskModel.Status.IN_PROGRESS);
        task.setIteration(iteration);
        if (iteration > 0) {
            task.addOutput("iteration", iteration);
        }
        return task;
    }

    private static TaskModel child(String ref, int iteration, TaskModel.Status status, Object output) {
        TaskModel task = new TaskModel();
        task.setTaskId(ref + "-" + iteration);
        task.setTaskType("NOOP");
        task.setWorkflowTask(bodyDefinition(ref));
        task.setReferenceTaskName(ref + "__" + iteration);
        task.setIteration(iteration);
        task.setStatus(status);
        task.setOutputData(output);
        return task;
    }

    private static WorkflowModel workflow(TaskModel... tasks) {
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("workflow-1");
        workflow.setTasks(List.of(tasks));
        return workflow;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> iterationOutput(TaskModel loop, int iteration) {
        return (Map<String, Object>) loop.outputAsMap().get(String.valueOf(iteration));
    }
}
