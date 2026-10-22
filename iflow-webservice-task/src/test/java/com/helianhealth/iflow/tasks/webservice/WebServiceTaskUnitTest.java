package com.helianhealth.iflow.tasks.webservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import com.helianhealth.iflow.tasks.webservice.providers.DefaultSoapClientProvider;
import com.helianhealth.iflow.tasks.webservice.providers.SoapClientProvider;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * WebServiceTask 单元测试：任务输入、局部渲染及网络调用的边界。
 */
class WebServiceTaskUnitTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private WebServiceTask webServiceTask;
    private final WorkflowModel workflow = new WorkflowModel();

    @BeforeEach
    void setup() {
        DefaultSoapClientProvider provider = new DefaultSoapClientProvider();
        webServiceTask = new WebServiceTask(provider, OBJECT_MAPPER);
    }

    @Test
    void executesAsynchronously() {
        assertTrue(webServiceTask.isAsync());
    }

    @Test
    void deserializeInputParameters() {
        Map<String, Object> inputMap = new HashMap<>();
        inputMap.put("uri", "http://example.com/soap");
        inputMap.put("soapTemplate", "<soap:Envelope><id>42</id></soap:Envelope>");
        inputMap.put("body", "<request>42</request>");
        inputMap.put("headers", Map.of("SOAPAction", "GetUser"));
        inputMap.put("connectionTimeOut", 1000);
        inputMap.put("readTimeOut", 6000);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("patient", Map.of("id", 42));
        parameters.put("empty", null);
        inputMap.put("templateParameters", parameters);

        WebServiceTask.Input input = OBJECT_MAPPER.convertValue(inputMap, WebServiceTask.Input.class);

        assertEquals("http://example.com/soap", input.getUri());
        assertEquals("<soap:Envelope><id>42</id></soap:Envelope>", input.getSoapTemplate());
        assertEquals("<request>42</request>", input.getBody());
        assertEquals("GetUser", input.getHeaders().get("SOAPAction"));
        assertEquals(1000, input.getConnectionTimeOut());
        assertEquals(6000, input.getReadTimeOut());
        assertEquals(parameters, input.getTemplateParameters());
    }

    @Test
    void startFailsWhenMissingSoapTemplate() {
        TaskModel task = new TaskModel();
        task.setInputData(Map.of("uri", "http://example.com/soap"));

        webServiceTask.start(workflow, task, null);

        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertTrue(task.getReasonForInCompletion().contains("Missing soapTemplate"));
    }

    @Test
    void defaultContentTypeIsXml() {
        WebServiceTask.Input input = new WebServiceTask.Input();
        assertEquals("text/xml;charset=UTF-8", input.getContentType());
    }

    @ParameterizedTest
    @ValueSource(strings = {"<r>$.missing</r>", "<r>$.patient</r>", "<r>", "<r>${$.patient.name}</r>", "<soap:Envelope/>"})
    void renderingFailureStopsBeforeCreatingClient(String template) {
        SoapClientProvider provider = mock(SoapClientProvider.class);
        TaskModel task = new TaskModel();
        task.setInputData(Map.of("uri", "http://example.com/soap", "soapTemplate", template,
                "templateParameters", Map.of("patient", Map.of("name", "张三"))));

        new WebServiceTask(provider, OBJECT_MAPPER).start(workflow, task, null);

        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertTrue(task.getReasonForInCompletion().contains("SOAP"));
        verifyNoInteractions(provider);
        assertFalse(task.getInputData().containsKey("body"));
        assertFalse(task.outputAsMap().containsKey("error"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void sendsRenderedXmlAndPreservesHeadersTimeoutsAndResponseContract() throws Exception {
        SoapClientProvider provider = mock(SoapClientProvider.class);
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        when(provider.createHttpClient(any())).thenAnswer(invocation -> {
            WebServiceTask.Input input = invocation.getArgument(0);
            assertEquals(1200, input.getConnectionTimeOut());
            assertEquals(4500, input.getReadTimeOut());
            assertEquals("<r id=\"A&amp;B\">张三&lt;&amp;</r>", input.getBody());
            assertEquals("<r id=\"$.requestId\">$.patient.name</r>", input.getSoapTemplate());
            return client;
        });
        when(client.execute(any(ClassicHttpRequest.class), any(HttpClientResponseHandler.class)))
                .thenAnswer(invocation -> {
                    ClassicHttpRequest request = invocation.getArgument(0);
                    assertEquals("POST", request.getMethod());
                    assertEquals("http://example.com/soap", request.getUri().toString());
                    assertEquals("<r id=\"A&amp;B\">张三&lt;&amp;</r>", EntityUtils.toString(request.getEntity()));
                    assertEquals("GetUser", request.getFirstHeader("SOAPAction").getValue());
                    assertEquals("text/xml; charset=UTF-8", request.getFirstHeader("Content-Type").getValue());
                    assertEquals("trace-42", request.getFirstHeader("X-Trace").getValue());
                    BasicClassicHttpResponse response = new BasicClassicHttpResponse(200, "OK");
                    response.setEntity(new StringEntity("<result>ok</result>"));
                    response.addHeader("X-Response", "soap");
                    HttpClientResponseHandler<WebServiceTask.WebServiceResponse> handler = invocation.getArgument(1);
                    return handler.handleResponse(response);
                });
        TaskModel task = new TaskModel();
        String template = "<r id=\"$.requestId\">$.patient.name</r>";
        task.setInputData(Map.of("uri", "http://example.com/soap", "soapTemplate", template,
                "templateParameters", Map.of("requestId", "A&B", "patient", Map.of("name", "张三<&")),
                "headers", Map.of("soapaction", "GetUser", "content-type", "text/xml; charset=UTF-8", "X-Trace", "trace-42"),
                "connectionTimeOut", 1200, "readTimeOut", 4500));

        new WebServiceTask(provider, OBJECT_MAPPER).start(workflow, task, null);

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus(), task.getReasonForInCompletion());
        assertEquals(template, task.getInputData().get("soapTemplate"));
        assertEquals("<r id=\"A&amp;B\">张三&lt;&amp;</r>", task.getInputData().get("body"));
        assertEquals(Map.of("body", "<result>ok</result>", "statusCode", 200, "reasonPhrase", "OK",
                "headers", Map.of("X-Response", "soap")), task.getOutputData());
        verify(client).close();
    }

    @Test
    void retainsRenderedBodyOnCallFailureAndRegeneratesItOnReexecution() {
        WebServiceTask failingTask =
                new WebServiceTask(new DefaultSoapClientProvider(), OBJECT_MAPPER) {
                    @Override
                    protected WebServiceResponse soapCall(Input input) throws IOException {
                        throw new IOException("connection refused");
                    }
                };
        TaskModel task = new TaskModel();
        String template = "<request>$.name</request>";
        task.setInputData(Map.of("uri", "http://example.com/soap", "soapTemplate", template,
                "templateParameters", Map.of("name", "张三<&"), "body", "stale body"));

        failingTask.start(workflow, task, null);

        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertTrue(task.getReasonForInCompletion().contains("connection refused"));
        assertEquals(template, task.getInputData().get("soapTemplate"));
        assertEquals("<request>张三&lt;&amp;</request>", task.getInputData().get("body"));

        task.getInputData().put("templateParameters", Map.of("name", "李四"));
        failingTask.start(workflow, task, null);

        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertTrue(task.getReasonForInCompletion().contains("connection refused"));
        assertEquals(template, task.getInputData().get("soapTemplate"));
        assertEquals("<request>李四</request>", task.getInputData().get("body"));
    }

    @Test
    void malformedParameterRootFailsAsTaskDiagnostic() {
        SoapClientProvider provider = mock(SoapClientProvider.class);
        TaskModel task = new TaskModel();
        task.setInputData(Map.of("uri", "http://example.com/soap", "soapTemplate", "<r/>",
                "templateParameters", java.util.List.of(1)));

        new WebServiceTask(provider, OBJECT_MAPPER).start(workflow, task, null);

        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertTrue(task.getReasonForInCompletion().contains("templateParameters"));
        verifyNoInteractions(provider);
    }

    @Test
    void writesResponseFieldsToOutputRoot() {
        WebServiceTask successfulTask =
                new WebServiceTask(new DefaultSoapClientProvider(), OBJECT_MAPPER) {
                    @Override
                    protected WebServiceResponse soapCall(Input input) {
                        WebServiceResponse response = new WebServiceResponse();
                        response.statusCode = 200;
                        response.reasonPhrase = "OK";
                        response.body = "<result>ok</result>";
                        response.headers = Map.of();
                        return response;
                    }
                };
        TaskModel task = new TaskModel();
        task.setInputData(
                Map.of(
                        "uri", "http://example.com/soap",
                        "soapTemplate", "<request/>"));

        successfulTask.start(workflow, task, null);

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals("<result>ok</result>", task.outputAsMap().get("body"));
        assertEquals(200, task.outputAsMap().get("statusCode"));
        assertFalse(task.outputAsMap().containsKey("result"));
        assertFalse(task.outputAsMap().containsKey("response"));
    }
}
