package com.helianhealth.agent.tasks.webservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.agent.core.model.TaskModel;
import com.helianhealth.agent.core.model.WorkflowModel;
import com.helianhealth.agent.tasks.webservice.providers.DefaultSoapClientProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WebServiceTask 单元测试：扁平 inputParameters → Input（与 HTTP 同构，body → soapTemplate）。
 */
class WebServiceTaskUnitTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private WebServiceTask webServiceTask;
    private final WorkflowModel workflow = new WorkflowModel();

    @BeforeEach
    void setup() {
        DefaultSoapClientProvider provider = new DefaultSoapClientProvider(5000, 8000);
        webServiceTask = new WebServiceTask(provider, OBJECT_MAPPER);
    }

    @Test
    void deserializeInputParameters() {
        Map<String, Object> inputMap = new HashMap<>();
        inputMap.put("uri", "http://example.com/soap");
        inputMap.put("soapTemplate", "<soap:Envelope><id>42</id></soap:Envelope>");
        inputMap.put("headers", Map.of("SOAPAction", "GetUser"));
        inputMap.put("connectionTimeOut", 1000);
        inputMap.put("readTimeOut", 6000);

        WebServiceTask.Input input = OBJECT_MAPPER.convertValue(inputMap, WebServiceTask.Input.class);

        assertEquals("http://example.com/soap", input.getUri());
        assertEquals("<soap:Envelope><id>42</id></soap:Envelope>", input.getSoapTemplate());
        assertEquals("GetUser", input.getHeaders().get("SOAPAction"));
        assertEquals(1000, input.getConnectionTimeOut());
        assertEquals(6000, input.getReadTimeOut());
    }

    @Test
    void startFailsWhenMissingSoapTemplate() {
        TaskModel task = new TaskModel();
        task.setInputData(Map.of("uri", "http://example.com/soap"));

        webServiceTask.execute(workflow, task, null);

        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertTrue(task.getReasonForInCompletion().contains("Missing soapTemplate"));
    }

    @Test
    void defaultContentTypeIsXml() {
        WebServiceTask.Input input = new WebServiceTask.Input();
        assertEquals("text/xml;charset=UTF-8", input.getContentType());
    }
}
