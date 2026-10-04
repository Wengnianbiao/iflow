package com.helianhealth.iflow.core.execution.tasks;

import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowTask;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JoinTaskTest {

    @Test
    void absentOrEmptyJoinReferencesCompleteImmediately() {
        for (Map<String, Object> input : List.<Map<String, Object>>of(Map.of(), Map.of("joinOn", List.of()))) {
            TaskModel join = join(input);

            new Join().execute(workflow(), join, null);

            assertEquals(TaskModel.Status.COMPLETED, join.getStatus());
            assertTrue(join.outputAsMap().isEmpty());
        }
    }

    @Test
    void collectsAllBranchOutputsOnceEveryBranchIsTerminal() {
        TaskModel left = branch("left", TaskModel.Status.COMPLETED, Map.of("id", 1));
        TaskModel right = branch("right", TaskModel.Status.SKIPPED, List.of(2, 3));
        TaskModel join = join(Map.of("joinOn", List.of("left", "right")));

        new Join().execute(workflow(left, right), join, null);

        assertEquals(TaskModel.Status.COMPLETED, join.getStatus());
        assertEquals(Map.of("left", Map.of("id", 1), "right", List.of(2, 3)), join.getOutputData());
    }

    @ParameterizedTest
    @EnumSource(value = TaskModel.Status.class, names = {"FAILED", "FAILED_WITH_TERMINAL_ERROR", "TIMED_OUT", "CANCELED"})
    void requiredFailedBranchFailsImmediatelyEvenWhileSiblingRuns(TaskModel.Status status) {
        TaskModel failed = branch("failed", status, Map.of("businessError", "preserved"));
        failed.setReasonForInCompletion("branch failed");
        TaskModel running = branch("running", TaskModel.Status.IN_PROGRESS, Map.of());
        TaskModel join = join(Map.of("joinOn", List.of("failed", "running")));

        new Join().execute(workflow(failed, running), join, null);

        assertEquals(TaskModel.Status.FAILED, join.getStatus());
        assertEquals("branch failed", join.getReasonForInCompletion());
        assertEquals(Map.of("businessError", "preserved"), join.outputAsMap().get("failed"));
    }

    @ParameterizedTest
    @EnumSource(value = TaskModel.Status.class, names = {"FAILED", "COMPLETED_WITH_ERRORS"})
    void optionalBranchFailureCompletesWithErrorsAfterSiblingFinishes(TaskModel.Status status) {
        TaskModel optional = branch("optional", status, 7);
        optional.getWorkflowTask().setOptional(true);
        TaskModel running = branch("running", TaskModel.Status.IN_PROGRESS, Map.of());
        WorkflowModel workflow = workflow(optional, running);
        TaskModel join = join(Map.of("joinOn", List.of("optional", "running")));

        new Join().execute(workflow, join, null);
        assertEquals(TaskModel.Status.IN_PROGRESS, join.getStatus());
        running.setStatus(TaskModel.Status.COMPLETED);
        new Join().execute(workflow, join, null);

        assertEquals(TaskModel.Status.COMPLETED_WITH_ERRORS, join.getStatus());
        assertTrue(join.getReasonForInCompletion().contains("optional/task-optional"));
        assertTrue(join.getReasonForInCompletion().contains("completed with errors"));
        assertEquals(7, join.outputAsMap().get("optional"));
    }

    @Test
    void permissiveFailureWaitsForSiblingBeforeFailingJoin() {
        TaskModel failed = branch("failed", TaskModel.Status.FAILED, Map.of());
        failed.getWorkflowTask().setPermissive(true);
        failed.setReasonForInCompletion("deferred failure");
        TaskModel running = branch("running", TaskModel.Status.IN_PROGRESS, Map.of());
        WorkflowModel workflow = workflow(failed, running);
        TaskModel join = join(Map.of("joinOn", List.of("failed", "running")));

        new Join().execute(workflow, join, null);
        assertEquals(TaskModel.Status.IN_PROGRESS, join.getStatus());
        running.setStatus(TaskModel.Status.COMPLETED);
        new Join().execute(workflow, join, null);

        assertEquals(TaskModel.Status.FAILED, join.getStatus());
        assertEquals("deferred failure", join.getReasonForInCompletion());
    }

    @Test
    void loopJoinUsesCurrentIterationReferences() {
        TaskModel previous = branch("branch__1", TaskModel.Status.FAILED, "old");
        TaskModel current = branch("branch__2", TaskModel.Status.COMPLETED, "current");
        TaskModel join = join(Map.of("joinOn", List.of("branch")));
        join.setIteration(2);

        new Join().execute(workflow(previous, current), join, null);

        assertEquals(TaskModel.Status.COMPLETED, join.getStatus());
        assertEquals(Map.of("branch__2", "current"), join.getOutputData());
    }

    private TaskModel join(Map<String, Object> input) {
        TaskModel task = new TaskModel();
        task.setReferenceTaskName("join_ref");
        task.setStatus(TaskModel.Status.IN_PROGRESS);
        task.setInputData(input);
        return task;
    }

    private TaskModel branch(String ref, TaskModel.Status status, Object output) {
        TaskModel task = new TaskModel();
        task.setReferenceTaskName(ref);
        task.setTaskId("task-" + ref);
        task.setTaskDefName(ref);
        task.setWorkflowTask(WorkflowTask.builder().taskReferenceName(ref).type("NOOP").build());
        task.setStatus(status);
        task.setOutputData(output);
        return task;
    }

    private WorkflowModel workflow(TaskModel... tasks) {
        WorkflowModel workflow = new WorkflowModel();
        workflow.setTasks(List.of(tasks));
        return workflow;
    }
}
