package com.helianhealth.iflow.gateway.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.iflow.common.domain.run.StartWorkflowRequest;
import com.helianhealth.iflow.core.model.WorkflowModel;
import com.helianhealth.iflow.core.service.WorkflowExecutionService;
import com.helianhealth.iflow.core.utils.ParametersUtils;
import com.helianhealth.iflow.gateway.jarvis.JarvisController;
import com.helianhealth.iflow.gateway.jarvis.JarvisRequestReader;
import com.helianhealth.iflow.gateway.jarvis.JarvisServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        classes = OpenApiMultipartServerTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "iflow.open-api.max-body-bytes=1024",
                "spring.servlet.multipart.max-file-size=1KB",
                "spring.servlet.multipart.max-request-size=1KB"
        })
class OpenApiMultipartServerTest {

    private static final AtomicInteger STARTS = new AtomicInteger();
    private static volatile Map<String, Object> capturedInput;
    private static volatile StartWorkflowRequest jarvisRequest;

    @LocalServerPort
    private int port;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @BeforeEach
    void reset() {
        STARTS.set(0);
        capturedInput = null;
        jarvisRequest = null;
    }

    @ParameterizedTest
    @ValueSource(strings = {"/hl-open-api/upload", "/pacs/upload"})
    @SuppressWarnings("unchecked")
    void realServletParsesBoundaryAndPassesFileBytesAsBase64(String path) throws Exception {
        String boundary = "iflow-boundary";
        byte[] fileBytes = new byte[] {0, 1, 2, 3, (byte) 0xff};
        byte[] body = multipartBody(boundary, "张三", fileBytes, true);

        HttpResponse<String> response = send(path,
                "multipart/form-data; boundary=" + boundary,
                HttpRequest.BodyPublishers.ofByteArray(body));

        assertEquals(200, response.statusCode());
        assertEquals(1, STARTS.get());
        assertEquals(Map.of("name", "张三"), capturedInput.get("body"));
        assertTrue(capturedInput.containsKey("rawBody"));
        assertNull(capturedInput.get("rawBody"));
        List<Map<String, Object>> descriptors = (List<Map<String, Object>>)
                ((Map<String, Object>) capturedInput.get("files")).get("file");
        assertEquals(1, descriptors.size());
        assertEquals("raw.bin", descriptors.get(0).get("fileName"));
        assertEquals("application/octet-stream", descriptors.get(0).get("contentType"));
        assertEquals((long) fileBytes.length, descriptors.get(0).get("size"));
        assertEquals(4, descriptors.get(0).size());
        assertEquals(Base64.getEncoder().encodeToString(fileBytes), descriptors.get(0).get("contentBase64"));
        assertArrayEquals(fileBytes, Base64.getDecoder().decode((String) descriptors.get(0).get("contentBase64")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/hl-open-api/upload", "/pacs/upload"})
    void malformedMultipartReturns400WithoutStartingWorkflow(String path) throws Exception {
        byte[] broken = multipartBody("actual-boundary", "value", new byte[] {1}, false);
        HttpResponse<String> response = send(path,
                "multipart/form-data; boundary=actual-boundary",
                HttpRequest.BodyPublishers.ofByteArray(broken));
        assertEquals(400, response.statusCode());
        assertEquals(0, STARTS.get());

        HttpResponse<String> missingBoundary = send(path,
                "multipart/form-data",
                HttpRequest.BodyPublishers.ofByteArray("body".getBytes(StandardCharsets.UTF_8)));
        assertEquals(400, missingBoundary.statusCode());
        assertEquals(0, STARTS.get());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/hl-open-api/upload", "/pacs/upload"})
    void chunkedMultipartOverLimitReturns413WithoutStartingWorkflow(String path) throws Exception {
        String boundary = "large-boundary";
        byte[] body = multipartBody(boundary, "value", new byte[2048], true);
        HttpResponse<String> response = send(path,
                "multipart/form-data; boundary=" + boundary,
                HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(body)));
        assertEquals(413, response.statusCode());
        assertEquals(0, STARTS.get());
    }

    @ParameterizedTest
    @ValueSource(strings = {"application/json", "application/vnd.iflow+json"})
    void jarvisRoutesByBusinessMethodAndPassesItsJsonBody(String contentType) throws Exception {
        String json = """
                {"BusinessMethod":"AddPacsApply","BusinessCode":"PACS","data":{"id":"001","name":"张三"},
                 "ServiceProviderType":"HIS","PatientCode":"0001","OutFeeItemIdList":["01"],
                 "ApplyNoList":["02"],"BarCodeList":["03"],"AuditTime":"2026-10-27","QueryType":1,
                 "extra":null,"body":"business-value"}
                """;
        HttpResponse<String> response = send("/HLOpenApi/Hjk?trace=001", contentType,
                HttpRequest.BodyPublishers.ofString(json));
        assertEquals(200, response.statusCode());
        assertEquals(Map.of("ok", true), new ObjectMapper().readValue(response.body(), Map.class));
        assertEquals("AddPacsApply", jarvisRequest.getRoute());
        Map<String, Object> input = jarvisRequest.getInput();
        assertEquals(Map.of("body", new ObjectMapper().readValue(json, Map.class), "rawBody", json), input);
        assertEquals(0, STARTS.get());

        WorkflowModel workflow = new WorkflowModel();
        workflow.setInput(input);
        assertEquals(Map.of("code", "PACS", "id", "001"),
                new ParametersUtils(new ObjectMapper()).getTaskInput(Map.of(
                        "code", "${workflow.input.body.BusinessCode}",
                        "id", "${workflow.input.body.data.id}"), workflow, "test"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{invalid}", "[]", "\"text\"", "null", ""})
    void jarvisRejectsInvalidJsonBodyWithoutStartingWorkflow(String body) throws Exception {
        HttpResponse<String> response = send("/HLOpenApi/Hjk", "application/json",
                HttpRequest.BodyPublishers.ofString(body));
        assertEquals(400, response.statusCode());
        assertNull(jarvisRequest);
        assertEquals(0, STARTS.get());
    }

    @Test
    void jarvisJsonBodyIsIndependentOfOpenApiBodyLimit() throws Exception {
        byte[] json = ("{\"BusinessMethod\":\"AddPacsApply\",\"data\":{\"text\":\"" + "x".repeat(2048) + "\"}}")
                .getBytes(StandardCharsets.UTF_8);
        for (HttpRequest.BodyPublisher publisher : List.of(
                HttpRequest.BodyPublishers.ofByteArray(json),
                HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(json)))) {
            HttpResponse<String> response = send("/HLOpenApi/Hjk", "application/json", publisher);
            assertEquals(200, response.statusCode());
            assertEquals("AddPacsApply", jarvisRequest.getRoute());
            assertEquals(Map.of("text", "x".repeat(2048)),
                    ((Map<?, ?>) jarvisRequest.getInput().get("body")).get("data"));
            assertEquals(new String(json, StandardCharsets.UTF_8), jarvisRequest.getInput().get("rawBody"));
            assertEquals(0, STARTS.get());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/probe", "/custom-controller"})
    void explicitControllersKeepTheirOwnHandling(String path) throws Exception {
        HttpResponse<String> response = send(path, "text/plain",
                HttpRequest.BodyPublishers.ofString("x".repeat(2048)));
        assertEquals(200, response.statusCode());
        assertEquals("controller:2048", response.body());
        assertEquals(0, STARTS.get());
    }

    @Test
    void customPathRejectsOversizedKnownLengthBodyBeforeWorkflowStart() throws Exception {
        HttpResponse<String> response = send("/pacs/apply-status", "text/plain",
                HttpRequest.BodyPublishers.ofString("x".repeat(2048)));
        assertEquals(413, response.statusCode());
        assertEquals(0, STARTS.get());
    }

    @Test
    void staticResourcesAndHomePageRemainAccessible() throws Exception {
        for (String path : List.of("/", "/index.html", "/assets/gateway-routing-test.txt")) {
            HttpResponse<String> response = client.send(HttpRequest.newBuilder(
                            URI.create("http://127.0.0.1:" + port + path)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), path);
            assertTrue(response.body().contains("gateway-routing-test"), path);
        }
        assertEquals(0, STARTS.get());
    }

    private HttpResponse<String> send(String path, String contentType, HttpRequest.BodyPublisher publisher)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", contentType)
                .POST(publisher)
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static byte[] multipartBody(
            String boundary, String fieldValue, byte[] fileBytes, boolean closeBoundary)
            throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"name\"\r\n"
                + "Content-Type: text/plain; charset=UTF-8\r\n\r\n"
                + fieldValue + "\r\n"
                + "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"raw.bin\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        output.write(fileBytes);
        output.write("\r\n".getBytes(StandardCharsets.US_ASCII));
        if (closeBoundary) {
            output.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII));
        }
        return output.toByteArray();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
    @Import({
            OpenApiController.class,
            OpenApiPaths.class,
            OpenApiRequestReader.class,
            OpenApiBodyLimitFilter.class,
            OpenApiMultipartExceptionHandler.class,
            JarvisController.class,
            JarvisRequestReader.class,
            JarvisServiceImpl.class,
            ProbeController.class
    })
    static class TestApplication {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        OpenApiService openApiService() {
            return request -> {
                STARTS.incrementAndGet();
                capturedInput = request.toWorkflowInput();
                return "{}";
            };
        }

        @Bean
        WorkflowExecutionService workflowExecutionService() {
            return new WorkflowExecutionService() {
                @Override
                public String startWorkflow(StartWorkflowRequest request) {
                    throw new AssertionError("Jarvis 必须同步等待工作流输出");
                }

                @Override
                public WorkflowModel startWorkflowSync(StartWorkflowRequest request) {
                    jarvisRequest = request;
                    WorkflowModel result = new WorkflowModel();
                    result.setStatus(WorkflowModel.Status.COMPLETED);
                    result.setOutput(Map.of("ok", true));
                    return result;
                }
            };
        }
    }

    @RestController
    static class ProbeController {
        @PostMapping({"/api/probe", "/custom-controller"})
        String invoke(@RequestBody String body) {
            return "controller:" + body.length();
        }
    }
}
