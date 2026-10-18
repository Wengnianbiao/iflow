package com.helianhealth.iflow.core.execution.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowTask;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import com.helianhealth.iflow.core.utils.ParametersUtils;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JdbcTaskMapperTest {

    @Test
    void preservesAlreadyResolvedPayloadWithoutEvaluatingItsBusinessTextAgain() {
        Map<String, Object> resolved = Map.of("payload", Map.of("text", "${literal.business.text}"),
                "sqlTemplate", "SELECT $.text");
        WorkflowTask definition = WorkflowTask.builder().name("jdbc").type("JDBC")
                .taskReferenceName("jdbc_ref").inputParameters(Map.of("payload", "${missing.input}")).build();
        TaskModel task = new JdbcTaskMapper(new ParametersUtils(new ObjectMapper())).getMappedTasks(TaskMapperContext.builder()
                .workflowModel(new WorkflowModel()).workflowTask(definition)
                .taskInput(resolved).taskId("jdbc-1").retryCount(2).build()).get(0);
        assertEquals(resolved, task.getInputData());
        assertNotSame(resolved, task.getInputData());
        assertEquals(TaskModel.Status.IN_PROGRESS, task.getStatus());
        assertEquals(0, task.getStartTime());
        assertEquals(2, task.getRetryCount());
    }

    @Test
    void resolvesWorkflowInputOnceAndPreservesTaskLocalSqlPaths() {
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("workflow-1");
        workflow.setInput(Map.of("patient", Map.of("name", "${literal.text}"), "database", "hospital"));
        WorkflowTask definition = WorkflowTask.builder().name("jdbc").type("JDBC")
                .taskReferenceName("jdbc_ref").inputParameters(Map.of(
                        "payload", "${workflow.input.patient}",
                        "datasource", "${workflow.input.database}",
                        "sqlTemplate", "SELECT $.name")).build();
        JdbcTaskMapper mapper = new JdbcTaskMapper(new ParametersUtils(new ObjectMapper()));

        TaskModel task = mapper.getMappedTasks(TaskMapperContext.builder()
                .workflowModel(workflow).workflowTask(definition).taskId("jdbc-1")
                .retryCount(3).retryTaskId("jdbc-previous").forkTaskId("fork-1").build()).get(0);

        assertEquals("JDBC", mapper.getTaskType());
        assertEquals("JDBC", task.getTaskType());
        assertEquals("hospital", task.getInputData().get("datasource"));
        assertEquals(Map.of("name", "${literal.text}"), task.getInputData().get("payload"));
        assertEquals("SELECT $.name", task.getInputData().get("sqlTemplate"));
        assertEquals("workflow-1", task.getWorkflowInstanceId());
        assertEquals("jdbc-previous", task.getRetriedTaskId());
        assertEquals("fork-1", task.getForkTaskId());
        assertEquals(3, task.getRetryCount());
    }

    @Test
    void usesExplicitEmptyPreparedInputWithoutFallingBackToDefinition() {
        WorkflowTask definition = WorkflowTask.builder().name("jdbc").type("JDBC")
                .taskReferenceName("jdbc_ref").inputParameters(Map.of("datasource", "ignored")).build();
        TaskModel task = new JdbcTaskMapper(new ParametersUtils(new ObjectMapper())).getMappedTasks(TaskMapperContext.builder()
                .workflowModel(new WorkflowModel()).workflowTask(definition)
                .taskInput(Map.of()).taskId("jdbc-1").build()).get(0);

        assertTrue(task.getInputData().isEmpty());
        task.getInputData().put("datasource", "later");
        assertEquals("ignored", definition.getInputParameters().get("datasource"));
    }
}
