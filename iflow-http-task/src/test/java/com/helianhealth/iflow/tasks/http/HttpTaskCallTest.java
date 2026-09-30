package com.helianhealth.iflow.tasks.http;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import com.helianhealth.iflow.tasks.http.providers.DefaultRestTemplateProvider;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HttpTask 真实调用测试
 * POST 回显、204、非 2xx、文本 / 数字 / JSON 响应，以及建连失败和读取超时。
 */
class HttpTaskCallTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final String ERROR_RESPONSE = "Something went wrong!";
    private static final String TEXT_RESPONSE = "Text Response";
    private static final double NUM_RESPONSE = 42.42d;

    private HttpServer server;
    private ExecutorService executor;
    private String baseUrl;
    private HttpTask httpTask;
    private final WorkflowModel workflow = new WorkflowModel();
    private byte[] jsonResponse;

    @BeforeEach
    void setup() throws Exception {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("key", "value1");
        json.put("num", 42);
        json.put("SomeKey", null);
        jsonResponse = OBJECT_MAPPER.writeValueAsBytes(json);

        executor = Executors.newCachedThreadPool();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(executor);
        server.createContext("/post", this::handlePost);
        server.createContext("/post2", exchange -> respond(exchange, 204, null, null));
        server.createContext("/failure", exchange -> respond(exchange, 500, "text/plain", ERROR_RESPONSE.getBytes(StandardCharsets.UTF_8)));
        server.createContext("/text", exchange -> respond(exchange, 200, "text/plain", TEXT_RESPONSE.getBytes(StandardCharsets.UTF_8)));
        server.createContext("/numeric", exchange -> respond(exchange, 200, "text/plain", String.valueOf(NUM_RESPONSE).getBytes(StandardCharsets.UTF_8)));
        server.createContext("/json", exchange -> respond(exchange, 200, "application/json", jsonResponse));
        server.createContext("/array", exchange -> respond(exchange, 200, "application/json", "[1,\"a\"]".getBytes(StandardCharsets.UTF_8)));
        server.createContext("/slow", exchange -> {
            drain(exchange);
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
            respond(exchange, 200, "text/plain", "late".getBytes(StandardCharsets.UTF_8));
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        httpTask = new HttpTask(
                new DefaultRestTemplateProvider(OBJECT_MAPPER, Duration.ofSeconds(2), Duration.ofSeconds(2)),
                OBJECT_MAPPER);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    void postEchoesRequestKeys() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("input_key1", "value1");
        body.put("input_key2", 45.3d);
        body.put("someKey", null);

        TaskModel task = task("/post", "POST");
        task.getInputData().put("body", body);
        task.getInputData().put("headers", Map.of("X-Request-Id", "trace-1"));

        httpTask.execute(workflow, task, null);

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus(), task.getReasonForInCompletion());
        assertEquals(200, task.outputAsMap().get("statusCode"));
        Object response = task.outputAsMap().get("body");
        assertInstanceOf(Map.class, response);
        assertEquals(Set.of("input_key1", "input_key2", "someKey"), ((Map<?, ?>) response).keySet());
    }

    @Test
    void postNoContent() {
        TaskModel task = task("/post2", "POST");
        task.getInputData().put("body", Map.of("input_key1", "value1"));

        httpTask.execute(workflow, task, null);

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus(), task.getReasonForInCompletion());
        assertEquals(204, task.outputAsMap().get("statusCode"));
        assertNull(task.outputAsMap().get("body"));
    }

    @Test
    void failureIncludesRemoteBody() {
        TaskModel task = task("/failure", "GET");

        httpTask.execute(workflow, task, null);

        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertTrue(task.getReasonForInCompletion().contains("HTTP 调用失败"));
        assertTrue(task.getReasonForInCompletion().contains(ERROR_RESPONSE));
    }

    @Test
    void textGet() {
        TaskModel task = task("/text", "GET");

        httpTask.execute(workflow, task, null);

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus(), task.getReasonForInCompletion());
        assertEquals(TEXT_RESPONSE, task.outputAsMap().get("body"));
    }

    @Test
    void numberGet() {
        TaskModel task = task("/numeric", "GET");

        httpTask.execute(workflow, task, null);

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus(), task.getReasonForInCompletion());
        Object response = task.outputAsMap().get("body");
        assertEquals(NUM_RESPONSE, response);
        assertInstanceOf(Number.class, response);
    }

    @Test
    void jsonGetKeepsNulls() {
        TaskModel task = task("/json", "GET");

        httpTask.execute(workflow, task, null);

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus(), task.getReasonForInCompletion());
        Object response = task.outputAsMap().get("body");
        assertInstanceOf(Map.class, response);
        Map<?, ?> map = (Map<?, ?>) response;
        assertEquals("value1", map.get("key"));
        assertEquals(42, ((Number) map.get("num")).intValue());
        assertTrue(map.containsKey("SomeKey"));
        assertNull(map.get("SomeKey"));
    }

    @Test
    void jsonArrayGet() {
        TaskModel task = task("/array", "GET");

        httpTask.execute(workflow, task, null);

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus(), task.getReasonForInCompletion());
        Object response = task.outputAsMap().get("body");
        assertInstanceOf(List.class, response);
        List<?> list = (List<?>) response;
        assertEquals(1, ((Number) list.get(0)).intValue());
        assertEquals("a", list.get(1));
    }

    @Test
    void connectionRefusedFails() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        TaskModel task = new TaskModel();
        task.getInputData().put("uri", "http://127.0.0.1:" + port);
        task.getInputData().put("method", "GET");
        task.getInputData().put("connectionTimeOut", 1000);

        httpTask.execute(workflow, task, null);

        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertTrue(task.getReasonForInCompletion().contains("HTTP 调用失败"));
    }

    @Test
    @Timeout(value = 4, unit = TimeUnit.SECONDS)
    void readTimeoutFails() {
        TaskModel task = task("/slow", "GET");
        task.getInputData().put("readTimeOut", 500);

        httpTask.execute(workflow, task, null);

        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertTrue(task.getReasonForInCompletion().contains("HTTP 调用失败"));
    }

    private TaskModel task(String path, String method) {
        TaskModel task = new TaskModel();
        task.getInputData().put("uri", baseUrl + path);
        task.getInputData().put("method", method);
        return task;
    }

    private void handlePost(HttpExchange exchange) throws IOException {
        byte[] requestBody = drain(exchange);
        assertEquals("trace-1", exchange.getRequestHeaders().getFirst("X-Request-Id"));
        assertTrue(exchange.getRequestHeaders().getFirst("Content-Type").startsWith("application/json"));
        Map<String, Object> reqBody = OBJECT_MAPPER.readValue(requestBody, MAP_TYPE);
        Map<String, Object> respBody = new LinkedHashMap<>();
        reqBody.keySet().forEach(key -> respBody.put(key, key));
        respond(exchange, 200, "application/json", OBJECT_MAPPER.writeValueAsBytes(respBody));
    }

    private static byte[] drain(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            return in.readAllBytes();
        }
    }

    private static void respond(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        try {
            if (contentType != null) {
                exchange.getResponseHeaders().set("Content-Type", contentType);
            }
            if (body == null) {
                exchange.sendResponseHeaders(status, -1);
                return;
            }
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
        } finally {
            exchange.close();
        }
    }
}
