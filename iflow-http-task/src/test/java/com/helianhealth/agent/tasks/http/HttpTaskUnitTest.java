package com.helianhealth.agent.tasks.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.agent.core.model.TaskModel;
import com.helianhealth.agent.core.model.WorkflowModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        httpTask =
                new HttpTask(
                        input -> {
                            throw new AssertionError("HTTP provider should not be used by this unit test");
                        },
                        OBJECT_MAPPER) {
                    @Override
                    protected HttpResponse httpCall(Input input) {
                        HttpResponse response = new HttpResponse();
                        response.body = Map.of();
                        response.statusCode = 200;
                        response.reasonPhrase = "OK";
                        return response;
                    }
                };
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
        task.addOutput("stale", true);

        httpTask.execute(workflow, task, null);
        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals(200, task.outputAsMap().get("statusCode"));
        assertEquals(Map.of(), task.outputAsMap().get("body"));
        assertFalse(task.outputAsMap().containsKey("result"));
        assertFalse(task.outputAsMap().containsKey("response"));
        assertFalse(task.outputAsMap().containsKey("stale"));
    }

    @Test
    void inputWithoutHttpRequestKey() {
        TaskModel task = new TaskModel();
        task.getInputData().put("uri", "http://example.com");
        task.getInputData().put("method", "GET");
        task.getInputData().put("accept", "text/html");

        httpTask.execute(workflow, task, null);
        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals(200, task.outputAsMap().get("statusCode"));
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
        assertEquals(200, task.outputAsMap().get("statusCode"));
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
        assertEquals(200, task.outputAsMap().get("statusCode"));
    }

    @Test
    void emptyInputFails() {
        TaskModel task = new TaskModel();
        httpTask.execute(workflow, task, null);
        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertTrue(task.getReasonForInCompletion().contains("Missing HTTP URI"));
    }
}
