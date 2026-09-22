package com.helianhealth.iflow.core.execution.mapper;

import com.helianhealth.iflow.common.domain.metadata.task.TaskType;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.utils.ParametersUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * TERMINATE 任务 Mapper。
 */
@Component
public class TerminateTaskMapper implements TaskMapper {

    private final ParametersUtils parametersUtils;

    public TerminateTaskMapper(ParametersUtils parametersUtils) {
        this.parametersUtils = parametersUtils;
    }

    @Override
    public String getTaskType() {
        return TaskType.TASK_TYPE_TERMINATE;
    }

    @Override
    public List<TaskModel> getMappedTasks(TaskMapperContext context) {
        Map<String, Object> taskInput = resolveTaskInput(context);
        TaskModel task = context.createTaskModel();
        task.setTaskType(TaskType.TASK_TYPE_TERMINATE);
        task.setStartTime(System.currentTimeMillis());
        task.setInputData(taskInput);
        task.setStatus(TaskModel.Status.IN_PROGRESS);
        return List.of(task);
    }

    private Map<String, Object> resolveTaskInput(TaskMapperContext context) {
        if (context.getTaskInput() != null) {
            return context.getTaskInput();
        }
        return parametersUtils.getTaskInput(
                context.getWorkflowTask().getInputParameters(),
                context.getWorkflowModel(),
                context.getTaskId());
    }
}
