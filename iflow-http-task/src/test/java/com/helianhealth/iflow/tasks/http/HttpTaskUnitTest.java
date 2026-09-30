package com.helianhealth.iflow.tasks.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
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
 * HttpTask 入参测试：accept 反序列化、平铺 input、缺参失败。
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
        assertTrue(task.getReasonForInCompletion().contains("缺少请求地址 uri"));
    }

    @Test
    void inputWithoutHttpRequestKeyAndMissingMethod() {
        TaskModel task = new TaskModel();
        task.getInputData().put("uri", "http://example.com");

        httpTask.execute(workflow, task, null);
        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertTrue(task.getReasonForInCompletion().contains("未指定 HTTP 方法"));
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
    void emptyInputFails() {
        TaskModel task = new TaskModel();
        httpTask.execute(workflow, task, null);
        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertTrue(task.getReasonForInCompletion().contains("缺少请求地址 uri"));
    }

    @Test
    void cancelMarksTaskCanceled() {
        TaskModel task = new TaskModel();
        httpTask.cancel(workflow, task, null);
        assertEquals(TaskModel.Status.CANCELED, task.getStatus());
    }
}
