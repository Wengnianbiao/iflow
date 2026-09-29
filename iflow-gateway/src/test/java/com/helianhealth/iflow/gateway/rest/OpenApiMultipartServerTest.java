package com.helianhealth.iflow.gateway.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.iflow.common.domain.run.FileAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        classes = OpenApiMultipartServerTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "iflow.open-api.max-body-bytes=1024",
                "iflow.open-api.include-raw-body=true",
                "spring.servlet.multipart.max-file-size=1KB",
                "spring.servlet.multipart.max-request-size=1KB"
        })
class OpenApiMultipartServerTest {

    private static final AtomicInteger STARTS = new AtomicInteger();
    private static volatile Object capturedBody;
    private static volatile Map<String, Object> capturedFiles;
    private static volatile byte[] capturedFileBytes;
    private static volatile FileAccess capturedAccess;
    private static volatile String capturedRef;

    @LocalServerPort
    private int port;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @BeforeEach
    void reset() {
        STARTS.set(0);
        capturedBody = null;
        capturedFiles = null;
        capturedFileBytes = null;
        capturedAccess = null;
        capturedRef = null;
    }

    @Test
    @SuppressWarnings("unchecked")
    void realServletParsesBoundaryAndPreservesRawFileBytes() throws Exception {
        String boundary = "iflow-boundary";
        byte[] fileBytes = new byte[] {0, 1, 2, 3, (byte) 0xff};
        byte[] body = multipartBody(boundary, "张三", fileBytes, true);

        HttpResponse<String> response = send(
                "multipart/form-data; boundary=" + boundary,
                HttpRequest.BodyPublishers.ofByteArray(body));

        assertEquals(200, response.statusCode());
        assertEquals(1, STARTS.get());
        assertEquals(Map.of("name", "张三"), capturedBody);
        List<Map<String, Object>> descriptors = (List<Map<String, Object>>) capturedFiles.get("file");
        assertEquals(1, descriptors.size());
        assertEquals("raw.bin", descriptors.get(0).get("fileName"));
        assertArrayEquals(fileBytes, capturedFileBytes);
        assertThrows(IOException.class, () -> capturedAccess.open(capturedRef));
    }

    @Test
    void malformedMultipartReturns400WithoutStartingWorkflow() throws Exception {
        byte[] broken = multipartBody("actual-boundary", "value", new byte[] {1}, false);
        HttpResponse<String> response = send(
                "multipart/form-data; boundary=actual-boundary",
                HttpRequest.BodyPublishers.ofByteArray(broken));
        assertEquals(400, response.statusCode());
        assertEquals(0, STARTS.get());

        HttpResponse<String> missingBoundary = send(
                "multipart/form-data",
                HttpRequest.BodyPublishers.ofByteArray("body".getBytes(StandardCharsets.UTF_8)));
        assertEquals(400, missingBoundary.statusCode());
        assertEquals(0, STARTS.get());
    }

    @Test
    void chunkedMultipartOverLimitReturns413WithoutStartingWorkflow() throws Exception {
        String boundary = "large-boundary";
        byte[] body = multipartBody(boundary, "value", new byte[2048], true);
        HttpResponse<String> response = send(
                "multipart/form-data; boundary=" + boundary,
                HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(body)));
        assertEquals(413, response.statusCode());
        assertEquals(0, STARTS.get());
    }

    private HttpResponse<String> send(String contentType, HttpRequest.BodyPublisher publisher)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + "/hl-open-api/upload"))
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
            OpenApiRequestReader.class,
            OpenApiBodyLimitFilter.class,
            OpenApiMultipartExceptionHandler.class
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
                capturedBody = request.body();
                capturedFiles = request.files();
                capturedAccess = request.fileAccess();
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> files = (List<Map<String, Object>>) request.files().get("file");
                if (files != null && !files.isEmpty()) {
                    capturedRef = String.valueOf(files.get(0).get("fileRef"));
                    try (var input = request.fileAccess().open(capturedRef)) {
                        capturedFileBytes = input.readAllBytes();
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                }
                return "{}";
            };
        }
    }
}
