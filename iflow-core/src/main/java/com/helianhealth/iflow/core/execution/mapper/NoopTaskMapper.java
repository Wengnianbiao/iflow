package com.helianhealth.iflow.core.execution.mapper;

import com.helianhealth.iflow.common.domain.metadata.task.TaskType;
import com.helianhealth.iflow.core.model.TaskModel;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * NOOP 任务 Mapper。
 */
@Component
public class NoopTaskMapper implements TaskMapper {

    @Override
    public String getTaskType() {
        return TaskType.TASK_TYPE_NOOP;
    }

    @Override
    public List<TaskModel> getMappedTasks(TaskMapperContext context) {
        TaskModel task = context.createTaskModel();
        task.setTaskType(TaskType.TASK_TYPE_NOOP);

        task.setStatus(TaskModel.Status.SCHEDULED);
        return List.of(task);
    }
}
