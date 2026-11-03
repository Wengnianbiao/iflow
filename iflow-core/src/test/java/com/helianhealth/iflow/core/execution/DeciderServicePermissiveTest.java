package com.helianhealth.iflow.core.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowDef;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowTask;
import com.helianhealth.iflow.core.exception.TerminateWorkflowException;
import com.helianhealth.iflow.core.execution.tasks.SystemTaskRegistry;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import com.helianhealth.iflow.core.utils.IDGenerator;
import com.helianhealth.iflow.core.utils.ParametersUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DeciderServicePermissiveTest {
    private final DeciderService decider = new DeciderService(new IDGenerator(),
            new ParametersUtils(new ObjectMapper()), new SystemTaskRegistry(Set.of()), Map.of());

    @ParameterizedTest
    @EnumSource(value = TaskModel.Status.class,
            names = {"FAILED", "TIMED_OUT", "CANCELED", "FAILED_WITH_TERMINAL_ERROR"})
    void finalFailureIsPreservedWithoutAJoin(TaskModel.Status status) {
        WorkflowTask definition = definition("request");
        TaskModel failed = task(definition, "failed", status, 0);
        WorkflowModel workflow = workflow(List.of(definition), failed);

        TerminateWorkflowException error = assertThrows(TerminateWorkflowException.class,
                () -> decider.decide(workflow));

        assertEquals(WorkflowModel.Status.FAILED, error.getWorkflowStatus());
        assertTrue(error.getMessage().contains("Task failed failed with status: " + status));
        assertTrue(error.getMessage().contains("failure-failed"));
        assertEquals(status, failed.getStatus());
    }

    @Test
    void historicalFailuresDoNotOverrideSuccessfulLatestAttempt() {
        WorkflowTask definition = definition("request");
        TaskModel failed = task(definition, "old", TaskModel.Status.FAILED, 0);
        failed.setRetried(true);
        TaskModel successful = task(definition, "latest", TaskModel.Status.COMPLETED, 1);
        successful.setRetriedTaskId("old");
        // 持久化返回的列表顺序不决定哪次尝试是最新。
        WorkflowModel workflow = workflow(List.of(definition), successful, failed);

        assertTrue(decider.decide(workflow).isComplete);
        assertEquals(TaskModel.Status.FAILED, failed.getStatus());
    }

    @Test
    void aggregatesOnlyLatestFailuresForEachReference() {
        WorkflowTask first = definition("first");
        WorkflowTask second = definition("second");
        TaskModel old = task(first, "obsolete", TaskModel.Status.FAILED, 0);
        old.setRetried(true);
        TaskModel latest = task(first, "latest", TaskModel.Status.FAILED, 1);
        TaskModel other = task(second, "other", TaskModel.Status.FAILED, 0);
        WorkflowModel workflow = workflow(List.of(first, second), latest, other, old);

        TerminateWorkflowException error = assertThrows(TerminateWorkflowException.class,
                () -> decider.decide(workflow));

        assertTrue(error.getMessage().contains("Task latest failed"));
        assertTrue(error.getMessage().contains("failure-latest"));
        assertTrue(error.getMessage().contains("Task other failed"));
        assertTrue(error.getMessage().contains("failure-other"));
        assertFalse(error.getMessage().contains("obsolete"));
    }

    @Test
    void doesNotFailBeforeRemainingTasksFinish() {
        WorkflowTask first = definition("first");
        WorkflowTask second = definition("second");
        second.setPermissive(false);
        TaskModel failed = task(first, "failed", TaskModel.Status.FAILED, 0);
        TaskModel running = task(second, "running", TaskModel.Status.IN_PROGRESS, 0);
        running.setExecuted(false);
        WorkflowModel workflow = workflow(List.of(first, second), failed, running);

        assertFalse(decider.decide(workflow).isComplete);
        assertEquals(WorkflowModel.Status.RUNNING, workflow.getStatus());
        assertEquals(TaskModel.Status.FAILED, failed.getStatus());
    }

    @Test
    void successfulTerminateDoesNotHidePermissiveFailure() {
        WorkflowTask first = definition("first");
        WorkflowTask terminateDefinition = WorkflowTask.builder().name("terminate").taskReferenceName("terminate")
                .type("TERMINATE").build();
        TaskModel failed = task(first, "failed", TaskModel.Status.FAILED, 0);
        TaskModel terminate = task(terminateDefinition, "terminate", TaskModel.Status.COMPLETED, 0);
        WorkflowModel workflow = workflow(List.of(first, terminateDefinition), failed, terminate);

        assertEquals(WorkflowModel.Status.FAILED, assertThrows(TerminateWorkflowException.class,
                () -> decider.decide(workflow)).getWorkflowStatus());
    }

    private static WorkflowTask definition(String reference) {
        return WorkflowTask.builder().name(reference).taskReferenceName(reference).type("HTTP")
                .permissive(true).retryCount(0).build();
    }

    private static TaskModel task(WorkflowTask definition, String id, TaskModel.Status status, int retryCount) {
        TaskModel task = new TaskModel();
        task.setWorkflowTask(definition);
        task.setTaskId(id);
        task.setWorkflowInstanceId("workflow");
        task.setReferenceTaskName(definition.getTaskReferenceName());
        task.setTaskType(definition.getType());
        task.setRetryCount(retryCount);
        task.setStatus(status);
        task.setExecuted(true);
        if (!status.isSuccessful()) {
            task.setReasonForInCompletion("failure-" + id);
        }
        return task;
    }

    private static WorkflowModel workflow(List<WorkflowTask> definitions, TaskModel... tasks) {
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("workflow");
        workflow.setStatus(WorkflowModel.Status.RUNNING);
        workflow.setStartTime(System.currentTimeMillis());
        workflow.setWorkflowDefinition(WorkflowDef.builder().route("permissive").tasks(definitions).build());
        workflow.setTasks(new ArrayList<>(List.of(tasks)));
        return workflow;
    }
}
