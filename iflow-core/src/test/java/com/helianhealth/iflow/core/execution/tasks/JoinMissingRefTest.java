package com.helianhealth.iflow.core.execution.tasks;

import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowDef;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowTask;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import com.helianhealth.iflow.core.utils.TaskUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JoinMissingRefTest {

    @Test
    void failsWhenJoinOnReferencesMissingTask() {
        TaskModel branch = new TaskModel();
        branch.setReferenceTaskName("sync_pacs_ref");
        branch.setStatus(TaskModel.Status.COMPLETED);

        WorkflowModel workflow = new WorkflowModel();
        workflow.setTasks(List.of(branch));

        TaskModel join = new TaskModel();
        join.setReferenceTaskName("apply_join_ref");
        join.setInputData(Map.of("joinOn", List.of("sync_pacs_ref", "mapping_m5c7s_ref")));

        new Join().execute(workflow, join, null);

        assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, join.getStatus());
        assertTrue(join.getReasonForInCompletion().contains("apply_join_ref"));
        assertTrue(join.getReasonForInCompletion().contains("mapping_m5c7s_ref"));
    }

    @Test
    void stillWaitsWhenReferencedTasksExistButAreNotTerminal() {
        TaskModel branch = new TaskModel();
        branch.setReferenceTaskName("sync_pacs_ref");
        branch.setStatus(TaskModel.Status.IN_PROGRESS);

        WorkflowModel workflow = new WorkflowModel();
        workflow.setTasks(List.of(branch));

        TaskModel join = new TaskModel();
        join.setReferenceTaskName("apply_join_ref");
        join.setStatus(TaskModel.Status.IN_PROGRESS);
        join.setInputData(Map.of("joinOn", List.of("sync_pacs_ref")));

        new Join().execute(workflow, join, null);

        assertEquals(TaskModel.Status.IN_PROGRESS, join.getStatus());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    void waitsForDeclaredForkTailUntilDeciderSchedulesIt(int iteration) {
        PendingJoin pending = pendingJoin(iteration);

        new Join().execute(pending.workflow(), pending.join(), null);

        assertEquals(TaskModel.Status.IN_PROGRESS, pending.join().getStatus());
        TaskModel tail = new TaskModel();
        String tailRef = iteration == 0 ? "tail" : TaskUtils.appendIteration("tail", iteration);
        tail.setReferenceTaskName(tailRef);
        tail.setIteration(iteration);
        tail.setStatus(TaskModel.Status.COMPLETED);
        tail.setOutputData(7);
        pending.workflow().getTasks().add(tail);

        new Join().execute(pending.workflow(), pending.join(), null);

        assertEquals(TaskModel.Status.COMPLETED, pending.join().getStatus());
        assertEquals(Map.of(tailRef, 7), pending.join().getOutputData());
    }

    @ParameterizedTest
    @ValueSource(strings = {"following_task", "different_scope", "different_iteration"})
    void missingReferenceOutsideCurrentForkStillFails(String mismatch) {
        PendingJoin pending = pendingJoin(2);
        switch (mismatch) {
            case "following_task" -> pending.join().setInputData(Map.of("joinOn", List.of("after")));
            case "different_scope" -> pending.fork().setForkTaskId("other-fork-scope");
            case "different_iteration" -> pending.fork().setIteration(1);
            default -> throw new AssertionError(mismatch);
        }

        new Join().execute(pending.workflow(), pending.join(), null);

        assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, pending.join().getStatus());
        assertTrue(pending.join().getReasonForInCompletion().contains("不存在"));
    }

    @Test
    void failsForUnselectedSwitchBranchInsteadOfWaitingForever() {
        PendingJoin pending = pendingJoin(2);
        TaskModel switchTask = pending.workflow().getTaskByRefName("first__2");
        WorkflowTask chosen = WorkflowTask.builder().taskReferenceName("chosen").type("NOOP").build();
        WorkflowTask unchosen = pending.fork().getWorkflowTask().getForkTasks().get(0).get(1);
        switchTask.getWorkflowTask().setType("SWITCH");
        switchTask.getWorkflowTask().setDecisionCases(Map.of("selected", List.of(chosen), "unselected", List.of(unchosen)));
        switchTask.getWorkflowTask().setDefaultCase(List.of());
        pending.fork().getWorkflowTask().setForkTasks(List.of(List.of(switchTask.getWorkflowTask())));
        switchTask.setTaskType("SWITCH");
        switchTask.setInputData(Map.of("hasChildren", true));
        TaskModel chosenRuntime = new TaskModel();
        chosenRuntime.setReferenceTaskName("chosen__2");
        chosenRuntime.setIteration(2);
        chosenRuntime.setStatus(TaskModel.Status.COMPLETED);
        pending.workflow().getTasks().add(chosenRuntime);

        new Join().execute(pending.workflow(), pending.join(), null);

        assertEquals(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, pending.join().getStatus());
        assertTrue(pending.join().getReasonForInCompletion().contains("tail__2"));
    }

    private PendingJoin pendingJoin(int iteration) {
        WorkflowTask first = WorkflowTask.builder().taskReferenceName("first").type("NOOP").build();
        WorkflowTask tail = WorkflowTask.builder().taskReferenceName("tail").type("NOOP").build();
        WorkflowTask forkDefinition = WorkflowTask.builder().taskReferenceName("fork").type("FORK_JOIN")
                .forkTasks(List.of(List.of(first, tail))).build();
        WorkflowTask joinDefinition = WorkflowTask.builder().taskReferenceName("join").type("JOIN").build();
        WorkflowTask after = WorkflowTask.builder().taskReferenceName("after").type("NOOP").build();
        TaskModel fork = new TaskModel();
        fork.setTaskType("FORK");
        fork.setReferenceTaskName(iteration == 0 ? "fork" : TaskUtils.appendIteration("fork", iteration));
        fork.setIteration(iteration);
        fork.setWorkflowTask(forkDefinition);
        fork.setStatus(TaskModel.Status.COMPLETED);
        TaskModel firstRuntime = new TaskModel();
        firstRuntime.setTaskType("NOOP");
        firstRuntime.setReferenceTaskName(iteration == 0 ? "first" : TaskUtils.appendIteration("first", iteration));
        firstRuntime.setIteration(iteration);
        firstRuntime.setWorkflowTask(first);
        firstRuntime.setStatus(TaskModel.Status.COMPLETED);
        TaskModel join = new TaskModel();
        join.setReferenceTaskName(iteration == 0 ? "join" : TaskUtils.appendIteration("join", iteration));
        join.setIteration(iteration);
        join.setStatus(TaskModel.Status.IN_PROGRESS);
        join.setInputData(Map.of("joinOn", List.of("tail")));
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowDefinition(WorkflowDef.builder().tasks(List.of(forkDefinition, joinDefinition, after)).build());
        workflow.setTasks(new ArrayList<>(List.of(fork, firstRuntime, join)));
        return new PendingJoin(workflow, join, fork);
    }

    private record PendingJoin(WorkflowModel workflow, TaskModel join, TaskModel fork) {}
}
