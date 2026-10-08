package com.helianhealth.iflow.core.execution.tasks;

import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowDef;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowTask;
import com.helianhealth.iflow.core.execution.DeciderService;
import com.helianhealth.iflow.core.execution.WorkflowExecutor;
import com.helianhealth.iflow.core.execution.mapper.WebServiceTaskMapper;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import com.helianhealth.iflow.core.utils.IDGenerator;
import com.helianhealth.iflow.core.utils.ParametersUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class TaskOutputContractTest {

    private final ParametersUtils parameters = new ParametersUtils();
    private final DeciderService decider = new DeciderService(
            new IDGenerator(), parameters, new SystemTaskRegistry(Set.of()), Map.of());

    static Stream<Arguments> outputs() {
        return Stream.of(
                Arguments.of(Map.of("error", "business", "result", 7)),
                Arguments.of(Map.of()),
                Arguments.of(List.of(Map.of("id", 1))),
                Arguments.of(List.of()),
                Arguments.of("text"),
                Arguments.of(42),
                Arguments.of(false),
                Arguments.of((Object) null));
    }

    @ParameterizedTest
    @MethodSource("outputs")
    void joinsPreserveBranchOutputIncludingEmptyAndNull(Object output) {
        TaskModel branch = completed("branch", output);
        WorkflowModel workflow = workflow(branch);
        TaskModel join = new TaskModel();
        join.setInputData(Map.of("joinOn", List.of("branch")));

        new Join().execute(workflow, join, null);

        assertEquals(TaskModel.Status.COMPLETED, join.getStatus());
        assertEquals(Collections.singletonMap("branch", output), join.getOutputData());
        TaskModel exclusive = new TaskModel();
        exclusive.setInputData(join.getInputData());
        new ExclusiveJoin().execute(workflow, exclusive, null);
        assertEquals(TaskModel.Status.COMPLETED, exclusive.getStatus());
        assertEquals(output, exclusive.getOutputData());
    }

    @ParameterizedTest
    @MethodSource("outputs")
    void workflowUsesRawOutputUnlessExplicitlyMapped(Object output) {
        TaskModel task = completed("parse", output);
        WorkflowModel workflow = workflow(task);

        decider.updateWorkflowOutput(workflow, task);

        assertEquals(output, workflow.getOutput());
        workflow.getWorkflowDefinition().setOutputParameters(Map.of("payload", "${parse.output}"));
        decider.updateWorkflowOutput(workflow, task);
        assertEquals(Collections.singletonMap("payload", output), workflow.getOutput());
    }

    @ParameterizedTest
    @MethodSource("outputs")
    void explicitTerminateOutputOverridesWorkflowMapping(Object output) {
        TaskModel terminate = new TaskModel();
        terminate.setTaskType("TERMINATE");
        Map<String, Object> input = new HashMap<>();
        input.put("terminationStatus", "COMPLETED");
        input.put("workflowOutput", output);
        terminate.setInputData(input);
        WorkflowModel workflow = workflow(terminate);
        workflow.getWorkflowDefinition().setOutputParameters(Map.of("fallback", true));

        new Terminate().execute(workflow, terminate, null);
        decider.updateWorkflowOutput(workflow, terminate);

        assertEquals(TaskModel.Status.COMPLETED, terminate.getStatus());
        assertEquals(output, terminate.getOutputData());
        assertEquals(output, workflow.getOutput());
    }

    @ParameterizedTest
    @MethodSource("outputs")
    void loopAggregatesRawValuesAndPrunesOldIterations(Object output) {
        WorkflowTask childDef = WorkflowTask.builder().type("INLINE").taskReferenceName("branch").build();
        TaskModel loop = new TaskModel();
        loop.setReferenceTaskName("loop");
        loop.setIteration(2);
        loop.setWorkflowTask(WorkflowTask.builder().type("DO_WHILE").taskReferenceName("loop")
                .loopOver(List.of(childDef)).items("${workflow.input.items}")
                .inputParameters(Map.of("keepLastN", 1)).build());
        loop.addOutput("iteration", 2);
        loop.addOutput("1", Map.of("branch", "old"));
        TaskModel child = completed("branch__2", output);
        child.setIteration(2);
        WorkflowModel workflow = workflow(loop, child);
        workflow.setInput(Map.of("items", List.of(1, 2)));

        new DoWhile(Map.of(), parameters).execute(workflow, loop, null);

        assertEquals(TaskModel.Status.COMPLETED, loop.getStatus());
        assertFalse(loop.outputAsMap().containsKey("1"));
        assertEquals(Collections.singletonMap("branch", output), loop.outputAsMap().get("2"));
    }

    private TaskModel completed(String ref, Object output) {
        TaskModel task = new TaskModel();
        task.setReferenceTaskName(ref);
        task.setTaskType("INLINE");
        task.setOutputData(output);
        task.setStatus(TaskModel.Status.COMPLETED);
        return task;
    }

    @Test
    void diagnosticsDoNotOverwriteBusinessErrorField() {
        TaskModel task = completed("business", Map.of("error", "third-party error field"));
        WorkflowSystemTask failing = new WorkflowSystemTask("TEST") {
            @Override
            public void execute(WorkflowModel workflow, TaskModel task,
                    WorkflowExecutor executor) {
                addError(task, "execution failed");
            }
        };
        failing.execute(workflow(task), task, null);

        assertEquals(Map.of("error", "third-party error field"), task.getOutputData());
        assertEquals("execution failed", task.getReasonForInCompletion());
    }

    @Test
    void namedOutputMutationRejectsScalarAndNull() {
        TaskModel task = completed("parse", 42);
        assertThrows(IllegalStateException.class, () -> task.addOutput("field", "value"));
        assertEquals(42, task.getOutputData());
        task.setOutputData(null);
        assertThrows(IllegalStateException.class, task::outputAsMap);
        assertNull(task.getOutputData());
    }

    @Test
    void webServicePreparesTypedParametersAndLeavesLocalPathsForTaskExecution() {
        WorkflowModel workflow = workflow();
        Map<String, Object> patient = Map.of("id", 42, "literal", "${business.text}");
        workflow.setInput(Map.of("patient", patient));
        WorkflowTask definition = WorkflowTask.builder().type("WEBSERVICE")
                .taskReferenceName("soap").inputParameters(Map.of(
                        "uri", "http://example.test",
                        "templateParameters", Map.of("workflow", "${workflow.input.patient}", "soap", true),
                        "soapTemplate", "<id>$.workflow.id</id>")).build();
        DeciderService webServiceDecider = new DeciderService(new IDGenerator(), parameters,
                new SystemTaskRegistry(Set.of()), Map.of("WEBSERVICE", new WebServiceTaskMapper(parameters)));

        List<TaskModel> tasks = webServiceDecider.getTasksToBeScheduled(workflow, definition, 0);

        assertEquals("<id>$.workflow.id</id>", tasks.get(0).getInputData().get("soapTemplate"));
        assertEquals(Map.of("workflow", patient, "soap", true),
                tasks.get(0).getInputData().get("templateParameters"));
    }

    private WorkflowModel workflow(TaskModel... tasks) {
        WorkflowModel workflow = new WorkflowModel();
        workflow.setTasks(List.of(tasks));
        workflow.setWorkflowDefinition(WorkflowDef.builder().route("output-test").build());
        return workflow;
    }
}
