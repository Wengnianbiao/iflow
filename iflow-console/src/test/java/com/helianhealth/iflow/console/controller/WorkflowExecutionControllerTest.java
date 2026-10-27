package com.helianhealth.iflow.console.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.iflow.common.base.PageList;
import com.helianhealth.iflow.common.domain.run.ExecutionSearch;
import com.helianhealth.iflow.common.domain.run.StartWorkflowRequest;
import com.helianhealth.iflow.core.dao.ExecutionDAO;
import com.helianhealth.iflow.core.dao.IndexDAO;
import com.helianhealth.iflow.core.service.WorkflowExecutionService;
import com.helianhealth.iflow.console.config.ConsoleExceptionHandler;
import com.helianhealth.iflow.gateway.rest.OpenApiRequestReader;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockPart;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

class WorkflowExecutionControllerTest {
    private final ExecutionDAO dao = mock(ExecutionDAO.class);
    private final IndexDAO indexDAO = mock(IndexDAO.class);
    private final WorkflowExecutionController controller = new WorkflowExecutionController(dao, null, null, indexDAO, null);

    @Test
    void consoleStartReturnsSubmittedWorkflowIdUsingAsynchronousEntry() {
        WorkflowExecutionService service = mock(WorkflowExecutionService.class);
        StartWorkflowRequest request = StartWorkflowRequest.builder().route("pacs/apply").build();
        when(service.startWorkflow(request)).thenReturn("submitted");
        var launchController = new WorkflowExecutionController(dao, service, null, indexDAO, null);

        assertEquals("submitted", launchController.start(request).getData());
        verify(service).startWorkflow(request);
        verifyNoMoreInteractions(service);
        verifyNoInteractions(dao, indexDAO);
    }

    @Test
    void consoleStartsSelectedVersionWithRawXmlUsingGatewayInputContract() throws Exception {
        WorkflowExecutionService service = mock(WorkflowExecutionService.class);
        when(service.startWorkflow(any())).thenReturn("submitted");
        String xml = """
                <root>
                  <patientCode id="56">患者编号</patientCode>
                  <applyId>申请单号</applyId>
                  <applyStatus>申请状态</applyStatus>
                </root>
                """;
        var response = launchMvc(service).perform(post("/api/workflow/execution/start-http")
                .queryParam("route", "pacs/apply").queryParam("version", "2")
                .contentType("application/xml;charset=UTF-8").content(xml.getBytes(StandardCharsets.UTF_8)))
                .andReturn().getResponse();
        assertEquals(200, response.getStatus());
        assertEquals("submitted", new ObjectMapper().readTree(response.getContentAsString()).get("data").asText());
        var captured = ArgumentCaptor.forClass(StartWorkflowRequest.class);
        verify(service).startWorkflow(captured.capture());
        StartWorkflowRequest request = captured.getValue();
        assertEquals("pacs/apply", request.getRoute());
        assertEquals(2, request.getVersion());
        assertEquals(Map.of("root", Map.of("patientCode", Map.of("@id", "56", "#text", "患者编号"),
                "applyId", "申请单号", "applyStatus", "申请状态")), request.getInput().get("body"));
        assertEquals(xml, request.getInput().get("rawBody"));
        assertEquals("POST", request.getInput().get("method"));
        assertEquals("/pacs/apply", request.getInput().get("path"));
        assertEquals(Map.of(), request.getInput().get("query"));
        assertEquals(Map.of(), request.getInput().get("files"));
        assertEquals("application/xml;charset=UTF-8", ((Map<?, ?>) request.getInput().get("headers")).get("content-type"));
        verifyNoMoreInteractions(service);
    }

    @Test
    void consoleRetainsRawJsonAndTextWithoutBrowserParsing() throws Exception {
        WorkflowExecutionService service = mock(WorkflowExecutionService.class);
        MockMvc mvc = launchMvc(service);
        for (String contentType : List.of("application/json", "text/plain")) {
            String rawBody = "  [1234567890123456789, \"张三\"]\n";
            var response = mvc.perform(post("/api/workflow/execution/start-http")
                    .queryParam("route", "pacs/apply").contentType(contentType + ";charset=UTF-8")
                    .content(rawBody.getBytes(StandardCharsets.UTF_8))).andReturn().getResponse();
            assertEquals(200, response.getStatus());
            var captured = ArgumentCaptor.forClass(StartWorkflowRequest.class);
            verify(service).startWorkflow(captured.capture());
            assertNull(captured.getValue().getVersion());
            assertEquals(rawBody, captured.getValue().getInput().get("rawBody"));
            assertEquals(contentType.equals("application/json") ? List.of(1234567890123456789L, "张三") : rawBody,
                    captured.getValue().getInput().get("body"));
            clearInvocations(service);
        }
    }

    @Test
    void consoleRejectsInvalidAndOversizedBodiesBeforeStarting() throws Exception {
        WorkflowExecutionService service = mock(WorkflowExecutionService.class);
        MockMvc mvc = launchMvc(service);
        for (String contentType : List.of("application/json", "application/xml")) {
            var response = mvc.perform(post("/api/workflow/execution/start-http")
                    .queryParam("route", "pacs/apply").contentType(contentType).content("{invalid"))
                    .andReturn().getResponse();
            assertEquals(400, response.getStatus());
            assertEquals("请求体格式或字符编码不合法",
                    new ObjectMapper().readTree(response.getContentAsString()).get("message").asText());
        }
        assertEquals(413, mvc.perform(post("/api/workflow/execution/start-http")
                .queryParam("route", "pacs/apply").contentType("text/plain").content("x".repeat(1025)))
                .andReturn().getResponse().getStatus());
        verifyNoInteractions(service);
    }

    @Test
    void consoleReadsUrlEncodedBodyBeforeServletParameterBinding() throws Exception {
        WorkflowExecutionService service = mock(WorkflowExecutionService.class);
        String body = "patientCode=001&tag=a&tag=b&name=%E5%BC%A0%E4%B8%89";
        var response = launchMvc(service).perform(post("/api/workflow/execution/start-http")
                .queryParam("route", "pacs/apply").queryParam("version", "2")
                .contentType("application/x-www-form-urlencoded;charset=UTF-8").content(body))
                .andReturn().getResponse();
        assertEquals(200, response.getStatus());
        var captured = ArgumentCaptor.forClass(StartWorkflowRequest.class);
        verify(service).startWorkflow(captured.capture());
        assertEquals("pacs/apply", captured.getValue().getRoute());
        assertEquals(2, captured.getValue().getVersion());
        assertEquals(Map.of("patientCode", "001", "tag", List.of("a", "b"), "name", "张三"),
                captured.getValue().getInput().get("body"));
        assertEquals(body, captured.getValue().getInput().get("rawBody"));
        assertEquals(Map.of(), captured.getValue().getInput().get("query"));
    }

    @Test
    void consoleAcceptsMultipartFieldsAndFilesAndEmptyRequests() throws Exception {
        WorkflowExecutionService service = mock(WorkflowExecutionService.class);
        MockMvc mvc = launchMvc(service);
        MockPart field = new MockPart("patientCode", "001".getBytes(StandardCharsets.UTF_8));
        MockPart file = new MockPart("file", "raw.bin", new byte[] {0, 1, (byte) 255});
        assertEquals(200, mvc.perform(multipart("/api/workflow/execution/start-http").part(field, file)
                .queryParam("route", "pacs/apply")
                .contentType("multipart/form-data;boundary=test")).andReturn().getResponse().getStatus());
        var captured = ArgumentCaptor.forClass(StartWorkflowRequest.class);
        verify(service).startWorkflow(captured.capture());
        assertEquals(Map.of("patientCode", "001"), captured.getValue().getInput().get("body"));
        var files = (Map<?, ?>) captured.getValue().getInput().get("files");
        assertEquals("AAH/", ((Map<?, ?>) ((List<?>) files.get("file")).getFirst()).get("contentBase64"));
        assertNull(captured.getValue().getInput().get("rawBody"));
        clearInvocations(service);
        assertEquals(200, mvc.perform(post("/api/workflow/execution/start-http")
                .queryParam("route", "pacs/apply")).andReturn().getResponse().getStatus());
        verify(service).startWorkflow(captured.capture());
        assertNull(captured.getValue().getInput().get("body"));
        assertEquals("", captured.getValue().getInput().get("rawBody"));
    }

    private MockMvc launchMvc(WorkflowExecutionService service) {
        return MockMvcBuilders.standaloneSetup(new WorkflowExecutionController(dao, service, null, indexDAO,
                        new OpenApiRequestReader(new ObjectMapper(), 1024)))
                .setControllerAdvice(new ConsoleExceptionHandler()).build();
    }

    @Test
    void searchAlwaysUsesIndexAndPassesCombinedFiltersAndPagination() {
        var indexed = new ExecutionSearch.WorkflowSummary("indexed", "first", "展示名称", 2,
                "COMPLETED", "order", 10, 30, 31, null, null);
        when(indexDAO.searchWorkflows(any())).thenReturn(new PageList<>(7, 2, 1, List.of(indexed)));
        var page = controller.search(null, List.of("first", "second", "first"), 2, "completed",
                null, "order", 0L, 100L, 2, 1).getData();
        assertEquals(7, page.getTotal());
        assertEquals("indexed", page.getRows().getFirst().getWorkflowId());
        assertEquals(20L, page.getRows().getFirst().getDurationMs());
        var captured = ArgumentCaptor.forClass(ExecutionSearch.WorkflowQuery.class);
        verify(indexDAO).searchWorkflows(captured.capture());
        assertEquals(List.of("first", "second"), captured.getValue().workflowRoutes());
        assertEquals(List.of("COMPLETED"), captured.getValue().statuses());
        assertEquals("order", captured.getValue().correlationId());
        assertEquals(2, captured.getValue().pageNum());
        assertEquals(1, captured.getValue().pageSize());
        verifyNoInteractions(dao);
    }

    @Test
    void emptyIndexReturnsEmptyResultsWithoutFallingBackToStoredWorkflows() {
        emptyWorkflowIndex();
        var page = controller.search("first", null, null, null,
                "unindexed", null, null, null, 1, 15).getData();
        assertEquals(0, page.getTotal());
        assertTrue(page.getRows().isEmpty());
        var captured = ArgumentCaptor.forClass(ExecutionSearch.WorkflowQuery.class);
        verify(indexDAO).searchWorkflows(captured.capture());
        assertEquals(List.of("first"), captured.getValue().workflowRoutes());
        assertEquals("unindexed", captured.getValue().workflowId());
        assertNull(captured.getValue().startTime());
        assertNull(captured.getValue().endTime());
        verifyNoInteractions(dao);
    }

    @Test
    void recentInstancesUseIndexImmediately() {
        emptyWorkflowIndex();
        assertTrue(controller.listInstances("first", 2, 50).getData().isEmpty());
        var captured = ArgumentCaptor.forClass(ExecutionSearch.WorkflowQuery.class);
        verify(indexDAO).searchWorkflows(captured.capture());
        assertEquals(List.of("first"), captured.getValue().workflowRoutes());
        assertEquals(2, captured.getValue().version());
        assertEquals(50, captured.getValue().pageSize());
        verifyNoInteractions(dao);
    }

    @Test
    void taskSearchQueriesIndexWithoutAnInitializationGate() {
        when(indexDAO.searchTasks(any())).thenReturn(new PageList<>(0, 1, 20, List.of()));
        assertEquals(0, controller.searchTasks("first", "wf", null, "call", "HTTP", "failed",
                true, 0L, 100L, 1, 20).getData().getTotal());
        var captured = ArgumentCaptor.forClass(ExecutionSearch.TaskQuery.class);
        verify(indexDAO).searchTasks(captured.capture());
        assertTrue(captured.getValue().asyncOnly());
        assertEquals("wf", captured.getValue().workflowId());
        assertEquals(List.of("FAILED"), captured.getValue().statuses());
        verifyNoInteractions(dao);
    }

    @Test
    void unboundedSearchDefaultsToSevenDaysAndClampsPagination() {
        emptyWorkflowIndex();
        long before = System.currentTimeMillis();
        controller.search(null, null, null, null, null, null, null, null, 0, 1000);
        var captured = ArgumentCaptor.forClass(ExecutionSearch.WorkflowQuery.class);
        verify(indexDAO).searchWorkflows(captured.capture());
        var query = captured.getValue();
        assertEquals(1, query.pageNum());
        assertEquals(100, query.pageSize());
        assertTrue(query.workflowRoutes().isEmpty());
        assertTrue(query.endTime() >= before && query.endTime() <= System.currentTimeMillis());
        assertEquals(Duration.ofDays(7).toMillis(), query.endTime() - query.startTime());
        verifyNoInteractions(dao);
    }

    @Test
    void correlationSearchDoesNotAddImplicitTimeBounds() {
        emptyWorkflowIndex();
        controller.search("first", null, null, null, null, "order", null, null, 1, 20);
        var captured = ArgumentCaptor.forClass(ExecutionSearch.WorkflowQuery.class);
        verify(indexDAO).searchWorkflows(captured.capture());
        assertEquals("order", captured.getValue().correlationId());
        assertNull(captured.getValue().startTime());
        assertNull(captured.getValue().endTime());
        verifyNoInteractions(dao);
    }

    private void emptyWorkflowIndex() {
        when(indexDAO.searchWorkflows(any())).thenReturn(new PageList<>(0, 1, 20, List.of()));
    }
}
