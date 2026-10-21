package com.helianhealth.iflow.core.dal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.iflow.core.dao.ExecutionDAO;
import com.helianhealth.iflow.core.exception.TransientException;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExecutionDAOFacadeTest {

    @Test
    void repeatedRegistrationPreservesRunningTaskAndAllowsDistinctRetry() {
        ExecutionDAO dao = mock(ExecutionDAO.class);
        when(dao.createTasks(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        ExecutionDAOFacade facade = new ExecutionDAOFacade(dao, new ObjectMapper());
        WorkflowModel workflow = new WorkflowModel();
        TaskModel running = task("running", "body", 0);
        running.setStatus(TaskModel.Status.IN_PROGRESS);
        running.setSeq(7);
        running.setStartTime(45);
        workflow.getTasks().add(running);

        TaskModel duplicate = task("duplicate", "body", 0);
        TaskModel retry = task("retry", "body", 1);
        List<TaskModel> candidates = List.of(duplicate, retry, task("duplicate-retry", "body", 1));

        List<TaskModel> registered = facade.registerTasks(workflow, candidates);
        assertEquals(List.of(retry), registered);
        assertSame(running, workflow.getTasks().get(0));
        assertEquals(TaskModel.Status.IN_PROGRESS, running.getStatus());
        assertEquals(45, running.getStartTime());
        assertEquals(TaskModel.Status.SCHEDULED, retry.getStatus());
        assertEquals(0, retry.getStartTime());
        assertEquals(8, retry.getSeq());

        assertEquals(List.of(), facade.registerTasks(workflow, candidates));
        assertEquals(List.of(running, retry), workflow.getTasks());
        verify(dao, times(1)).createTasks(anyList());
        verify(dao, times(1)).updateWorkflow(workflow);
    }

    @Test
    void incompletePersistenceDoesNotPublishTasksToWorkflowRuntime() {
        ExecutionDAO dao = mock(ExecutionDAO.class);
        ExecutionDAOFacade facade = new ExecutionDAOFacade(dao, new ObjectMapper());
        WorkflowModel workflow = new WorkflowModel();
        TaskModel existing = task("existing", "loop", 0);
        workflow.getTasks().add(existing);
        TaskModel first = task("first", "first", 0);
        TaskModel second = task("second", "second", 0);
        when(dao.createTasks(anyList())).thenReturn(List.of(first));

        assertThrows(TransientException.class,
                () -> facade.registerTasks(workflow, List.of(first, second)));

        assertEquals(List.of(existing), workflow.getTasks());
        verify(dao, never()).updateWorkflow(any(WorkflowModel.class));
    }

    private TaskModel task(String id, String referenceName, int retryCount) {
        TaskModel task = new TaskModel();
        task.setTaskId(id);
        task.setReferenceTaskName(referenceName);
        task.setRetryCount(retryCount);
        return task;
    }
}
