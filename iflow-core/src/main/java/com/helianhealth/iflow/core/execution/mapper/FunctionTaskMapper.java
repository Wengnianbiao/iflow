package com.helianhealth.iflow.core.execution.mapper;

import com.helianhealth.iflow.common.domain.metadata.task.TaskType;
import com.helianhealth.iflow.core.model.TaskModel;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FUNCTION 任务 Mapper。
 */
@Component
public class FunctionTaskMapper implements TaskMapper {

    @Override
    public String getTaskType() {
        return TaskType.TASK_TYPE_FUNCTION;
    }

    @Override
    public List<TaskModel> getMappedTasks(TaskMapperContext context) {
        Map<String, Object> resolvedInput = context.getTaskInput();

        TaskModel task = context.createTaskModel();
        task.setTaskType(TaskType.TASK_TYPE_FUNCTION);
        task.setStartTime(System.currentTimeMillis());
        task.setInputData(
                resolvedInput == null ? new HashMap<>() : new HashMap<>(resolvedInput));
        task.setStatus(TaskModel.Status.IN_PROGRESS);
        return List.of(task);
    }
}
