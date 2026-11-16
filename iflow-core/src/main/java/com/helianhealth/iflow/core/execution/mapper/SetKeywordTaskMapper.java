package com.helianhealth.iflow.core.execution.mapper;

import com.helianhealth.iflow.common.domain.metadata.task.TaskType;
import com.helianhealth.iflow.core.model.TaskModel;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SetKeywordTaskMapper implements TaskMapper {

    @Override
    public String getTaskType() {
        return TaskType.TASK_TYPE_SET_KEYWORD;
    }

    @Override
    public List<TaskModel> getMappedTasks(TaskMapperContext context) {
        TaskModel task = context.createTaskModel();
        task.setInputData(context.getTaskInput());
        task.setStatus(TaskModel.Status.SCHEDULED);
        return List.of(task);
    }
}
