package com.helianhealth.agent.gateway;

import com.helianhealth.agent.core.model.WorkflowModel;
import com.helianhealth.agent.gateway.jarvis.JarvisServiceImpl;
import com.helianhealth.agent.gateway.jarvis.model.JarvisRequest;
import com.helianhealth.agent.gateway.rest.OpenApiServiceImpl;
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
        assertEquals(expected, gateway.invoke("request", "/test"));
    }

    @Test
    void jarvisRejectsNonObjectOutputWithClearMessage() {
        JarvisServiceImpl gateway = new JarvisServiceImpl(request -> completed(List.of(1, 2)));
        var response = gateway.invoke(new JarvisRequest());
        assertEquals("-1", response.getCode());
        assertTrue(response.getMessage().contains("requires an object workflow output"));
    }

    @Test
    void jarvisKeepsObjectResponseContract() {
        JarvisServiceImpl gateway = new JarvisServiceImpl(request -> completed(
                Map.of("code", "200", "rsp", List.of(1, 2))));
        var response = gateway.invoke(new JarvisRequest());
        assertEquals("200", response.getCode());
        assertEquals(List.of(1, 2), response.getRsp());
    }

    private WorkflowModel completed(Object output) {
        WorkflowModel workflow = new WorkflowModel();
        workflow.setStatus(WorkflowModel.Status.COMPLETED);
        workflow.setOutput(output);
        return workflow;
    }
}
