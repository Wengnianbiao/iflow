package com.helianhealth.iflow.core.execution.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowTask;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import com.helianhealth.iflow.core.utils.ParametersUtils;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WebServiceTaskMapperTest {

    @Test
    void consumesResolvedInputWithoutReparsingParametersOrTemplate() {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("workflow", Map.of("text", "${literal.business.text}"));
        parameters.put("soap", List.of(42, true));
        parameters.put("empty", null);
        Map<String, Object> resolved = Map.of("templateParameters", parameters,
                "soapTemplate", "<r>$.workflow.text</r>");
        WorkflowTask definition = WorkflowTask.builder().name("soap").type("WEBSERVICE")
                .taskReferenceName("soap").inputParameters(Map.of("uri", "${missing.input}")).build();

        TaskModel task = new WebServiceTaskMapper(new ParametersUtils(new ObjectMapper())).getMappedTasks(TaskMapperContext.builder()
                .workflowModel(new WorkflowModel()).workflowTask(definition)
                .taskInput(resolved).taskId("soap-1").retryCount(2).build()).get(0);

        assertEquals(resolved, task.getInputData());
        assertNotSame(resolved, task.getInputData());
        assertEquals(TaskModel.Status.IN_PROGRESS, task.getStatus());
        assertEquals(0, task.getStartTime());
        assertEquals(2, task.getRetryCount());
    }

    @Test
    void resolvesFrameworkExpressionsOnceWhenInvokedWithoutPreparedInput() {
        WorkflowModel workflow = new WorkflowModel();
        workflow.setInput(Map.of("patient", Map.of("name", "${literal.text}"), "xml", "<r>ready</r>"));
        WorkflowTask definition = WorkflowTask.builder().name("soap").type("WEBSERVICE")
                .taskReferenceName("soap").inputParameters(Map.of(
                        "soapTemplate", "${workflow.input.xml}",
                        "templateParameters", Map.of("patient", "${workflow.input.patient}"))).build();

        TaskModel task = new WebServiceTaskMapper(new ParametersUtils(new ObjectMapper())).getMappedTasks(TaskMapperContext.builder()
                .workflowModel(workflow).workflowTask(definition).taskId("soap-1").build()).get(0);

        assertEquals("<r>ready</r>", task.getInputData().get("soapTemplate"));
        assertEquals(Map.of("patient", Map.of("name", "${literal.text}")),
                task.getInputData().get("templateParameters"));
    }

    @Test
    void copiesPreparedInputAndPropagatesRetryAndForkContext() {
        Map<String, Object> resolved = new LinkedHashMap<>();
        resolved.put("uri", "http://example.test/soap");
        resolved.put("soapTemplate", null);
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId("workflow-1");
        workflow.setCorrelationId("patient-1");
        WorkflowTask definition = WorkflowTask.builder().name("soap").type("WEBSERVICE")
                .taskReferenceName("soap_ref").build();
        WebServiceTaskMapper mapper = new WebServiceTaskMapper(new ParametersUtils(new ObjectMapper()));

        TaskModel task = mapper.getMappedTasks(TaskMapperContext.builder()
                .workflowModel(workflow).workflowTask(definition).taskId("soap-1")
                .taskInput(resolved).retryCount(3).retryTaskId("soap-previous").forkTaskId("fork-1")
                .build()).get(0);

        assertEquals("WEBSERVICE", mapper.getTaskType());
        assertEquals("WEBSERVICE", task.getTaskType());
        assertEquals("patient-1", task.getCorrelationId());
        assertEquals("soap-previous", task.getRetriedTaskId());
        assertEquals("fork-1", task.getForkTaskId());
        assertEquals(3, task.getRetryCount());
        assertTrue(task.getInputData().containsKey("soapTemplate"));
        assertNull(task.getInputData().get("soapTemplate"));
        task.getInputData().put("uri", "changed");
        assertEquals("http://example.test/soap", resolved.get("uri"));
    }

    @Test
    void usesExplicitEmptyPreparedInputWithoutFallingBackToDefinition() {
        WorkflowTask definition = WorkflowTask.builder().name("soap").type("WEBSERVICE")
                .taskReferenceName("soap_ref").inputParameters(Map.of("uri", "ignored")).build();
        TaskModel task = new WebServiceTaskMapper(new ParametersUtils(new ObjectMapper())).getMappedTasks(TaskMapperContext.builder()
                .workflowModel(new WorkflowModel()).workflowTask(definition)
                .taskInput(Map.of()).taskId("soap-1").build()).get(0);

        assertTrue(task.getInputData().isEmpty());
        task.getInputData().put("uri", "later");
        assertEquals("ignored", definition.getInputParameters().get("uri"));
    }
}
