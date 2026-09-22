package com.helianhealth.iflow.tasks.webservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.iflow.common.domain.metadata.task.TaskType;
import com.helianhealth.iflow.core.execution.WorkflowExecutor;
import com.helianhealth.iflow.core.execution.tasks.WorkflowSystemTask;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import com.helianhealth.iflow.tasks.webservice.providers.SoapClientProvider;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * WebService (SOAP) 外呼系统任务。
 */
@Component(TaskType.TASK_TYPE_WEBSERVICE)
public class WebServiceTask extends WorkflowSystemTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebServiceTask.class);

    protected ObjectMapper objectMapper;
    protected SoapClientProvider soapClientProvider;

    @Autowired
    public WebServiceTask(SoapClientProvider soapClientProvider, ObjectMapper objectMapper) {
        this(TaskType.TASK_TYPE_WEBSERVICE, soapClientProvider, objectMapper);
    }

    protected WebServiceTask(String name, SoapClientProvider soapClientProvider, ObjectMapper objectMapper) {
        super(name);
        this.soapClientProvider = soapClientProvider;
        this.objectMapper = objectMapper;
        LOGGER.info("{} initialized...", getTaskType());
    }

    @Override
    public void execute(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
        Input input = objectMapper.convertValue(task.getInputData(), Input.class);
        if (input.getUri() == null || input.getUri().isBlank()) {
            task.setReasonForInCompletion(
                    "Missing WebService URI. See documentation for WebServiceTask for required input parameters");
            task.setStatus(TaskModel.Status.FAILED);
            return;
        }

        if (input.getSoapTemplate() == null || input.getSoapTemplate().isBlank()) {
            task.setReasonForInCompletion("Missing soapTemplate");
            task.setStatus(TaskModel.Status.FAILED);
            return;
        }

        try {
            WebServiceResponse response = soapCall(input);
            LOGGER.debug(
                    "Response: {}, {}, task:{}",
                    response.statusCode,
                    response.body,
                    task.getTaskId());
            if (response.statusCode > 199 && response.statusCode < 300) {
                task.setStatus(TaskModel.Status.COMPLETED);
            } else {
                if (response.body != null) {
                    task.setReasonForInCompletion(response.body);
                } else {
                    task.setReasonForInCompletion("No response from the remote service");
                }
                task.setStatus(TaskModel.Status.FAILED);
            }
            task.setOutputData(response.asMap());
        } catch (Exception e) {
            LOGGER.error(
                    "Failed to invoke {} task: {} - uri: {} in workflow: {}",
                    getTaskType(),
                    task.getTaskId(),
                    input.getUri(),
                    task.getWorkflowInstanceId(),
                    e);
            task.setStatus(TaskModel.Status.FAILED);
            task.setReasonForInCompletion("Failed to invoke " + getTaskType() + " task due to: " + e);
            addError(task, e.toString());
        }
    }

    /**
     * 发起 SOAP HTTP POST。{@link Input#soapTemplate} 此时应为已完成 {@code ${...}} 替换的 XML。
     */
    protected WebServiceResponse soapCall(Input input) throws Exception {
        try (CloseableHttpClient client = soapClientProvider.createHttpClient(input)) {
            HttpPost post = new HttpPost(input.getUri());
            post.setEntity(new StringEntity(input.getSoapTemplate(), ContentType.parse(input.getContentType())));

            input.getHeaders().forEach((key, value) -> {
                if (value != null) {
                    post.setHeader(key, value.toString());
                }
            });
            if (!hasHeaderIgnoreCase(input.getHeaders(), "Content-Type")) {
                post.setHeader("Content-Type", input.getContentType());
            }
            if (!hasHeaderIgnoreCase(input.getHeaders(), "SOAPAction")) {
                post.setHeader("SOAPAction", "");
            }

            return client.execute(post, response -> readResponse((ClassicHttpResponse) response));
        }
    }

    private boolean hasHeaderIgnoreCase(Map<String, Object> headers, String name) {
        if (headers == null || headers.isEmpty()) {
            return false;
        }
        for (String key : headers.keySet()) {
            if (key != null && key.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private WebServiceResponse readResponse(ClassicHttpResponse httpResponse) throws java.io.IOException {
        WebServiceResponse response = new WebServiceResponse();
        response.statusCode = httpResponse.getCode();
        response.reasonPhrase = httpResponse.getReasonPhrase();
        HttpEntity entity = httpResponse.getEntity();
        if (entity != null) {
            try {
                response.body = EntityUtils.toString(entity, "UTF-8");
            } catch (org.apache.hc.core5.http.ParseException e) {
                throw new java.io.IOException("failed to parse SOAP response", e);
            } finally {
                EntityUtils.consumeQuietly(entity);
            }
        }
        Map<String, String> headers = new LinkedHashMap<>();
        for (var h : httpResponse.getHeaders()) {
            headers.put(h.getName(), h.getValue());
        }
        response.headers = headers;
        return response;
    }

    @Override
    public void cancel(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
        task.setStatus(TaskModel.Status.CANCELED);
    }

    public static class WebServiceResponse {

        public String body;
        public Map<String, String> headers;
        public int statusCode;
        public String reasonPhrase;

        @Override
        public String toString() {
            return "WebServiceResponse [body="
                    + body
                    + ", headers="
                    + headers
                    + ", statusCode="
                    + statusCode
                    + ", reasonPhrase="
                    + reasonPhrase
                    + "]";
        }

        public Map<String, Object> asMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("body", body);
            map.put("headers", headers);
            map.put("statusCode", statusCode);
            map.put("reasonPhrase", reasonPhrase);
            return map;
        }
    }

    /**
     * WebService 入参：与 {@link com.helianhealth.agent.tasks.http.HttpTask.Input} 同构，
     * 仅将 {@code body} 换成 {@link #soapTemplate}。
     */
    public static class Input {

        private String uri;
        private String soapTemplate;
        private Map<String, Object> headers = new HashMap<>();
        private String contentType = "text/xml;charset=UTF-8";

        /** 建连超时（毫秒），未指定时使用系统默认 5000ms */
        private Integer connectionTimeOut;

        /** 读取超时（毫秒），未指定时使用系统默认 30000ms */
        private Integer readTimeOut;

        public String getUri() {
            return uri;
        }

        public void setUri(String uri) {
            this.uri = uri;
        }

        public String getSoapTemplate() {
            return soapTemplate;
        }

        public void setSoapTemplate(String soapTemplate) {
            this.soapTemplate = soapTemplate;
        }

        public Map<String, Object> getHeaders() {
            return headers;
        }

        public void setHeaders(Map<String, Object> headers) {
            this.headers = headers;
        }

        public String getContentType() {
            return contentType;
        }

        public void setContentType(String contentType) {
            this.contentType = contentType;
        }

        public Integer getConnectionTimeOut() {
            return connectionTimeOut;
        }

        public Integer getReadTimeOut() {
            return readTimeOut;
        }

        public void setConnectionTimeOut(Integer connectionTimeOut) {
            this.connectionTimeOut = connectionTimeOut;
        }

        public void setReadTimeOut(Integer readTimeOut) {
            this.readTimeOut = readTimeOut;
        }
    }
}
