package com.helianhealth.iflow.tasks.webservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import com.helianhealth.iflow.tasks.webservice.providers.SoapClientProvider;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** SOAP 任务状态、请求默认值与客户端释放，不依赖外部 SOAP 服务。 */
class WebServiceTaskExecutionTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WorkflowModel workflow = new WorkflowModel();

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void missingOrBlankUriFailsBeforeCreatingClient(String uri) {
        SoapClientProvider provider = mock(SoapClientProvider.class);
        TaskModel task = task();
        task.getInputData().put("uri", uri);

        new WebServiceTask(provider, objectMapper).execute(workflow, task, null);

        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertTrue(task.getReasonForInCompletion().contains("Missing WebService URI"));
        verifyNoInteractions(provider);
        assertFalse(task.getInputData().containsKey("body"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"\t "})
    void missingOrBlankTemplateFailsBeforeCreatingClient(String template) {
        SoapClientProvider provider = mock(SoapClientProvider.class);
        TaskModel task = task();
        task.getInputData().put("soapTemplate", template);

        new WebServiceTask(provider, objectMapper).execute(workflow, task, null);

        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertEquals("Missing soapTemplate", task.getReasonForInCompletion());
        verifyNoInteractions(provider);
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 201, 204, 299})
    void successfulStatusWithoutEntityCompletesAndClosesClient(int statusCode) throws Exception {
        CloseableHttpClient client = responding(statusCode, null);
        TaskModel task = task();

        new WebServiceTask(providerFor(client), objectMapper).execute(workflow, task, null);

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus(), task.getReasonForInCompletion());
        assertEquals(statusCode, task.outputAsMap().get("statusCode"));
        assertNull(task.outputAsMap().get("body"));
        assertEquals("remote response", task.outputAsMap().get("reasonPhrase"));
        assertEquals(Map.of("X-Response", "trace"), task.outputAsMap().get("headers"));
        verify(client).close();
    }

    @ParameterizedTest
    @ValueSource(ints = {199, 300, 400, 500})
    void failureStatusKeepsFaultBodyAndClosesClient(int statusCode) throws Exception {
        String fault = "<fault>service unavailable</fault>";
        CloseableHttpClient client = responding(statusCode, fault);
        TaskModel task = task();

        new WebServiceTask(providerFor(client), objectMapper).execute(workflow, task, null);

        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertEquals(fault, task.getReasonForInCompletion());
        assertEquals(fault, task.outputAsMap().get("body"));
        assertEquals(statusCode, task.outputAsMap().get("statusCode"));
        verify(client).close();
    }

    @Test
    void failureStatusWithoutEntityReportsNoResponse() throws Exception {
        CloseableHttpClient client = responding(503, null);
        TaskModel task = task();

        new WebServiceTask(providerFor(client), objectMapper).execute(workflow, task, null);

        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertEquals("No response from the remote service", task.getReasonForInCompletion());
        assertEquals(503, task.outputAsMap().get("statusCode"));
        assertNull(task.outputAsMap().get("body"));
        verify(client).close();
    }

    @Test
    @SuppressWarnings("unchecked")
    void defaultHeadersAndRenderedBodyAreSentAndNullCustomHeadersAreSkipped() throws Exception {
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        when(client.execute(any(ClassicHttpRequest.class), any(HttpClientResponseHandler.class)))
                .thenAnswer(invocation -> {
                    ClassicHttpRequest request = invocation.getArgument(0);
                    assertEquals("POST", request.getMethod());
                    assertEquals("<request>张三</request>", EntityUtils.toString(request.getEntity()));
                    assertEquals("text/xml;charset=UTF-8", request.getFirstHeader("Content-Type").getValue());
                    assertEquals("", request.getFirstHeader("SOAPAction").getValue());
                    assertEquals("42", request.getFirstHeader("X-Request-Id").getValue());
                    assertNull(request.getFirstHeader("X-Ignored"));
                    HttpClientResponseHandler<WebServiceTask.WebServiceResponse> handler = invocation.getArgument(1);
                    return handler.handleResponse(new BasicClassicHttpResponse(200, "OK"));
                });
        TaskModel task = task();
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put("X-Request-Id", 42);
        headers.put("X-Ignored", null);
        task.getInputData().putAll(Map.of("soapTemplate", "<request>$.name</request>",
                "templateParameters", Map.of("name", "张三"), "headers", headers));

        new WebServiceTask(providerFor(client), objectMapper).execute(workflow, task, null);

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus(), task.getReasonForInCompletion());
        assertEquals("<request>$.name</request>", task.getInputData().get("soapTemplate"));
        assertEquals("<request>张三</request>", task.getInputData().get("body"));
        verify(client).close();
    }

    @Test
    void clientExecutionFailureClosesClientAndPreservesBusinessOutput() throws Exception {
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        when(client.execute(any(ClassicHttpRequest.class), any(HttpClientResponseHandler.class)))
                .thenThrow(new IOException("read timed out"));
        TaskModel task = task();
        task.setOutputData(Map.of("retained", "previous output"));

        new WebServiceTask(providerFor(client), objectMapper).execute(workflow, task, null);

        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertTrue(task.getReasonForInCompletion().contains("read timed out"));
        assertEquals("<request/>", task.getInputData().get("body"));
        assertEquals(Map.of("retained", "previous output"), task.getOutputData());
        verify(client).close();
    }

    @Test
    void cancelMarksTaskCanceledWithoutCreatingClient() {
        SoapClientProvider provider = mock(SoapClientProvider.class);
        TaskModel task = task();

        new WebServiceTask(provider, objectMapper).cancel(workflow, task, null);

        assertEquals(TaskModel.Status.CANCELED, task.getStatus());
        verifyNoInteractions(provider);
    }

    private SoapClientProvider providerFor(CloseableHttpClient client) {
        SoapClientProvider provider = mock(SoapClientProvider.class);
        when(provider.createHttpClient(any())).thenReturn(client);
        return provider;
    }

    @SuppressWarnings("unchecked")
    private CloseableHttpClient responding(int statusCode, String body) throws Exception {
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        when(client.execute(any(ClassicHttpRequest.class), any(HttpClientResponseHandler.class)))
                .thenAnswer(invocation -> {
                    BasicClassicHttpResponse response = new BasicClassicHttpResponse(statusCode, "remote response");
                    if (body != null) {
                        response.setEntity(new StringEntity(body));
                    }
                    response.addHeader("X-Response", "trace");
                    HttpClientResponseHandler<WebServiceTask.WebServiceResponse> handler = invocation.getArgument(1);
                    return handler.handleResponse(response);
                });
        return client;
    }

    private TaskModel task() {
        TaskModel task = new TaskModel();
        task.setInputData(new LinkedHashMap<>(Map.of("uri", "http://example.com/soap", "soapTemplate", "<request/>")));
        return task;
    }
}
