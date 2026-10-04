package com.helianhealth.iflow.tasks.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import com.helianhealth.iflow.tasks.http.providers.RestTemplateProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** HTTP 任务状态、失败诊断及请求/响应契约，不依赖外部服务。 */
class HttpTaskExecutionTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WorkflowModel workflow = new WorkflowModel();

    @ParameterizedTest
    @ValueSource(ints = {200, 201, 204, 299})
    void allSuccessfulStatusesCompleteAndExposeResponse(int statusCode) {
        HttpTask.HttpResponse response = response(statusCode, "ok");
        TaskModel task = task();

        returning(response).execute(workflow, task, null);

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals(response.asMap(), task.getOutputData());
    }

    @ParameterizedTest
    @ValueSource(ints = {199, 300, 404, 500})
    void unsuccessfulStatusKeepsRemoteBodyAndResponse(int statusCode) {
        HttpTask.HttpResponse response = response(statusCode, Map.of("message", "rejected"));
        TaskModel task = task();

        returning(response).execute(workflow, task, null);

        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertTrue(task.getReasonForInCompletion().contains("rejected"));
        assertEquals(response.asMap(), task.getOutputData());
    }

    @Test
    void unsuccessfulStatusWithoutBodyIncludesStatusInDiagnostic() {
        TaskModel task = task();

        returning(response(503, null)).execute(workflow, task, null);

        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertTrue(task.getReasonForInCompletion().contains("503"));
        assertNull(task.outputAsMap().get("body"));
    }

    @Test
    void exceptionWithoutMessageUsesExceptionTypeAndPreservesBusinessOutput() {
        HttpTask failing = new HttpTask(mock(RestTemplateProvider.class), objectMapper) {
            @Override
            protected HttpResponse httpCall(Input input) throws IOException {
                throw new IOException();
            }
        };
        TaskModel task = task();
        task.setOutputData(Map.of("retained", "previous output"));

        failing.execute(workflow, task, null);

        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertEquals("HTTP 调用失败：IOException", task.getReasonForInCompletion());
        assertEquals(Map.of("retained", "previous output"), task.getOutputData());
    }

    @Test
    @SuppressWarnings("unchecked")
    void sendsBodyMediaTypesHeadersAndTimeoutsToProvider() {
        RestTemplateProvider provider = mock(RestTemplateProvider.class);
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(provider.getRestTemplate(any())).thenAnswer(invocation -> {
            HttpTask.Input input = invocation.getArgument(0);
            assertEquals(1234, input.getConnectionTimeOut());
            assertEquals(5678, input.getReadTimeOut());
            return restTemplate;
        });
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.set("X-Response", "trace");
        when(restTemplate.exchange(eq("http://example.com/api"), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(String.class))).thenAnswer(invocation -> {
            HttpEntity<?> request = invocation.getArgument(2);
            assertEquals(Map.of("id", 42), request.getBody());
            assertEquals(MediaType.APPLICATION_XML, request.getHeaders().getContentType());
            assertEquals(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN), request.getHeaders().getAccept());
            assertEquals("42", request.getHeaders().getFirst("X-Request-Id"));
            assertFalse(request.getHeaders().containsKey("X-Ignored"));
            return ResponseEntity.ok().headers(responseHeaders).body("{\"accepted\":true}");
        });
        TaskModel task = task();
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put("X-Request-Id", 42);
        headers.put("X-Ignored", null);
        task.getInputData().putAll(Map.of("method", "POST", "body", Map.of("id", 42),
                "contentType", "application/xml", "accept", List.of("application/json", "text/plain"),
                "headers", headers, "connectionTimeOut", 1234, "readTimeOut", 5678));

        new HttpTask(provider, objectMapper).execute(workflow, task, null);

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus(), task.getReasonForInCompletion());
        assertEquals(Map.of("accepted", true), task.outputAsMap().get("body"));
        assertEquals(responseHeaders, task.outputAsMap().get("headers"));
        verify(provider).getRestTemplate(any());
    }

    @Test
    void restClientFailureBecomesTaskDiagnostic() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new ResourceAccessException("read timed out"));
        TaskModel task = task();

        new HttpTask(input -> restTemplate, objectMapper).execute(workflow, task, null);

        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertEquals("HTTP 调用失败：read timed out", task.getReasonForInCompletion());
        assertTrue(task.outputAsMap().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"INVALID", ""})
    void invalidContentTypeFailsWithoutExchangingRequest(String contentType) {
        RestTemplate restTemplate = mock(RestTemplate.class);
        TaskModel task = task();
        task.getInputData().put("contentType", contentType);

        new HttpTask(input -> restTemplate, objectMapper).execute(workflow, task, null);

        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertTrue(task.getReasonForInCompletion().startsWith("HTTP 调用失败："));
        verifyNoInteractions(restTemplate);
    }

    private TaskModel task() {
        TaskModel task = new TaskModel();
        task.setInputData(new LinkedHashMap<>(Map.of("uri", "http://example.com/api", "method", "GET")));
        return task;
    }

    private HttpTask returning(HttpTask.HttpResponse response) {
        return new HttpTask(mock(RestTemplateProvider.class), objectMapper) {
            @Override
            protected HttpResponse httpCall(Input input) {
                return response;
            }
        };
    }

    private static HttpTask.HttpResponse response(int statusCode, Object body) {
        HttpTask.HttpResponse response = new HttpTask.HttpResponse();
        response.statusCode = statusCode;
        response.reasonPhrase = "remote response";
        response.body = body;
        response.headers = new HttpHeaders();
        return response;
    }
}
