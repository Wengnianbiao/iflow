package com.helianhealth.agent.mysql.dao;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.helianhealth.agent.common.domain.run.Task;
import com.helianhealth.agent.common.domain.run.Workflow;
import com.helianhealth.agent.core.model.TaskModel;
import com.helianhealth.agent.core.model.WorkflowExecutionMapper;
import com.helianhealth.agent.core.model.WorkflowModel;

import java.io.IOException;

final class MySqlExecutionJsonSupport {

    private final ObjectMapper objectMapper;

    MySqlExecutionJsonSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    String toWorkflowPayload(WorkflowModel workflow) {
        return toJson(WorkflowExecutionMapper.toWorkflowHeader(workflow));
    }

    WorkflowModel fromWorkflowPayload(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
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
        return toJson(WorkflowExecutionMapper.toTask(task));
    }

    TaskModel fromTaskPayload(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
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
