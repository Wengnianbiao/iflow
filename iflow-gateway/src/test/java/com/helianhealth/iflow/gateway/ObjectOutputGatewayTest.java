package com.helianhealth.iflow.gateway;

import com.helianhealth.iflow.common.exception.WorkFlowConfigException;
import com.helianhealth.iflow.common.exception.WorkFlowErrorCode;
import com.helianhealth.iflow.common.domain.run.StartWorkflowRequest;
import com.helianhealth.iflow.common.utils.JsonUtils;
import com.helianhealth.iflow.core.model.WorkflowModel;
import com.helianhealth.iflow.core.service.WorkflowExecutionService;
import com.helianhealth.iflow.gateway.jarvis.JarvisServiceImpl;
import com.helianhealth.iflow.gateway.jarvis.model.JarvisResultData;
import com.helianhealth.iflow.gateway.rest.OpenApiServiceImpl;
import com.helianhealth.iflow.gateway.rest.OpenApiRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
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
        OpenApiServiceImpl gateway = new OpenApiServiceImpl(syncService(request -> completed(output)));
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
        JarvisServiceImpl gateway = new JarvisServiceImpl(syncService(request -> completed(output)));
        assertEquals(output, gateway.invoke(jarvisRequest("test")));
    }

    @Test
    void jarvisReturnsNullWorkflowOutput() {
        JarvisServiceImpl gateway = new JarvisServiceImpl(syncService(request -> completed(null)));
        assertNull(gateway.invoke(jarvisRequest("test")));
    }

    @Test
    void jarvisWrapsFailedWorkflowAsError() {
        WorkflowModel workflow = new WorkflowModel();
        workflow.setStatus(WorkflowModel.Status.FAILED);
        workflow.setReasonForInCompletion("task failed");
        JarvisServiceImpl gateway = new JarvisServiceImpl(syncService(request -> workflow));

        JarvisResultData<?> response = assertInstanceOf(
                JarvisResultData.class, gateway.invoke(jarvisRequest("test")));
        assertEquals("-1", response.getCode());
        assertEquals("task failed", response.getMessage());
    }

    @Test
    void jarvisWrapsInvocationExceptionAsError() {
        JarvisServiceImpl gateway = new JarvisServiceImpl(syncService(request -> {
            throw new WorkFlowConfigException(WorkFlowErrorCode.WORKFLOW_NOT_FOUND);
        }));
        JarvisResultData<?> response = assertInstanceOf(
                JarvisResultData.class, gateway.invoke(jarvisRequest("missingWorkflow")));
        assertEquals("-1", response.getCode());
        assertTrue(response.getMessage().contains("missingWorkflow"));
    }

    static Stream<Map<String, Object>> invalidJarvisBodies() {
        return Stream.of(Map.of(), Map.of("BusinessMethod", ""),
                Map.of("BusinessMethod", " "), Map.of("BusinessMethod", 123));
    }

    @ParameterizedTest
    @MethodSource("invalidJarvisBodies")
    void jarvisRejectsMissingOrInvalidBusinessMethodWithoutStarting(Map<String, Object> body) {
        JarvisServiceImpl gateway = new JarvisServiceImpl(syncService(request -> {
            throw new AssertionError("非法 Jarvis 路由不应启动工作流");
        }));
        JarvisResultData<?> response = assertInstanceOf(JarvisResultData.class,
                gateway.invoke(Map.of("body", body, "rawBody", JsonUtils.toJsonString(body))));
        assertEquals("-1", response.getCode());
        assertEquals("BusinessMethod 必须为非空字符串", response.getMessage());
    }

    private Map<String, Object> jarvisRequest(String route) {
        Map<String, Object> body = Map.of("BusinessMethod", route);
        return Map.of("body", body, "rawBody", JsonUtils.toJsonString(body));
    }

    private WorkflowExecutionService syncService(Function<StartWorkflowRequest, WorkflowModel> execute) {
        return new WorkflowExecutionService() {
            @Override
            public String startWorkflow(StartWorkflowRequest request) {
                throw new AssertionError("协议入口必须等待工作流最终输出");
            }

            @Override
            public WorkflowModel startWorkflowSync(StartWorkflowRequest request) {
                return execute.apply(request);
            }
        };
    }

    private WorkflowModel completed(Object output) {
        WorkflowModel workflow = new WorkflowModel();
        workflow.setStatus(WorkflowModel.Status.COMPLETED);
        workflow.setOutput(output);
        return workflow;
    }
}
