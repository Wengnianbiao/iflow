package com.helianhealth.agent.core.execution.mapper;

import com.helianhealth.agent.common.domain.metadata.task.TaskType;
import com.helianhealth.agent.common.domain.metadata.workflow.WorkflowTask;
import com.helianhealth.agent.core.model.TaskModel;
import com.helianhealth.agent.core.utils.ParametersUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MAPPING 任务 Mapper
 */
@Component
public class ParamMappingTaskMapper implements TaskMapper {

    public static final String PAYLOAD = "payload";
    public static final String PARAMS_CONFIG = "paramsConfig";

    private final ParametersUtils parametersUtils;

    public ParamMappingTaskMapper(ParametersUtils parametersUtils) {
        this.parametersUtils = parametersUtils;
    }

    @Override
    public String getTaskType() {
        return TaskType.TASK_TYPE_MAPPING;
    }

    @Override
    public List<TaskModel> getMappedTasks(TaskMapperContext context) {
        WorkflowTask workflowTask = context.getWorkflowTask();
        Map<String, Object> inputParameters = workflowTask.getInputParameters();
        validateInputParameters(inputParameters);

        Map<String, Object> resolvedInput = resolveInputs(context, inputParameters);

        Object rawParamsConfig = resolvedInput.get(PARAMS_CONFIG);
        if (!(rawParamsConfig instanceof List<?> configs) || configs.isEmpty()) {
            throw new IllegalArgumentException("MAPPING inputParameters.paramsConfig must be a non-empty JSON array");
        }

        Map<String, Object> taskInput = new LinkedHashMap<>(resolvedInput);
        taskInput.put(PARAMS_CONFIG, new ArrayList<>(configs));

        TaskModel task = context.createTaskModel();
        task.setTaskType(TaskType.TASK_TYPE_MAPPING);
        task.setStartTime(System.currentTimeMillis());
        task.setInputData(taskInput);
        task.setStatus(TaskModel.Status.IN_PROGRESS);
        return List.of(task);
    }

    private void validateInputParameters(Map<String, Object> inputParameters) {
        if (inputParameters == null
                || !inputParameters.containsKey(PAYLOAD)
                || !inputParameters.containsKey(PARAMS_CONFIG)) {
            throw new IllegalArgumentException(
                    "MAPPING inputParameters must contain payload and paramsConfig");
        }
    }

    private Map<String, Object> resolveInputs(
            TaskMapperContext context, Map<String, Object> inputParameters) {
        if (context.getTaskInput() != null) {
            return context.getTaskInput();
        }
        return parametersUtils
                .getTaskInput(inputParameters, context.getWorkflowModel(), context.getTaskId());
    }
}
