package com.helianhealth.iflow.core.execution.tasks;

import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TerminateTaskTest {

    private final Terminate terminate = new Terminate();

    @ParameterizedTest
    @ValueSource(strings = {"COMPLETED", "FAILED", "TERMINATED"})
    void acceptsTerminalStatusAndPreservesRawOutputWithoutFinalizingWorkflow(String status) {
        WorkflowModel workflow = new WorkflowModel();
        TaskModel task = task(Map.of("terminationStatus", status,
                "terminationReason", "business rule", "workflowOutput", List.of(1, 2)));

        terminate.start(workflow, task, null);

        assertTrue(Terminate.isValidStatus(status));
        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals(List.of(1, 2), task.getOutputData());
        assertNull(task.getReasonForInCompletion());
        assertEquals(WorkflowModel.Status.RUNNING, workflow.getStatus());
        assertEquals("TERMINATE", terminate.getTaskType());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"RUNNING", "TIMED_OUT", "completed", " COMPLETED ", "unknown"})
    void rejectsInvalidStatusWithDiagnostic(String status) {
        Map<String, Object> input = new HashMap<>();
        input.put("terminationStatus", status);
        TaskModel task = task(input);

        terminate.start(new WorkflowModel(), task, null);

        assertFalse(Terminate.isValidStatus(status));
        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertEquals("invalid terminationStatus: " + status, task.getReasonForInCompletion());
    }

    @Test
    void leavesOutputNullWhenWorkflowOutputIsOmitted() {
        TaskModel task = task(Map.of("terminationStatus", "COMPLETED"));
        task.setOutputData(Map.of("stale", true));

        terminate.start(new WorkflowModel(), task, null);

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertNull(task.getOutputData());
    }

    private TaskModel task(Map<String, Object> input) {
        TaskModel task = new TaskModel();
        task.setStatus(TaskModel.Status.IN_PROGRESS);
        task.setInputData(input);
        return task;
    }
}
