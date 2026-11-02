package com.helianhealth.iflow.persistence.mysql.dao;

import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowDef;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MySqlExecutionDAOTest {

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private PreparedStatement statement;

    @Mock
    private ResultSet resultSet;

    private MySqlExecutionDAO dao;
    private MySqlExecutionJsonSupport jsonSupport;

    @BeforeEach
    void setUp() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        lenient().when(statement.executeUpdate()).thenReturn(1);
        dao = new MySqlExecutionDAO(dataSource);
        jsonSupport = new MySqlExecutionJsonSupport(new com.fasterxml.jackson.databind.ObjectMapper());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void runningInstancesRemainPendingOnCreationAndUpdate(boolean create) throws Exception {
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("running");
        workflow.setStatus(WorkflowModel.Status.RUNNING);
        workflow.setWorkflowDefinition(WorkflowDef.builder().route("demo").build());
        if (create) {
            dao.createWorkflow(workflow);
        } else {
            dao.updateWorkflow(workflow);
        }
        verify(connection).prepareStatement(contains("INSERT IGNORE INTO workflow_pending"));
        verify(connection, never()).prepareStatement(contains("DELETE FROM workflow_pending"));
    }

    @Test
    void unfinishedAndRunningScansUsePendingRecords() throws Exception {
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);
        assertTrue(dao.getUnfinishedWorkflowIds().isEmpty());
        assertTrue(dao.getRunningWorkflowIds().isEmpty());
        verify(connection, times(2)).prepareStatement("SELECT workflow_id FROM workflow_pending");
    }

    @Test
    void updateTaskStampsTerminalEndTimeBeforePersistingPayload() throws Exception {
        TaskModel task = new TaskModel();
        task.setTaskId("task-1");
        task.setWorkflowInstanceId("wf-1");
        task.setReferenceTaskName("ref");
        task.setTaskType("HTTP");
        task.setStatus(TaskModel.Status.COMPLETED);

        dao.updateTask(task);

        assertTrue(task.getUpdateTime() > 0);
        assertTrue(task.getEndTime() > 0);

        ArgumentCaptor<String> payloads = ArgumentCaptor.forClass(String.class);
        verify(statement, atLeastOnce()).setString(eq(1), payloads.capture());
        TaskModel persisted = jsonSupport.fromTaskPayload(payloads.getAllValues().get(0));
        assertEquals(task.getEndTime(), persisted.getEndTime());
    }

    @Test
    void updateWorkflowStampsTerminalEndTimeBeforePersistingPayload() throws Exception {
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("wf-1");
        workflow.setWorkflowDefinition(WorkflowDef.builder().route("demo").version(1).build());
        workflow.setStatus(WorkflowModel.Status.COMPLETED);

        dao.updateWorkflow(workflow);

        assertTrue(workflow.getUpdateTime() > 0);
        assertTrue(workflow.getEndTime() > 0);

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(statement).setString(eq(2), payload.capture());
        WorkflowModel persisted = jsonSupport.fromWorkflowPayload(payload.getValue());
        assertEquals(workflow.getEndTime(), persisted.getEndTime());
    }

    @Test
    void updateWorkflowRefreshesTerminalEndTime() throws Exception {
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("wf-1");
        workflow.setWorkflowDefinition(WorkflowDef.builder().route("demo").version(1).build());
        workflow.setStatus(WorkflowModel.Status.COMPLETED);
        workflow.setEndTime(1);

        dao.updateWorkflow(workflow);

        assertTrue(workflow.getEndTime() > 1);
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(statement).setString(eq(2), payload.capture());
        WorkflowModel persisted = jsonSupport.fromWorkflowPayload(payload.getValue());
        assertEquals(workflow.getEndTime(), persisted.getEndTime());
    }

    @Test
    void createTasksSkipsDuplicateScheduleKey() throws Exception {
        when(statement.executeUpdate()).thenReturn(0);
        TaskModel task = scheduledTask(TaskModel.Status.IN_PROGRESS);

        List<TaskModel> created = dao.createTasks(List.of(task));

        assertTrue(created.isEmpty());
        verify(connection, times(1)).prepareStatement(contains("task_scheduled"));
        verify(connection, never()).prepareStatement(contains("INSERT INTO task (task_id, json_data)"));
    }

    @ParameterizedTest
    @EnumSource(value = TaskModel.Status.class, names = {"SCHEDULED", "IN_PROGRESS"})
    void createTasksIndexesPendingTask(TaskModel.Status status) throws Exception {
        when(statement.executeUpdate()).thenReturn(1, 1, 1, 1, 1);
        TaskModel task = scheduledTask(status);

        List<TaskModel> created = dao.createTasks(List.of(task));

        assertEquals(1, created.size());
        assertTrue(task.getScheduledTime() > 0);
        assertEquals(task.getScheduledTime(), task.getFirstScheduledTime());
        verify(connection).prepareStatement(contains("INSERT IGNORE INTO task_in_progress"));
        verify(statement, atLeastOnce()).setString(eq(1), eq(task.getTaskDefName()));
    }

    @ParameterizedTest
    @EnumSource(value = TaskModel.Status.class, names = {"SCHEDULED", "IN_PROGRESS"})
    void updateTaskKeepsPendingIndexUntilTerminal(TaskModel.Status status) throws Exception {
        dao.updateTask(scheduledTask(status));

        verify(connection).prepareStatement(contains("DELETE FROM task_in_progress"));
        verify(connection).prepareStatement(contains("INSERT IGNORE INTO task_in_progress"));
    }

    @Test
    void terminalTaskIsRemovedFromPendingIndex() throws Exception {
        dao.updateTask(scheduledTask(TaskModel.Status.COMPLETED));

        verify(connection).prepareStatement(contains("DELETE FROM task_in_progress"));
        verify(connection, never()).prepareStatement(contains("INSERT IGNORE INTO task_in_progress"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void pendingQueriesIncludeScheduledAndRunningButExcludeTerminal(boolean byTaskType) throws Exception {
        TaskModel scheduled = scheduledTask(TaskModel.Status.SCHEDULED);
        scheduled.setSeq(2);
        scheduled.setScheduledTime(200);
        TaskModel running = scheduledTask(TaskModel.Status.IN_PROGRESS);
        running.setSeq(1);
        running.setScheduledTime(100);
        TaskModel terminal = scheduledTask(TaskModel.Status.COMPLETED);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, true, true, false);
        when(resultSet.getString("task_id")).thenReturn("scheduled", "running", "terminal");
        when(resultSet.getString("json_data")).thenReturn(
                jsonSupport.toTaskPayload(scheduled),
                jsonSupport.toTaskPayload(running),
                jsonSupport.toTaskPayload(terminal));

        List<TaskModel> pending = byTaskType
                ? dao.getPendingTasksForTaskType("HTTP")
                : dao.getPendingTasks();

        assertEquals(List.of("running", "scheduled"), pending.stream().map(TaskModel::getTaskId).toList());
        assertEquals(List.of(TaskModel.Status.IN_PROGRESS, TaskModel.Status.SCHEDULED),
                pending.stream().map(TaskModel::getStatus).toList());
    }

    @Test
    void getTasksForWorkflowDoesNotOrderByMissingSeqColumn() throws Exception {
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        assertTrue(dao.getTasksForWorkflow("wf-1").isEmpty());

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(connection).prepareStatement(sql.capture());
        assertTrue(sql.getValue().contains("workflow_to_task"));
        assertFalse(sql.getValue().contains("t.seq"));
    }

    private static TaskModel scheduledTask(TaskModel.Status status) {
        TaskModel task = new TaskModel();
        task.setTaskId("task-1");
        task.setWorkflowInstanceId("wf-1");
        task.setReferenceTaskName("ref");
        task.setTaskType("HTTP");
        task.setTaskDefName("HTTP");
        task.setStatus(status);
        return task;
    }
}
