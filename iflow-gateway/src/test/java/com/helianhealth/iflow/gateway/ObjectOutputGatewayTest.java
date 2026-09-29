package com.helianhealth.iflow.gateway;

import com.helianhealth.iflow.common.exception.WorkFlowConfigException;
import com.helianhealth.iflow.common.exception.WorkFlowErrorCode;
import com.helianhealth.iflow.core.model.WorkflowModel;
import com.helianhealth.iflow.gateway.jarvis.JarvisServiceImpl;
import com.helianhealth.iflow.gateway.jarvis.model.JarvisRequest;
import com.helianhealth.iflow.gateway.jarvis.model.JarvisResultData;
import com.helianhealth.iflow.gateway.rest.OpenApiServiceImpl;
import com.helianhealth.iflow.gateway.rest.OpenApiRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ObjectOutputGatewayTest {

    static Stream<Arguments> responses() {
        return Stream.of(
                Arguments.of(List.of(1, 2), "[1,2]"),
                Arguments.of(Map.of("code", "OK"), "{\"code\":\"OK\"}"),
                Arguments.of("<response>ok</response>", "<response>ok</response>"),
                Arguments.of(42, "42"),
                Arguments.of(false, "false"),
                Arguments.of(null, "null"),
                Arguments.of(Map.of("response", "legacy response"), "legacy response"));
    }

    @ParameterizedTest
    @MethodSource("responses")
    void openApiReturnsRawWorkflowValues(Object output, String expected) {
        OpenApiServiceImpl gateway = new OpenApiServiceImpl(request -> completed(output));
        assertEquals(expected, gateway.invoke(new OpenApiRequest(
                "request", Map.of(), Map.of(), "POST", "/test", null)));
    }

    static Stream<Object> jarvisResponses() {
        return Stream.of(
                List.of(1, 2),
                Map.of("code", "OK"),
                "<response>ok</response>",
                42,
                false);
    }

    @ParameterizedTest
    @MethodSource("jarvisResponses")
    void jarvisReturnsRawWorkflowValues(Object output) {
        JarvisServiceImpl gateway = new JarvisServiceImpl(request -> completed(output));
        assertEquals(output, gateway.invoke(new JarvisRequest()));
    }

    @Test
    void jarvisReturnsNullWorkflowOutput() {
        JarvisServiceImpl gateway = new JarvisServiceImpl(request -> completed(null));
        assertNull(gateway.invoke(new JarvisRequest()));
    }

    @Test
    void jarvisWrapsFailedWorkflowAsError() {
        WorkflowModel workflow = new WorkflowModel();
        workflow.setStatus(WorkflowModel.Status.FAILED);
        workflow.setReasonForInCompletion("task failed");
        JarvisServiceImpl gateway = new JarvisServiceImpl(request -> workflow);

        JarvisResultData<?> response = assertInstanceOf(
                JarvisResultData.class, gateway.invoke(new JarvisRequest()));
        assertEquals("-1", response.getCode());
        assertEquals("task failed", response.getMessage());
    }

    @Test
    void jarvisWrapsInvocationExceptionAsError() {
        JarvisServiceImpl gateway = new JarvisServiceImpl(request -> {
            throw new WorkFlowConfigException(WorkFlowErrorCode.WORKFLOW_NOT_FOUND);
        });
        JarvisRequest request = new JarvisRequest();
        request.setBusinessMethod("missingWorkflow");

        JarvisResultData<?> response = assertInstanceOf(
                JarvisResultData.class, gateway.invoke(request));
        assertEquals("-1", response.getCode());
        assertTrue(response.getMessage().contains("missingWorkflow"));
    }

    private WorkflowModel completed(Object output) {
        WorkflowModel workflow = new WorkflowModel();
        workflow.setStatus(WorkflowModel.Status.COMPLETED);
        workflow.setOutput(output);
        return workflow;
    }
}
