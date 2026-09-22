package com.helianhealth.iflow.tasks.http;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.iflow.common.domain.metadata.task.TaskType;
import com.helianhealth.iflow.core.execution.WorkflowExecutor;
import com.helianhealth.iflow.core.execution.tasks.WorkflowSystemTask;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import com.helianhealth.iflow.tasks.http.providers.RestTemplateProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * HTTP 外呼系统任务
 */
@Component(TaskType.TASK_TYPE_HTTP)
public class HttpTask extends WorkflowSystemTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpTask.class);

    private final TypeReference<Map<String, Object>> mapOfObj = new TypeReference<>() {};
    private final TypeReference<List<Object>> listOfObj = new TypeReference<>() {};

    protected ObjectMapper objectMapper;
    protected RestTemplateProvider restTemplateProvider;

    @Autowired
    public HttpTask(RestTemplateProvider restTemplateProvider, ObjectMapper objectMapper) {
        this(TaskType.TASK_TYPE_HTTP, restTemplateProvider, objectMapper);
    }

    protected HttpTask(String name, RestTemplateProvider restTemplateProvider, ObjectMapper objectMapper) {
        super(name);
        this.restTemplateProvider = restTemplateProvider;
        this.objectMapper = objectMapper;
        LOGGER.info("{} initialized...", getTaskType());
    }

    @Override
    public void execute(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
        Object request = task.getInputData();

        Input input = objectMapper.convertValue(request, Input.class);
        if (input.getUri() == null) {
            task.setReasonForInCompletion(
                    "Missing HTTP URI.  See documentation for HttpTask for required input parameters");
            task.setStatus(TaskModel.Status.FAILED);
            return;
        }

        if (input.getMethod() == null) {
            task.setReasonForInCompletion("No HTTP method specified");
            task.setStatus(TaskModel.Status.FAILED);
            return;
        }

        try {
            HttpResponse response = httpCall(input);
            LOGGER.debug(
                    "Response: {}, {}, task:{}",
                    response.statusCode,
                    response.body,
                    task.getTaskId());
            if (response.statusCode > 199 && response.statusCode < 300) {
                task.setStatus(TaskModel.Status.COMPLETED);
            } else {
                if (response.body != null) {
                    task.setReasonForInCompletion(response.body.toString());
                } else {
                    task.setReasonForInCompletion("No response from the remote service");
                }
                task.setStatus(TaskModel.Status.FAILED);
            }
            task.setOutputData(response.asMap());
        } catch (Exception e) {
            LOGGER.error(
                    "Failed to invoke {} task: {} - uri: {}, vipAddress: {} in workflow: {}",
                    getTaskType(),
                    task.getTaskId(),
                    input.getUri(),
                    input.getVipAddress(),
                    task.getWorkflowInstanceId(),
                    e);
            task.setStatus(TaskModel.Status.FAILED);
            task.setReasonForInCompletion("Failed to invoke " + getTaskType() + " task due to: " + e);
            addError(task, e.toString());
        }
    }

    /**
     * 发起 HTTP 调用。protected 以便子类复用。
     */
    protected HttpResponse httpCall(Input input) throws Exception {
        RestTemplate restTemplate = restTemplateProvider.getRestTemplate(input);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf(input.getContentType()));
        headers.setAccept(input.getAcceptList().stream()
                .map(MediaType::valueOf)
                .collect(Collectors.toList()));

        input.getHeaders().forEach((key, value) -> {
            if (value != null) {
                headers.add(key, value.toString());
            }
        });

        HttpEntity<Object> request = new HttpEntity<>(input.getBody(), headers);
        HttpResponse response = new HttpResponse();
        try {
            ResponseEntity<String> responseEntity = restTemplate.exchange(
                    input.getUri(),
                    HttpMethod.valueOf(input.getMethod()),
                    request,
                    String.class);
            if (responseEntity.getStatusCode().is2xxSuccessful() && responseEntity.hasBody()) {
                response.body = extractBody(responseEntity.getBody());
            }
            int statusCode = responseEntity.getStatusCode().value();
            response.statusCode = statusCode;
            response.reasonPhrase = HttpStatus.valueOf(statusCode).getReasonPhrase();
            response.headers = responseEntity.getHeaders();
            return response;
        } catch (RestClientException ex) {
            LOGGER.error(
                    "Got unexpected http response - uri: {}, vipAddress: {}",
                    input.getUri(),
                    input.getVipAddress(),
                    ex);
            throw new Exception(ex.getLocalizedMessage(), ex);
        }
    }

    private Object extractBody(String responseBody) {
        try {
            JsonNode node = objectMapper.readTree(responseBody);
            if (node.isArray()) {
                return objectMapper.convertValue(node, listOfObj);
            } else if (node.isObject()) {
                return objectMapper.convertValue(node, mapOfObj);
            } else if (node.isNumber()) {
                return objectMapper.convertValue(node, Double.class);
            } else {
                return node.asText();
            }
        } catch (IOException ex) {
            LOGGER.error("Error extracting response body", ex);
            return responseBody;
        }
    }

    @Override
    public void cancel(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
        task.setStatus(TaskModel.Status.CANCELED);
    }

    public static class HttpResponse {

        public Object body;
        public MultiValueMap<String, String> headers;
        public int statusCode;
        public String reasonPhrase;

        @Override
        public String toString() {
            return "HttpResponse [body="
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

    public static class Input {

        private String method;
        private String vipAddress;
        private String appName;
        private Map<String, Object> headers = new HashMap<>();
        private String uri;
        private Object body;
        private List<String> accept =
                new ArrayList<>(Collections.singletonList(MediaType.APPLICATION_JSON_VALUE));
        private String contentType = MediaType.APPLICATION_JSON_VALUE;

        /** 建连超时（毫秒），未指定时使用系统默认 5000ms */
        private Integer connectionTimeOut;

        /** 读取超时（毫秒），未指定时使用系统默认 30000ms */
        private Integer readTimeOut;

        public String getMethod() {
            return method;
        }

        public void setMethod(String method) {
            this.method = method;
        }

        public Map<String, Object> getHeaders() {
            return headers;
        }

        public void setHeaders(Map<String, Object> headers) {
            this.headers = headers;
        }

        public Object getBody() {
            return body;
        }

        public void setBody(Object body) {
            this.body = body;
        }

        public String getUri() {
            return uri;
        }

        public void setUri(String uri) {
            this.uri = uri;
        }

        public String getVipAddress() {
            return vipAddress;
        }

        public void setVipAddress(String vipAddress) {
            this.vipAddress = vipAddress;
        }

        public String getAccept() {
            return accept != null && !accept.isEmpty()
                    ? accept.get(0)
                    : MediaType.APPLICATION_JSON_VALUE;
        }

        public List<String> getAcceptList() {
            return accept;
        }

        @JsonSetter("accept")
        public void setAccept(Object accept) {
            if (accept == null) {
                return;
            }
            if (accept instanceof String) {
                this.accept = Collections.singletonList((String) accept);
            } else if (accept instanceof List) {
                this.accept = ((List<?>) accept).stream()
                        .map(Object::toString)
                        .collect(Collectors.toList());
            }
        }

        public String getContentType() {
            return contentType;
        }

        public void setContentType(String contentType) {
            this.contentType = contentType;
        }

        public String getAppName() {
            return appName;
        }

        public void setAppName(String appName) {
            this.appName = appName;
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
