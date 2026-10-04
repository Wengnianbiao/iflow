package com.helianhealth.iflow.tasks.webservice;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowTask;
import com.helianhealth.iflow.core.execution.mapper.TaskMapperContext;
import com.helianhealth.iflow.core.execution.mapper.WebServiceTaskMapper;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import com.helianhealth.iflow.core.utils.ParametersUtils;
import com.helianhealth.iflow.tasks.webservice.providers.SoapClientProvider;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 从 JSON 入参、编排层表达式到实际 SOAP 请求，验证数据类型及容器路径的完整链路。
 */
class WebServiceDataTypesTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final String URI = "http://example.com/soap";

    @ParameterizedTest(name = "{0}")
    @MethodSource("scalarValues")
    void preservesJsonScalarTypesAndBindsTextAttributesAndCdata(
            String description, String jsonValue, Object expectedValue, String text, String escapedText)
            throws Exception {
        WorkflowModel workflow = workflowWithInput("{\"value\":" + jsonValue + "}");
        String template = "<r value=\"$.value\"><text>$.value</text>"
                + "<raw><![CDATA[$.value]]></raw></r>";
        TaskModel task = mapTask(workflow, template, Map.of("value", "${workflow.input.value}"));

        Map<?, ?> parameters = (Map<?, ?>) task.getInputData().get("templateParameters");
        assertTrue(parameters.containsKey("value"), "显式 null 也应保留参数名");
        assertEquals(expectedValue, parameters.get("value"));
        if (expectedValue != null) {
            assertEquals(expectedValue.getClass(), parameters.get("value").getClass());
        }
        assertSentXml(workflow, task, "<r value=\"" + escapedText + "\"><text>" + escapedText
                + "</text><raw><![CDATA[" + text + "]]></raw></r>");
        assertEquals(template, task.getInputData().get("soapTemplate"));
        assertEquals(expectedValue, parameters.get("value"));
    }

    private static Stream<Arguments> scalarValues() {
        return Stream.of(
                Arguments.of("字符串：中文及 emoji", "\"张三😀\"", "张三😀", "张三😀", "张三😀"),
                Arguments.of("字符串：数字文本保留前导零", "\"00123\"", "00123", "00123", "00123"),
                Arguments.of("字符串：空串", "\"\"", "", "", ""),
                Arguments.of("字符串：XML 特殊字符", "\"<&>\\\"'\"", "<&>\"'", "<&>\"'",
                        "&lt;&amp;&gt;&quot;&apos;"),
                Arguments.of("整数：零", "0", 0, "0", "0"),
                Arguments.of("整数：负数", "-42", -42, "-42", "-42"),
                Arguments.of("整数：32 位上界", "2147483647", Integer.MAX_VALUE, "2147483647", "2147483647"),
                Arguments.of("整数：64 位上界", "9223372036854775807", Long.MAX_VALUE,
                        "9223372036854775807", "9223372036854775807"),
                Arguments.of("整数：超过 64 位", "9223372036854775808", new BigInteger("9223372036854775808"),
                        "9223372036854775808", "9223372036854775808"),
                Arguments.of("小数", "36.5", 36.5, "36.5", "36.5"),
                Arguments.of("小数：科学计数法", "1e-7", 1e-7, "1.0E-7", "1.0E-7"),
                Arguments.of("布尔：true", "true", true, "true", "true"),
                Arguments.of("布尔：false", "false", false, "false", "false"),
                Arguments.of("null", "null", null, "", ""),
                Arguments.of("日期时间以字符串传递", "\"2026-10-04T12:30:00+08:00\"",
                        "2026-10-04T12:30:00+08:00", "2026-10-04T12:30:00+08:00", "2026-10-04T12:30:00+08:00"),
                Arguments.of("二进制以 Base64 字符串传递", "\"AAEC/w==\"", "AAEC/w==", "AAEC/w==", "AAEC/w=="));
    }

    @Test
    void bindsNestedObjectsScalarArraysObjectArraysMixedArraysAndMultidimensionalArrays() throws Exception {
        WorkflowModel workflow = workflowWithInput("""
                {
                  "patient": {"name": "张三<&", "address": {"city": "北京"}, "display name": "张先生"},
                  "codes": ["001", "002"],
                  "orders": [
                    {"id": 101, "lines": [{"quantity": 2, "price": 12.5}]},
                    {"id": 102, "lines": [{"quantity": 0, "price": 0.0}]}
                  ],
                  "mixed": ["文字", 42, 3.5, false, null, {"value": "对象"}, ["嵌套数组"]],
                  "matrix": [[1, 2], [3, 4]],
                  "emptyObject": {},
                  "emptyArray": []
                }
                """);
        Map<String, Object> bindings = new LinkedHashMap<>();
        workflow.getInput().keySet().forEach(key -> bindings.put(key, "${workflow.input." + key + "}"));
        String template = """
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/" xmlns:m="urn:iflow:test">
                  <soap:Body>
                    <m:submit name="$.patient['display name']">
                      <m:patient><m:name>$.patient.name</m:name><m:city>$.patient.address.city</m:city></m:patient>
                      <m:codes><m:code>$.codes[0]</m:code><m:code>$.codes[1]</m:code></m:codes>
                      <m:order id="$.orders[0].id" quantity="$.orders[0].lines[0].quantity">$.orders[0].lines[0].price</m:order>
                      <m:order id="$.orders[1].id" quantity="$.orders[1].lines[0].quantity">$.orders[1].lines[0].price</m:order>
                      <m:mixed>$.mixed[0] / $.mixed[1] / $.mixed[2] / $.mixed[3] / $.mixed[4] / $.mixed[5].value / $.mixed[6][0]</m:mixed>
                      <m:matrix>$.matrix[0][1] / $.matrix[1][0]</m:matrix>
                    </m:submit>
                  </soap:Body>
                </soap:Envelope>
                """;
        String expectedXml = """
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/" xmlns:m="urn:iflow:test">
                  <soap:Body>
                    <m:submit name="张先生">
                      <m:patient><m:name>张三&lt;&amp;</m:name><m:city>北京</m:city></m:patient>
                      <m:codes><m:code>001</m:code><m:code>002</m:code></m:codes>
                      <m:order id="101" quantity="2">12.5</m:order>
                      <m:order id="102" quantity="0">0.0</m:order>
                      <m:mixed>文字 / 42 / 3.5 / false /  / 对象 / 嵌套数组</m:mixed>
                      <m:matrix>2 / 3</m:matrix>
                    </m:submit>
                  </soap:Body>
                </soap:Envelope>
                """;
        TaskModel task = mapTask(workflow, template, bindings);

        assertEquals(workflow.getInput(), task.getInputData().get("templateParameters"));
        assertSentXml(workflow, task, expectedXml);
        assertEquals(template, task.getInputData().get("soapTemplate"));
        assertEquals(workflow.getInput(), task.getInputData().get("templateParameters"));
    }

    @Test
    void bindsObjectAndArrayOutputsFromPreviousTasksWithoutReinterpretingBusinessText() throws Exception {
        WorkflowModel workflow = new WorkflowModel();
        TaskModel prepare = new TaskModel();
        prepare.setReferenceTaskName("prepare");
        prepare.setStatus(TaskModel.Status.COMPLETED);
        prepare.setOutputData(Map.of("name", "${literal.text} $.missing", "active", false));
        TaskModel items = new TaskModel();
        items.setReferenceTaskName("items");
        items.setStatus(TaskModel.Status.COMPLETED);
        items.setOutputData(List.of(Map.of("id", 42), Map.of("id", 43)));
        workflow.getTasks().addAll(List.of(prepare, items));
        TaskModel task = mapTask(workflow,
                "<r active=\"$.patient.active\">$.patient.name / $.orders[1].id</r>",
                Map.of("patient", "${prepare.output}", "orders", "${items.output}"));

        assertSentXml(workflow, task, "<r active=\"false\">${literal.text} $.missing / 43</r>");
        assertEquals(Map.of("patient", prepare.getOutputData(), "orders", items.getOutputData()),
                task.getInputData().get("templateParameters"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidContainerPaths")
    void rejectsContainerValuesAndInvalidTraversalBeforeNetworkCall(
            String path, String jsonParameters, String diagnostic) throws Exception {
        WorkflowModel workflow = workflowWithInput(jsonParameters);
        TaskModel task = mapTask(workflow, "<r>" + path + "</r>", Map.of("payload", "${workflow.input}"));
        SoapClientProvider provider = mock(SoapClientProvider.class);

        new WebServiceTask(provider, OBJECT_MAPPER).execute(workflow, task, null);

        assertEquals(TaskModel.Status.FAILED, task.getStatus());
        assertTrue(task.getReasonForInCompletion().contains(path), task.getReasonForInCompletion());
        assertTrue(task.getReasonForInCompletion().contains(diagnostic), task.getReasonForInCompletion());
        assertFalse(task.outputAsMap().containsKey("error"));
        verifyNoInteractions(provider);
    }

    private static Stream<Arguments> invalidContainerPaths() {
        return Stream.of(
                Arguments.of("$.payload.value", "{\"value\":{}}", "concrete scalar field"),
                Arguments.of("$.payload.value", "{\"value\":[]}", "concrete scalar field"),
                Arguments.of("$.payload.value[0]", "{\"value\":[{\"id\":1}]}", "concrete scalar field"),
                Arguments.of("$.payload.value[0]", "{\"value\":[[1]]}", "concrete scalar field"),
                Arguments.of("$.payload.value[0]", "{\"value\":[]}", "array index out of bounds"),
                Arguments.of("$.payload.value[1][0]", "{\"value\":[[1]]}", "array index out of bounds"),
                Arguments.of("$.payload.value[0][1]", "{\"value\":[[1]]}", "array index out of bounds"),
                Arguments.of("$.payload.value[0].missing", "{\"value\":[{\"id\":1}]}", "missing field"),
                Arguments.of("$.payload.value[0].id", "{\"value\":[null]}", "non-object"),
                Arguments.of("$.payload.value[0].id", "{\"value\":[42]}", "non-object"),
                Arguments.of("$.payload.value[0][0]", "{\"value\":[{\"id\":1}]}", "non-array"),
                Arguments.of("$.payload.value[0]", "{\"value\":\"abc\"}", "non-array"));
    }

    private WorkflowModel workflowWithInput(String json) throws Exception {
        WorkflowModel workflow = new WorkflowModel();
        workflow.setInput(OBJECT_MAPPER.readValue(json, MAP_TYPE));
        return workflow;
    }

    private TaskModel mapTask(WorkflowModel workflow, String template, Map<String, Object> bindings) {
        WorkflowTask definition = WorkflowTask.builder().name("soap").type("WEBSERVICE")
                .taskReferenceName("soap").inputParameters(Map.of(
                        "uri", URI, "soapTemplate", template, "templateParameters", bindings)).build();
        return new WebServiceTaskMapper(new ParametersUtils()).getMappedTasks(TaskMapperContext.builder()
                .workflowModel(workflow).workflowTask(definition).taskId("soap-1").build()).get(0);
    }

    @SuppressWarnings("unchecked")
    private void assertSentXml(WorkflowModel workflow, TaskModel task, String expectedXml) throws Exception {
        SoapClientProvider provider = mock(SoapClientProvider.class);
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        when(provider.createHttpClient(any())).thenReturn(client);
        AtomicReference<String> sentXml = new AtomicReference<>();
        when(client.execute(any(ClassicHttpRequest.class), any(HttpClientResponseHandler.class)))
                .thenAnswer(invocation -> {
                    ClassicHttpRequest request = invocation.getArgument(0);
                    assertEquals("POST", request.getMethod());
                    assertEquals(URI, request.getUri().toString());
                    sentXml.set(EntityUtils.toString(request.getEntity()));
                    BasicClassicHttpResponse response = new BasicClassicHttpResponse(200, "OK");
                    response.setEntity(new StringEntity("<result>成功</result>", ContentType.TEXT_XML));
                    HttpClientResponseHandler<WebServiceTask.WebServiceResponse> handler = invocation.getArgument(1);
                    return handler.handleResponse(response);
                });

        new WebServiceTask(provider, OBJECT_MAPPER).execute(workflow, task, null);

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus(), task.getReasonForInCompletion());
        assertEquals(expectedXml, sentXml.get());
        assertEquals(Map.of("body", "<result>成功</result>", "statusCode", 200,
                "reasonPhrase", "OK", "headers", Map.of()), task.getOutputData());
        verify(provider).createHttpClient(any());
        verify(client).execute(any(ClassicHttpRequest.class), any(HttpClientResponseHandler.class));
        verify(client).close();
    }
}
