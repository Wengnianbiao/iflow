package com.helianhealth.agent.core.execution.mapper;

import com.helianhealth.agent.common.domain.metadata.task.TaskType;
import com.helianhealth.agent.core.model.TaskModel;
import com.helianhealth.agent.core.utils.ParametersUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * INLINE 任务 Mapper。
 *
 * <p>{@code expression}、{@code evaluatorType} 和脚本参数统一从
 * {@code WorkflowTask.inputParameters} 解析后写入任务 inputData。
 */
@Component
public class InlineTaskMapper implements TaskMapper {

    private final ParametersUtils parametersUtils;

    public InlineTaskMapper(ParametersUtils parametersUtils) {
        this.parametersUtils = parametersUtils;
    }

    @Override
    public String getTaskType() {
        return TaskType.TASK_TYPE_INLINE;
    }

    @Override
    public List<TaskModel> getMappedTasks(TaskMapperContext context) {
        Map<String, Object> taskInput =
                parametersUtils.getTaskInput(
                        context.getWorkflowTask().getInputParameters(),
                        context.getWorkflowModel(),
                        context.getTaskId());

        TaskModel inlineTask = context.createTaskModel();
        inlineTask.setTaskType(TaskType.TASK_TYPE_INLINE);
        inlineTask.setStartTime(System.currentTimeMillis());
        inlineTask.setInputData(taskInput);
        inlineTask.setStatus(TaskModel.Status.IN_PROGRESS);
        return List.of(inlineTask);
    }
}
