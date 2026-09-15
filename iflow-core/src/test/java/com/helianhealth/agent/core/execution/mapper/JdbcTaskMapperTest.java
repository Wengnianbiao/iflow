package com.helianhealth.agent.core.execution.mapper;

import com.helianhealth.agent.common.domain.metadata.workflow.WorkflowTask;
import com.helianhealth.agent.core.model.TaskModel;
import com.helianhealth.agent.core.model.WorkflowModel;
import com.helianhealth.agent.core.utils.ParametersUtils;
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
        TaskModel task = new JdbcTaskMapper(new ParametersUtils()).getMappedTasks(TaskMapperContext.builder()
                .workflowModel(new WorkflowModel()).workflowTask(definition)
                .taskInput(resolved).taskId("jdbc-1").retryCount(2).build()).get(0);
        assertEquals(resolved, task.getInputData());
        assertNotSame(resolved, task.getInputData());
        assertEquals(TaskModel.Status.SCHEDULED, task.getStatus());
        assertEquals(2, task.getRetryCount());
    }
}
