package com.helianhealth.iflow.persistence.mysql.dao;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.helianhealth.iflow.common.domain.run.Task;
import com.helianhealth.iflow.common.domain.run.Workflow;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowExecutionMapper;
import com.helianhealth.iflow.core.model.WorkflowModel;

import java.io.IOException;
import java.util.Map;

final class MySqlExecutionJsonSupport {

    private final ObjectMapper objectMapper;
    private final ExecutionPayloadCodec payloadCodec;
    private final PayloadTruncator payloadTruncator;

    MySqlExecutionJsonSupport(ObjectMapper objectMapper) {
        this(objectMapper, new ExecutionPayloadCodec());
    }

    MySqlExecutionJsonSupport(ObjectMapper objectMapper, ExecutionPayloadCodec payloadCodec) {
        this.objectMapper = objectMapper;
        this.payloadCodec = payloadCodec;
        this.payloadTruncator = new PayloadTruncator(objectMapper);
    }

    String toWorkflowPayload(WorkflowModel workflow) {
        Workflow persisted = WorkflowExecutionMapper.toWorkflowHeader(workflow);
        if (persisted != null) {
            String label = "工作流 " + persisted.getWorkflowId();
            persisted.setInput(truncateInput(label + " 输入", persisted.getInput()));
            persisted.setOutput(payloadTruncator.truncate(label + " 输出", persisted.getOutput()));
        }
        return payloadCodec.encode(toJson(persisted));
    }

    WorkflowModel fromWorkflowPayload(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        json = payloadCodec.decode(json);
        try {
            ObjectNode root = (ObjectNode) objectMapper.readTree(json);
            normalizeLegacyWorkflowHeader(root);
            Workflow workflow =
                    objectMapper.readerFor(Workflow.class)
                            .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                            .readValue(root);
            if (workflow.getTasks() == null) {
                workflow.setTasks(new java.util.LinkedList<>());
            }
            return WorkflowExecutionMapper.toModel(workflow);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not read workflow payload", ex);
        }
    }

    String toTaskPayload(TaskModel task) {
        Task persisted = WorkflowExecutionMapper.toTask(task);
        if (persisted != null) {
            String label = "任务 " + persisted.getTaskId();
            persisted.setInputData(truncateInput(label + " 输入", persisted.getInputData()));
            persisted.setOutputData(payloadTruncator.truncate(label + " 输出", persisted.getOutputData()));
        }
        return payloadCodec.encode(toJson(persisted));
    }

    /** 只替换持久化 DTO 的字段；截断器不修改与运行时共享的嵌套值。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> truncateInput(String label, Map<String, Object> input) {
        // Map 序列化为 JSON 对象，截断后仍为对象；未超限时原样返回。
        return (Map<String, Object>) payloadTruncator.truncate(label, input);
    }

    TaskModel fromTaskPayload(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        json = payloadCodec.decode(json);
        try {
            return WorkflowExecutionMapper.toTaskModel(objectMapper.readValue(json, Task.class));
        } catch (IOException ex) {
            throw new IllegalStateException("Could not read task payload", ex);
        }
    }

    static String taskKey(TaskModel task) {
        return task.getReferenceTaskName() + "_" + task.getRetryCount();
    }

    private void normalizeLegacyWorkflowHeader(ObjectNode root) {
        if (root != null && !root.has("createTime") && root.has("startTime")) {
            root.set("createTime", root.get("startTime"));
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not write execution payload", ex);
        }
    }
}
