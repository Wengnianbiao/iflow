package com.helianhealth.agent.core.execution.mapper;

import com.helianhealth.agent.common.domain.metadata.task.TaskType;
import com.helianhealth.agent.common.domain.metadata.workflow.WorkflowTask;
import com.helianhealth.agent.core.model.TaskModel;
import com.helianhealth.agent.core.utils.ParametersUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * PARAM_MAP 任务 Mapper
 */
@Component
public class ParamMappingTaskMapper implements TaskMapper {

    public static final String PAYLOAD = "payload";
    public static final String PARAMS_CONFIG = "paramsConfig";
    private static final Set<String> INPUT_KEYS = Set.of(PAYLOAD, PARAMS_CONFIG);

    private final ParametersUtils parametersUtils;

    public ParamMappingTaskMapper(ParametersUtils parametersUtils) {
        this.parametersUtils = parametersUtils;
    }

    @Override
    public String getTaskType() {
        return TaskType.TASK_TYPE_PARAM_MAP;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<TaskModel> getMappedTasks(TaskMapperContext context) {
        WorkflowTask workflowTask = context.getWorkflowTask();
        Map<String, Object> inputParameters = workflowTask.getInputParameters();
        validateInputKeys(inputParameters);

        Object rawParamsConfig = inputParameters.get(PARAMS_CONFIG);
        if (!(rawParamsConfig instanceof List<?> configs) || configs.isEmpty()) {
            throw new IllegalArgumentException("PARAM_MAP inputParameters.paramsConfig must be a non-empty JSON array");
        }

        Object payload = resolvePayload(context, inputParameters.get(PAYLOAD));
        if (!(payload instanceof Map<?, ?> payloadMap)) {
            throw new IllegalArgumentException("PARAM_MAP inputParameters.payload must resolve to a JSON object");
        }

        Map<String, Object> taskInput = new LinkedHashMap<>();
        taskInput.put(PAYLOAD, new LinkedHashMap<>((Map<String, Object>) payloadMap));
        taskInput.put(PARAMS_CONFIG, new ArrayList<>(configs));

        TaskModel task = context.createTaskModel();
        task.setTaskType(TaskType.TASK_TYPE_PARAM_MAP);
        task.setStartTime(System.currentTimeMillis());
        task.setInputData(taskInput);
        task.setStatus(TaskModel.Status.IN_PROGRESS);
        return List.of(task);
    }

    private void validateInputKeys(Map<String, Object> inputParameters) {
        if (inputParameters == null || !inputParameters.keySet().equals(INPUT_KEYS)) {
            throw new IllegalArgumentException(
                    "PARAM_MAP inputParameters must contain exactly payload and paramsConfig");
        }
    }

    private Object resolvePayload(TaskMapperContext context, Object rawPayload) {
        if (context.getTaskInput() != null) {
            return context.getTaskInput().get(PAYLOAD);
        }
        Map<String, Object> payloadInput = new HashMap<>();
        payloadInput.put(PAYLOAD, rawPayload);
        return parametersUtils
                .getTaskInput(payloadInput, context.getWorkflowModel(), context.getTaskId())
                .get(PAYLOAD);
    }
}
