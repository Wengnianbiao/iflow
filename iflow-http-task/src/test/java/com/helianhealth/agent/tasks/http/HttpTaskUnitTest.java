package com.helianhealth.agent.tasks.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.agent.core.model.TaskModel;
import com.helianhealth.agent.core.model.WorkflowModel;
import com.helianhealth.agent.tasks.http.providers.RestTemplateProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * HttpTask 单元测试。
 *
 * <p>覆盖 Input 反序列化、accept 参数、http_request 键 / 平铺 input 两种形态。
 */
class HttpTaskUnitTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private HttpTask httpTask;
    private final WorkflowModel workflow = new WorkflowModel();

    @BeforeEach
    void setup() {
        RestTemplateProvider restTemplateProvider = mock(RestTemplateProvider.class);
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplateProvider.getRestTemplate(any())).thenReturn(restTemplate);
        when(restTemplate.exchange(
                        anyString(),
                        any(HttpMethod.class),
                        any(HttpEntity.class),
                        eq(String.class)))
                .thenReturn(ResponseEntity.ok("{}"));
        httpTask = new HttpTask(restTemplateProvider, OBJECT_MAPPER);
    }

    @Test
    void acceptDefaultValue() {
        HttpTask.Input input = new HttpTask.Input();
        assertEquals("application/json", input.getAccept());
        assertEquals(1, input.getAcceptList().size());
        assertEquals("application/json", input.getAcceptList().get(0));
    }

    @Test
    void acceptSingleString() {
        HttpTask.Input input = new HttpTask.Input();
        input.setAccept("text/plain");
        assertEquals("text/plain", input.getAccept());
        assertEquals(1, input.getAcceptList().size());
        assertEquals("text/plain", input.getAcceptList().get(0));
    }

    @Test
    void acceptMultipleValues() {
        HttpTask.Input input = new HttpTask.Input();
        input.setAccept(Arrays.asList("application/json", "text/plain", "application/xml"));
        assertEquals("application/json", input.getAccept());
        List<String> acceptList = input.getAcceptList();
        assertEquals(3, acceptList.size());
        assertEquals("application/json", acceptList.get(0));
        assertEquals("text/plain", acceptList.get(1));
        assertEquals("application/xml", acceptList.get(2));
    }

    @Test
    void acceptSingleStringDeserialization() {
        Map<String, Object> inputMap = new HashMap<>();
        inputMap.put("uri", "http://example.com");
        inputMap.put("method", "GET");
        inputMap.put("accept", "text/html");

        HttpTask.Input input = OBJECT_MAPPER.convertValue(inputMap, HttpTask.Input.class);
        assertEquals("text/html", input.getAccept());
        assertEquals(1, input.getAcceptList().size());
        assertEquals("text/html", input.getAcceptList().get(0));
    }

    @Test
    void acceptListDeserialization() {
        Map<String, Object> inputMap = new HashMap<>();
        inputMap.put("uri", "http://example.com");
        inputMap.put("method", "GET");
        inputMap.put("accept", Arrays.asList("application/json", "text/plain"));

        HttpTask.Input input = OBJECT_MAPPER.convertValue(inputMap, HttpTask.Input.class);
        assertEquals("application/json", input.getAccept());
        List<String> acceptList = input.getAcceptList();
        assertEquals(2, acceptList.size());
        assertEquals("application/json", acceptList.get(0));
        assertEquals("text/plain", acceptList.get(1));
    }

    @Test
    void acceptMediaTypeParsing() {
        HttpTask.Input input = new HttpTask.Input();
        input.setAccept(Arrays.asList("application/json", "text/plain"));
        for (String accept : input.getAcceptList()) {
            assertNotNull(MediaType.valueOf(accept));
        }
    }

    @Test
    void inputWithHttpRequestKey() {
        TaskModel task = new TaskModel();
        Map<String, Object> httpRequest = new HashMap<>();
        httpRequest.put("uri", "http://example.com");
        httpRequest.put("method", "GET");
        httpRequest.put("accept", "text/html");
        task.getInputData().put(HttpTask.REQUEST_PARAMETER_NAME, httpRequest);

        httpTask.execute(workflow, task, null);
        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertNotNull(task.getOutputData().get("response"));
    }

    @Test
    void inputWithoutHttpRequestKey() {
        TaskModel task = new TaskModel();
        task.getInputData().put("uri", "http://example.com");
        task.getInputData().put("method", "GET");
        task.getInputData().put("accept", "text/html");

        httpTask.execute(workflow, task, null);
        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertNotNull(task.getOutputData().get("response"));
    }

    @Test
    void inputWithoutHttpRequestKeyAndMissingUri() {
        TaskModel task = new TaskModel();
        task.getInputData().put("method", "GET");

        httpTask.execute(workflow, task, null);
        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertTrue(task.getReasonForInCompletion().contains("Missing HTTP URI"));
    }

    @Test
    void inputWithoutHttpRequestKeyAndMissingMethod() {
        TaskModel task = new TaskModel();
        task.getInputData().put("uri", "http://example.com");

        httpTask.execute(workflow, task, null);
        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertTrue(task.getReasonForInCompletion().contains("No HTTP method specified"));
    }

    @Test
    void inputDirectWithListAccept() {
        TaskModel task = new TaskModel();
        task.getInputData().put("uri", "http://example.com");
        task.getInputData().put("method", "GET");
        task.getInputData().put("accept", Arrays.asList("application/json", "text/plain"));

        httpTask.execute(workflow, task, null);
        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertNotNull(task.getOutputData().get("response"));
    }

    @Test
    void inputHttpRequestKeyWithListAccept() {
        TaskModel task = new TaskModel();
        Map<String, Object> httpRequest = new HashMap<>();
        httpRequest.put("uri", "http://example.com");
        httpRequest.put("method", "GET");
        httpRequest.put("accept", Arrays.asList("application/json", "application/xml"));
        task.getInputData().put(HttpTask.REQUEST_PARAMETER_NAME, httpRequest);

        httpTask.execute(workflow, task, null);
        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertNotNull(task.getOutputData().get("response"));
    }

    @Test
    void emptyInputFails() {
        TaskModel task = new TaskModel();
        httpTask.execute(workflow, task, null);
        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertTrue(task.getReasonForInCompletion().contains("Missing HTTP URI"));
    }
}
