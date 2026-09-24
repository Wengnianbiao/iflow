package com.helianhealth.iflow.tasks.mapping;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.helianhealth.iflow.common.domain.metadata.task.TaskType;
import com.helianhealth.iflow.core.execution.WorkflowExecutor;
import com.helianhealth.iflow.core.execution.tasks.WorkflowSystemTask;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 参数映射系统任务：硬编码 {@code payload}（表达式或参数对象）按 {@code paramsConfig} 映射出参，映射字段直接写入 {@code outputData} 根级。
 */
@Component(TaskType.TASK_TYPE_MAPPING)
public class MappingTask extends WorkflowSystemTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(MappingTask.class);

    public static final String INPUT_PAYLOAD = "payload";

    public static final String INPUT_PARAMS_CONFIG = "paramsConfig";

    private static final TypeReference<List<ParamMappingConfig>> PARAM_MAPPING_CONFIGS = new TypeReference<>() {};

    private final ParamMapTransformer paramMapTransformer;

    private final ObjectMapper objectMapper;

    public MappingTask(ParamMapTransformer paramMapTransformer, ObjectMapper objectMapper) {
        super(TaskType.TASK_TYPE_MAPPING);
        this.paramMapTransformer = paramMapTransformer;
        this.objectMapper = objectMapper.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    @Override
    public void execute(WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        try {
            Object payload = task.getInputData().get(INPUT_PAYLOAD);
            List<ParamMappingConfig> fields =
                    objectMapper.convertValue(task.getInputData().get(INPUT_PARAMS_CONFIG), PARAM_MAPPING_CONFIGS);
            if (fields == null || fields.isEmpty()) {
                task.setStatus(TaskModel.Status.COMPLETED);
                task.setOutputData(new Object());
                return;
            }
            Map<String, Object> output = paramMapTransformer.transform(fields, payload);
            task.setOutputData(output);
            task.setStatus(TaskModel.Status.COMPLETED);
        } catch (Exception e) {
            LOGGER.error(
                    "MAPPING task failed: workflow={}, task={}",
                    workflow.getWorkflowId(),
                    task.getTaskId(),
                    e);
            task.setStatus(TaskModel.Status.FAILED);
            task.setReasonForInCompletion("Failed to invoke " + getTaskType() + " task due to: " + e);
        }
    }
}
