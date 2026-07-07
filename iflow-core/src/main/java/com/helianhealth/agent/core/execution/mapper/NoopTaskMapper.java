package com.helianhealth.agent.core.execution.mapper;

import com.helianhealth.agent.common.domain.metadata.task.TaskType;
import com.helianhealth.agent.core.model.TaskModel;
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
        task.setStartTime(System.currentTimeMillis());
        task.setStatus(TaskModel.Status.IN_PROGRESS);
        return List.of(task);
    }
}
