package com.helianhealth.iflow.core.execution.tasks;

import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ExclusiveJoinTaskTest {

    @Test
    void skipsAbsentAndRunningCandidatesThenSelectsFirstTerminalCandidate() {
        TaskModel running = branch("running", TaskModel.Status.IN_PROGRESS, "unfinished");
        TaskModel selected = branch("selected", TaskModel.Status.COMPLETED, List.of(1, 2));
        TaskModel later = branch("later", TaskModel.Status.COMPLETED, "later");
        TaskModel join = join(Map.of("joinOn", List.of("missing", "running", "selected", "later")));

        assertTrue(new ExclusiveJoin().execute(workflow(running, selected, later), join, null));

        assertEquals(TaskModel.Status.COMPLETED, join.getStatus());
        assertEquals(List.of(1, 2), join.getOutputData());
    }

    @Test
    void usesFallbackWhenNoPrimaryCandidateHasFinished() {
        TaskModel primary = branch("primary", TaskModel.Status.IN_PROGRESS, null);
        TaskModel fallback = branch("fallback", TaskModel.Status.COMPLETED, "fallback output");
        TaskModel join = join(Map.of("joinOn", List.of("primary"),
                "defaultExclusiveJoinTask", List.of("fallback")));

        assertTrue(new ExclusiveJoin().execute(workflow(primary, fallback), join, null));

        assertEquals(TaskModel.Status.COMPLETED, join.getStatus());
        assertEquals("fallback output", join.getOutputData());
    }

    @Test
    void primaryFailureTakesPrecedenceOverSuccessfulFallback() {
        TaskModel primary = branch("primary", TaskModel.Status.FAILED, "business failure");
        primary.setReasonForInCompletion("selected branch failed");
        TaskModel fallback = branch("fallback", TaskModel.Status.COMPLETED, "fallback output");
        TaskModel join = join(Map.of("joinOn", List.of("primary"),
                "defaultExclusiveJoinTask", List.of("fallback")));

        assertTrue(new ExclusiveJoin().execute(workflow(primary, fallback), join, null));

        assertEquals(TaskModel.Status.FAILED, join.getStatus());
        assertEquals("selected branch failed", join.getReasonForInCompletion());
        assertTrue(join.outputAsMap().isEmpty());
    }

    @ParameterizedTest
    @EnumSource(value = TaskModel.Status.class, names = {"SCHEDULED", "IN_PROGRESS"})
    void waitsWhenAllCandidatesAreAbsentOrStillRunning(TaskModel.Status status) {
        TaskModel running = branch("running", status, null);
        TaskModel join = join(Map.of("joinOn", List.of("missing", "running")));

        assertFalse(new ExclusiveJoin().execute(workflow(running), join, null));
        assertFalse(new ExclusiveJoin().execute(workflow(running), join, null));

        assertEquals(TaskModel.Status.IN_PROGRESS, join.getStatus());
        assertNull(join.getReasonForInCompletion());
        assertTrue(join.outputAsMap().isEmpty());
    }

    private TaskModel branch(String ref, TaskModel.Status status, Object output) {
        TaskModel task = new TaskModel();
        task.setReferenceTaskName(ref);
        task.setStatus(status);
        task.setOutputData(output);
        return task;
    }

    private TaskModel join(Map<String, Object> input) {
        TaskModel task = new TaskModel();
        task.setStatus(TaskModel.Status.IN_PROGRESS);
        task.setInputData(input);
        return task;
    }

    private WorkflowModel workflow(TaskModel... tasks) {
        WorkflowModel workflow = new WorkflowModel();
        workflow.setTasks(List.of(tasks));
        return workflow;
    }
}
