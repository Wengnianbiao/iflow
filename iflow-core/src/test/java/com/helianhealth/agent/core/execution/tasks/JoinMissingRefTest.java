package com.helianhealth.agent.core.execution.tasks;

import com.helianhealth.agent.core.model.TaskModel;
import com.helianhealth.agent.core.model.WorkflowModel;
import org.junit.jupiter.api.Test;

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
}
