package com.helianhealth.iflow.core.execution.mapper;

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

        TaskModel task = new WebServiceTaskMapper(new ParametersUtils()).getMappedTasks(TaskMapperContext.builder()
                .workflowModel(new WorkflowModel()).workflowTask(definition)
                .taskInput(resolved).taskId("soap-1").retryCount(2).build()).get(0);

        assertEquals(resolved, task.getInputData());
        assertNotSame(resolved, task.getInputData());
        assertEquals(TaskModel.Status.SCHEDULED, task.getStatus());
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

        TaskModel task = new WebServiceTaskMapper(new ParametersUtils()).getMappedTasks(TaskMapperContext.builder()
                .workflowModel(workflow).workflowTask(definition).taskId("soap-1").build()).get(0);

        assertEquals("<r>ready</r>", task.getInputData().get("soapTemplate"));
        assertEquals(Map.of("patient", Map.of("name", "${literal.text}")),
                task.getInputData().get("templateParameters"));
    }
}
