package com.helianhealth.iflow.gateway.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.iflow.common.domain.run.StartWorkflowRequest;
import com.helianhealth.iflow.core.model.WorkflowModel;
import com.helianhealth.iflow.core.utils.ParametersUtils;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockPart;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OpenApiControllerTest {
    private static final String PATH = "/hl-open-api/query-lis-apply";
    private StartWorkflowRequest started;
    private int starts;
    private MockMvc mvc;
    private RuntimeException executionFailure;

    @BeforeEach
    void setup() {
        started = null;
        starts = 0;
        executionFailure = null;
        mvc = mvc(OpenApiRequestReader.DEFAULT_MAX_BODY_BYTES, false);
    }

    private MockMvc mvc(int limit, boolean raw, Filter... filters) {
        OpenApiService service = new OpenApiServiceImpl(request -> {
            started = request;
            starts++;
            if (executionFailure != null) {
                throw executionFailure;
            }
            WorkflowModel result = new WorkflowModel();
            result.setStatus(WorkflowModel.Status.COMPLETED);
            result.setOutput(Map.of("ok", true));
            return result;
        });
        return MockMvcBuilders.standaloneSetup(new OpenApiController(service,
                new OpenApiRequestReader(new ObjectMapper(), limit, raw)))
                .addFilters(filters).build();
    }

    static Stream<Arguments> jsonValues() {
        return Stream.of(
                Arguments.of("{\"barCode\":\"00123\",\"request\":{\"name\":\"张三\"}}",
                        Map.of("barCode", "00123", "request", Map.of("name", "张三"))),
                Arguments.of("[{\"barCode\":\"00123\"},{\"barCode\":\"00456\"}]",
                        List.of(Map.of("barCode", "00123"), Map.of("barCode", "00456"))),
                Arguments.of("[]", List.of()),
                Arguments.of("42", 42),
                Arguments.of("1234567890123456789", 1234567890123456789L),
                Arguments.of("12345.67890123456789", new BigDecimal("12345.67890123456789")),
                Arguments.of("true", true),
                Arguments.of("\"text\"", "text"),
                Arguments.of("null", null));
    }

    @ParameterizedTest
    @MethodSource("jsonValues")
    void exposesNativeJsonValuesWithoutBusinessUnwrapping(String json, Object expected) throws Exception {
        mvc.perform(post(PATH).contentType("application/json").content(json.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk());
        assertEquals(expected, started.getInput().get("body"));
        assertEquals("hl-open-api/query-lis-apply", started.getRoute());
        assertEquals("POST", started.getInput().get("method"));
        assertEquals(PATH, started.getInput().get("path"));
        assertEquals(Map.of(), started.getInput().get("query"));
        assertEquals(Map.of(), started.getInput().get("files"));
        assertFalse(started.getInput().containsKey("request"));
        assertFalse(started.getInput().containsKey("rawBody"));
    }

    @Test
    void arraysAreUsableByWorkflowExpressions() throws Exception {
        mvc.perform(post(PATH).contentType("application/json").content("[{\"barCode\":\"00123\"}]"))
                .andExpect(status().isOk());
        WorkflowModel workflow = new WorkflowModel();
        workflow.setInput(started.getInput());
        Map<String, Object> input = new ParametersUtils(new ObjectMapper()).getTaskInput(Map.of(
                "items", "${workflow.input.body}", "first", "${workflow.input.body[0].barCode}"), workflow, "test");
        assertEquals(List.of(Map.of("barCode", "00123")), input.get("items"));
        assertEquals("00123", input.get("first"));
    }

    @Test
    void preservesQueryAndHeadersSeparatelyFromBody() throws Exception {
        mvc.perform(post(PATH).contentType("application/vnd.iflow+json;charset=UTF-8")
                        .queryParam("barCode", "query").queryParam("tag", "a", "b")
                        .header("X-Trace", "first", "second").content("{\"barCode\":\"body\"}"))
                .andExpect(status().isOk());
        assertEquals(Map.of("barCode", "body"), started.getInput().get("body"));
        assertEquals(Map.of("barCode", "query", "tag", List.of("a", "b")), started.getInput().get("query"));
        assertEquals(List.of("first", "second"), ((Map<?, ?>) started.getInput().get("headers")).get("x-trace"));
    }

    @Test
    void getHasNullBodyAndStructuredQuery() throws Exception {
        mvc.perform(get(PATH).queryParam("barCode", "00123").queryParam("tag", "a", "b"))
                .andExpect(status().isOk());
        assertNull(started.getInput().get("body"));
        assertEquals("GET", started.getInput().get("method"));
        assertEquals(Map.of("barCode", "00123", "tag", List.of("a", "b")), started.getInput().get("query"));
    }

    @Test
    void plainTextIsNotGuessedAndRawBodyIsOptional() throws Exception {
        mvc = mvc(1024, true);
        String text = "  {\"barCode\":\"00123\"}\n";
        mvc.perform(post(PATH).contentType("text/plain").content(text)).andExpect(status().isOk());
        assertEquals(text, started.getInput().get("body"));
        assertEquals(text, started.getInput().get("rawBody"));
        mvc.perform(post(PATH).contentType("application/json").content(text)).andExpect(status().isOk());
        assertEquals(Map.of("barCode", "00123"), started.getInput().get("body"));
        assertEquals(text, started.getInput().get("rawBody"));
    }

    @Test
    void emptyBodyIsNullRegardlessOfContentType() throws Exception {
        for (String contentType : List.of("application/json", "application/xml", "text/plain", "image/png")) {
            mvc.perform(post(PATH).contentType(contentType)).andExpect(status().isOk());
            assertNull(started.getInput().get("body"));
        }
        mvc.perform(post(PATH)).andExpect(status().isOk());
        assertTrue(started.getInput().containsKey("body"));
        assertNull(started.getInput().get("body"));
    }

    static Stream<Arguments> xmlEncodings() {
        return Stream.of(
                Arguments.of("application/xml", "UTF-8"),
                Arguments.of("text/xml", "GB18030"),
                Arguments.of("application/soap+xml", "UTF-16"));
    }

    @ParameterizedTest
    @MethodSource("xmlEncodings")
    void xmlRetainsShapeAndDetectsDeclaredEncoding(String type, String encoding) throws Exception {
        mvc = mvc(4096, true);
        String xml = "<?xml version=\"1.0\" encoding=\"" + encoding + "\"?>"
                + "<r:request xmlns:r=\"urn:test\" id=\"01\">说明<item>00123</item><item>张三</item><empty/></r:request>";
        mvc.perform(post(PATH).contentType(type).content(xml.getBytes(Charset.forName(encoding))))
                .andExpect(status().isOk());
        assertEquals(Map.of("r:request", Map.of("@xmlns:r", "urn:test", "@id", "01", "#text", "说明",
                "item", List.of("00123", "张三"), "empty", "")), started.getInput().get("body"));
        // UTF-16 may decode its byte-order mark as a leading character.
        assertEquals(xml, ((String) started.getInput().get("rawBody")).replaceFirst("^\uFEFF", ""));
    }

    @Test
    void explicitCharsetIsHonored() throws Exception {
        mvc = mvc(1024, true);
        for (String type : List.of("application/json", "application/xml", "text/plain")) {
            String text = type.endsWith("json") ? "{\"name\":\"张三\"}"
                    : type.endsWith("xml") ? "<?xml version=\"1.0\" encoding=\"UTF-8\"?><name>张三</name>" : "张三";
            mvc.perform(post(PATH).contentType(type + ";charset=GB18030").content(text.getBytes(Charset.forName("GB18030"))))
                    .andExpect(status().isOk());
            assertEquals(type.endsWith("plain") ? "张三" : Map.of("name", "张三"), started.getInput().get("body"));
            assertEquals(text, started.getInput().get("rawBody"));
        }
    }

    static Stream<Arguments> invalidBodies() {
        return Stream.of(
                Arguments.of("application/json", " "),
                Arguments.of("application/json", "{"),
                Arguments.of("application/json", "{} {}"),
                Arguments.of("application/xml", " "),
                Arguments.of("application/xml", "<r>"),
                Arguments.of("application/xml", "<!DOCTYPE r [<!ENTITY x SYSTEM 'file:///does-not-exist'>]><r>&x;</r>"));
    }

    @ParameterizedTest
    @MethodSource("invalidBodies")
    void invalidDocumentsDoNotStartWorkflow(String type, String body) throws Exception {
        mvc.perform(post(PATH).contentType(type).content(body)).andExpect(status().isBadRequest());
        assertEquals(0, starts);
    }

    @Test
    void rejectsInvalidUtf8BeforeStarting() throws Exception {
        mvc.perform(post(PATH).contentType("application/json").content(new byte[] {'"', (byte) 0xc3, '"'}))
                .andExpect(status().isBadRequest());
        assertEquals(0, starts);
    }

    @Test
    void rejectsMissingAndUnsupportedContentTypesBeforeStarting() throws Exception {
        mvc.perform(post(PATH).content("{}")).andExpect(status().isUnsupportedMediaType());
        for (String type : List.of("image/png", "application/json;charset=UNKNOWN", "invalid")) {
            mvc.perform(post(PATH).header("Content-Type", type).content("{}"))
                    .andExpect(status().isUnsupportedMediaType());
        }
        assertEquals(0, starts);
    }

    @Test
    void bodyModeRawSkipsJsonAndXmlDocumentParsing() throws Exception {
        mvc = mvc(4096, true);
        String invalidJson = " {not-json} ";
        mvc.perform(post(PATH).contentType("application/json")
                        .header(OpenApiRequestReader.BODY_MODE_HEADER, " RAW ")
                        .content(invalidJson))
                .andExpect(status().isOk());
        assertEquals(invalidJson, started.getInput().get("body"));
        assertEquals(invalidJson, started.getInput().get("rawBody"));

        String xmlWithDtd = "<!DOCTYPE r [<!ENTITY x 'value'>]><r>&x;</r>";
        mvc.perform(post(PATH).contentType("application/xml")
                        .header(OpenApiRequestReader.BODY_MODE_HEADER, "raw")
                        .content(xmlWithDtd))
                .andExpect(status().isOk());
        assertEquals(xmlWithDtd, started.getInput().get("body"));
    }

    @Test
    void rejectsEmptyUnknownAndDuplicateBodyModes() throws Exception {
        for (String mode : List.of("", "guess")) {
            mvc.perform(post(PATH).contentType("application/json")
                            .header(OpenApiRequestReader.BODY_MODE_HEADER, mode)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post(PATH).contentType("application/json")
                        .header(OpenApiRequestReader.BODY_MODE_HEADER, "auto", "raw")
                        .content("{}"))
                .andExpect(status().isBadRequest());
        assertEquals(0, starts);
    }

    @Test
    void parsesUrlEncodedFieldsWithoutMixingUrlQuery() throws Exception {
        String itemResults = "[{\"examElementId\":\"3837\"}]";
        String encoded = "examNo=00123&tag=a&tag=b&empty=&itemResults="
                + URLEncoder.encode(itemResults, StandardCharsets.UTF_8)
                + "&name=" + URLEncoder.encode("张三", StandardCharsets.UTF_8);
        mvc.perform(post(PATH + "?examNo=query&tag=url")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content(encoded))
                .andExpect(status().isOk());

        assertEquals(Map.of(
                "examNo", "00123",
                "tag", List.of("a", "b"),
                "empty", "",
                "itemResults", itemResults,
                "name", "张三"), started.getInput().get("body"));
        assertEquals(Map.of("examNo", "query", "tag", "url"), started.getInput().get("query"));
    }

    @Test
    void urlEncodedRawModeKeepsOriginalText() throws Exception {
        mvc = mvc(4096, true);
        String encoded = "name=%E5%BC%A0%E4%B8%89&tag=a&tag=b";
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .header(OpenApiRequestReader.BODY_MODE_HEADER, "raw")
                        .content(encoded))
                .andExpect(status().isOk());
        assertEquals(encoded, started.getInput().get("body"));
        assertEquals(encoded, started.getInput().get("rawBody"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void multipartSplitsFieldsAndPassesUploadedBytesAsBase64() throws Exception {
        mvc = mvc(8192, true);
        MockPart examNo = textPart("examNo", "00123");
        String itemJson = "[{\"examElementId\":\"3837\"}]";
        MockPart itemResults = textPart("itemResults", itemJson);
        MockPart repeated = textPart("examNo", "00456");
        byte[] pdf = new byte[] {'%', 'P', 'D', 'F', '-', 0, (byte) 0xff};
        byte[] base64Text = "AQIDBA==".getBytes(StandardCharsets.US_ASCII);
        byte[] dataUri = "data:text/plain;base64,SGVsbG8=".getBytes(StandardCharsets.US_ASCII);
        MockPart firstFile = filePart("file", "report.pdf", "application/pdf", pdf);
        MockPart secondFile = filePart("file", "content.txt", "text/plain", base64Text);
        MockPart thirdFile = filePart("file", "data-uri.txt", "text/plain", dataUri);
        MockPart zeroFile = filePart("file", "empty.bin", "application/octet-stream", new byte[0]);
        MockPart placeholder = filePart("file", "", "application/octet-stream", new byte[0]);

        mvc.perform(multipart(PATH + "?examNo=query")
                        .part(examNo, itemResults, repeated, firstFile, secondFile, thirdFile, zeroFile, placeholder)
                        .contentType("multipart/form-data; boundary=mock-boundary")
                        .header(OpenApiRequestReader.BODY_MODE_HEADER, "raw"))
                .andExpect(status().isOk());

        assertEquals(Map.of("examNo", List.of("00123", "00456"), "itemResults", itemJson),
                started.getInput().get("body"));
        assertEquals(Map.of("examNo", "query"), started.getInput().get("query"));
        assertFalse(started.getInput().containsKey("rawBody"));
        List<Map<String, Object>> descriptors = (List<Map<String, Object>>)
                ((Map<String, Object>) started.getInput().get("files")).get("file");
        assertEquals(4, descriptors.size());
        assertDescriptor(descriptors.get(0), "report.pdf", "application/pdf", pdf);
        assertDescriptor(descriptors.get(1), "content.txt", "text/plain", base64Text);
        assertDescriptor(descriptors.get(2), "data-uri.txt", "text/plain", dataUri);
        assertDescriptor(descriptors.get(3), "empty.bin", "application/octet-stream", new byte[0]);
    }

    @Test
    void multipartPayloadBytesAreCheckedBeforeWorkflowStart() throws Exception {
        mvc = mvc(3, false);
        mvc.perform(multipart(PATH)
                        .part(filePart("file", "four.bin", "application/octet-stream",
                                new byte[] {1, 2, 3, 4}))
                        .contentType("multipart/form-data; boundary=mock-boundary"))
                .andExpect(status().isPayloadTooLarge());
        assertEquals(0, starts);
    }

    @Test
    @SuppressWarnings("unchecked")
    void uploadedFileDataCanBeSerializedAfterWorkflowStartFails() throws Exception {
        executionFailure = new IllegalStateException("simulated execution failure");
        mvc.perform(multipart(PATH)
                        .part(filePart("file", "payload.bin", "application/octet-stream",
                                new byte[] {1, 2, 3}))
                        .contentType("multipart/form-data; boundary=mock-boundary"))
                .andExpect(status().isOk());

        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> restored = mapper.readValue(mapper.writeValueAsString(started.getInput()), Map.class);
        List<Map<String, Object>> descriptors = (List<Map<String, Object>>)
                ((Map<String, Object>) restored.get("files")).get("file");
        assertDescriptor(descriptors.get(0), "payload.bin", "application/octet-stream", new byte[] {1, 2, 3});
    }

    private static MockPart textPart(String name, String value) {
        MockPart part = new MockPart(name, value.getBytes(StandardCharsets.UTF_8));
        part.getHeaders().setContentType(new MediaType("text", "plain", StandardCharsets.UTF_8));
        return part;
    }

    private static MockPart filePart(String name, String fileName, String contentType, byte[] bytes) {
        MockPart part = new MockPart(name, fileName, bytes);
        part.getHeaders().setContentType(MediaType.parseMediaType(contentType));
        return part;
    }

    private void assertDescriptor(
            Map<String, Object> descriptor,
            String fileName,
            String contentType,
            byte[] expectedBytes) {
        assertEquals(fileName, descriptor.get("fileName"));
        assertEquals(contentType, descriptor.get("contentType"));
        assertEquals(expectedBytes.length, ((Number) descriptor.get("size")).longValue());
        assertEquals(4, descriptor.size());
        assertEquals(Base64.getEncoder().encodeToString(expectedBytes), descriptor.get("contentBase64"));
        assertArrayEquals(expectedBytes, Base64.getDecoder().decode((String) descriptor.get("contentBase64")));
    }

    @Test
    void countsActualBytesEvenWithoutAnAccurateContentLength() throws Exception {
        for (long reportedLength : new long[] {-1, 1}) {
            mvc = mvc(4, false, (request, response, chain) -> chain.doFilter(
                    new HttpServletRequestWrapper((HttpServletRequest) request) {
                        @Override public long getContentLengthLong() { return reportedLength; }
                        @Override public int getContentLength() { return (int) reportedLength; }
                    }, response));
            mvc.perform(post(PATH).contentType("text/plain").content("12345"))
                    .andExpect(status().isPayloadTooLarge());
        }
        assertEquals(0, starts);
        mvc.perform(post(PATH).contentType("text/plain").content("1234")).andExpect(status().isOk());
        assertEquals("1234", started.getInput().get("body"));
    }

    @Test
    void appliesDefaultTenMiBLimitAndCountsBytesInsteadOfCharacters() throws Exception {
        mvc.perform(post(PATH).contentType("text/plain")
                        .content(new byte[OpenApiRequestReader.DEFAULT_MAX_BODY_BYTES + 1]))
                .andExpect(status().isPayloadTooLarge());
        mvc = mvc(4, false);
        mvc.perform(post(PATH).contentType("text/plain").content("张三".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isPayloadTooLarge());
        assertEquals(0, starts);
    }

    @Test
    void bindsOpenApiConfiguration() {
        new ApplicationContextRunner().withUserConfiguration(ReaderConfiguration.class)
                .withPropertyValues("iflow.open-api.max-body-bytes=4", "iflow.open-api.include-raw-body=true")
                .run(context -> {
                    OpenApiRequestReader reader = context.getBean(OpenApiRequestReader.class);
                    MockHttpServletRequest request = new MockHttpServletRequest("POST", PATH);
                    request.setContentType("text/plain");
                    request.setContent("1234".getBytes(StandardCharsets.UTF_8));
                    assertEquals("1234", reader.read(request).rawBody());
                    request.setContent("12345".getBytes(StandardCharsets.UTF_8));
                    assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> reader.read(request));
                });
        assertThrows(IllegalArgumentException.class, () -> new OpenApiRequestReader(new ObjectMapper(), 0, false));
    }

    @Configuration(proxyBeanMethods = false)
    @Import(OpenApiRequestReader.class)
    static class ReaderConfiguration {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
    }
}
