package com.helianhealth.agent.mysql.dao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.agent.core.model.TaskModel;
import com.helianhealth.agent.core.model.WorkflowModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MySqlExecutionJsonSupportTest {

    private final MySqlExecutionJsonSupport support =
            new MySqlExecutionJsonSupport(new ObjectMapper());

    @Test
    void workflowPayloadOmitsTasks() {
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("wf-1");
        workflow.setStatus(WorkflowModel.Status.RUNNING);
        workflow.setCorrelationId("corr-1");
        workflow.getTasks().add(new TaskModel());

        String json = support.toWorkflowPayload(workflow);
        WorkflowModel restored = support.fromWorkflowPayload(json);

        assertNotNull(restored);
        assertEquals("wf-1", restored.getWorkflowId());
        assertEquals(WorkflowModel.Status.RUNNING, restored.getStatus());
        assertTrue(restored.getTasks().isEmpty());
        assertTrue(json.contains("\"workflowId\":\"wf-1\""));
    }

    @Test
    void legacyWorkflowPayloadWithStartTimeIsRestored() {
        String legacyJson =
                """
                {"workflowId":"wf-legacy","status":"RUNNING","startTime":12345}
                """;

        WorkflowModel restored = support.fromWorkflowPayload(legacyJson);

        assertNotNull(restored);
        assertEquals("wf-legacy", restored.getWorkflowId());
        assertEquals(WorkflowModel.Status.RUNNING, restored.getStatus());
        assertEquals(12345L, restored.getStartTime());
    }

    @Test
    void taskKeyUsesReferenceNameAndRetryCount() {
        TaskModel task = new TaskModel();
        task.setReferenceTaskName("noop");
        task.setRetryCount(2);

        assertEquals("noop_2", MySqlExecutionJsonSupport.taskKey(task));
    }

    @Test
    void taskPayloadRoundTrip() {
        TaskModel task = new TaskModel();
        task.setTaskId("t-1");
        task.setReferenceTaskName("http");
        task.setRetryCount(0);
        task.setStatus(TaskModel.Status.COMPLETED);
        task.setForkTaskId("fork-1");

        TaskModel restored = support.fromTaskPayload(support.toTaskPayload(task));

        assertEquals("t-1", restored.getTaskId());
        assertEquals("http", restored.getReferenceTaskName());
        assertEquals(TaskModel.Status.COMPLETED, restored.getStatus());
        assertEquals("fork-1", restored.getForkTaskId());
    }
}
