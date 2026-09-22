package com.helianhealth.agent.tasks.mapping;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.helianhealth.agent.common.domain.metadata.task.TaskType;
import com.helianhealth.agent.core.execution.WorkflowExecutor;
import com.helianhealth.agent.core.execution.tasks.WorkflowSystemTask;
import com.helianhealth.agent.core.model.TaskModel;
import com.helianhealth.agent.core.model.WorkflowModel;
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
            task.setOutputData(map(task.getInputData()));
            task.setStatus(TaskModel.Status.COMPLETED);
        } catch (Exception e) {
            String msg = e.getMessage();
            LOGGER.error(
                    "MAPPING task failed: workflow={}, task={}",
                    workflow.getWorkflowId(),
                    task.getTaskId(),
                    e);
            task.setStatus(TaskModel.Status.FAILED);
            task.setReasonForInCompletion(msg);
            addError(task, msg);
        }
    }

    private Map<String, Object> map(Map<String, Object> input) {
        Object payload = resolvePayload(input);
        List<ParamMappingConfig> configs =
                objectMapper.convertValue(input.get(INPUT_PARAMS_CONFIG), PARAM_MAPPING_CONFIGS);
        if (configs == null || configs.isEmpty()) {
            throw new IllegalArgumentException("MAPPING inputParameters.paramsConfig must be a non-empty JSON array");
        }
        Map<String, Object> target = paramMapTransformer.transform(configs, payload);
        LOGGER.debug("MAPPING outputKeys={}", target.keySet());
        return target;
    }

    private static Object resolvePayload(Map<String, Object> input) {
        if (input == null || !input.containsKey(INPUT_PAYLOAD)) {
            throw new IllegalArgumentException("payload is required");
        }
        return input.get(INPUT_PAYLOAD);
    }
}
