package com.helianhealth.iflow.tasks.spi;

import com.helianhealth.iflow.common.domain.metadata.task.TaskType;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowTask;
import com.helianhealth.iflow.core.execution.mapper.TaskMapper;
import com.helianhealth.iflow.core.execution.mapper.TaskMapperContext;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.utils.ParametersUtils;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * SPI 任务 Mapper：只解析 {@code remoteExtension} 与 {@code spiInput} 中的
 * 表达式；Java 源码保持原文。
 */
@Component
public class SpiTaskMapper implements TaskMapper {

    private static final Set<String> INPUT_PARAMETER_KEYS =
            Set.of(
                    SpiTaskInput.REMOTE_EXTENSION,
                    SpiTaskInput.EXTENSION_MODE,
                    SpiTaskInput.CLASS_NAME,
                    SpiTaskInput.SOURCE_CODE,
                    SpiTaskInput.SPI_INPUT);

    private final ParametersUtils parametersUtils;

    public SpiTaskMapper(ParametersUtils parametersUtils) {
        this.parametersUtils = parametersUtils;
    }

    @Override
    public String getTaskType() {
        return TaskType.TASK_TYPE_SPI;
    }

    @Override
    public List<TaskModel> getMappedTasks(TaskMapperContext context) {
        Map<String, Object> input = resolveTaskInput(context);
        SpiTaskInput.extractSpiInput(input);
        SpiTaskInput.extractExtensionDefinition(input);

        TaskModel taskModel = context.createTaskModel();
        taskModel.setInputData(input);
        taskModel.setStatus(TaskModel.Status.IN_PROGRESS);
        taskModel.setRetryCount(context.getRetryCount());
        return List.of(taskModel);
    }

    private Map<String, Object> resolveTaskInput(TaskMapperContext context) {
        WorkflowTask workflowTask = context.getWorkflowTask();
        Map<String, Object> inputParameters = new HashMap<>(workflowTask.getInputParameters());
        if (!INPUT_PARAMETER_KEYS.containsAll(inputParameters.keySet())) {
            throw new IllegalArgumentException(
                    "SPI inputParameters contains unsupported fields");
        }
        if (!inputParameters.containsKey(SpiTaskInput.SPI_INPUT)) {
            throw new IllegalArgumentException("SPI inputParameters.spiInput is required");
        }

        Map<String, Object> expressionParameters = new HashMap<>();
        expressionParameters.put(
                SpiTaskInput.REMOTE_EXTENSION,
                inputParameters.get(SpiTaskInput.REMOTE_EXTENSION));
        expressionParameters.put(
                SpiTaskInput.SPI_INPUT,
                inputParameters.get(SpiTaskInput.SPI_INPUT));
        Map<String, Object> resolved =
                new HashMap<>(
                        parametersUtils.getTaskInput(
                                expressionParameters,
                                context.getWorkflowModel(),
                                context.getTaskId()));

        copyRaw(inputParameters, resolved, SpiTaskInput.EXTENSION_MODE);
        copyRaw(inputParameters, resolved, SpiTaskInput.CLASS_NAME);
        copyRaw(inputParameters, resolved, SpiTaskInput.SOURCE_CODE);
        return resolved;
    }

    private void copyRaw(Map<String, Object> source, Map<String, Object> target, String key) {
        if (source.containsKey(key)) {
            target.put(key, source.get(key));
        }
    }
}
