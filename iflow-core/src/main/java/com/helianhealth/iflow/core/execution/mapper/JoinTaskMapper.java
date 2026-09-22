package com.helianhealth.iflow.core.execution.mapper;

import com.helianhealth.iflow.common.domain.metadata.task.TaskType;
import com.helianhealth.iflow.core.model.TaskModel;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JOIN 任务 Mapper。
 */
@Component
public class JoinTaskMapper implements TaskMapper {

    @Override
    public String getTaskType() {
        return TaskType.TASK_TYPE_JOIN;
    }

    @Override
    public List<TaskModel> getMappedTasks(TaskMapperContext context) {
        Map<String, Object> joinInput = new HashMap<>();
        joinInput.put("joinOn", context.getWorkflowTask().getJoinOn());

        TaskModel joinTask = context.createTaskModel();
        joinTask.setTaskType(TaskType.TASK_TYPE_JOIN);
        joinTask.setTaskDefName(TaskType.TASK_TYPE_JOIN);
        joinTask.setStartTime(System.currentTimeMillis());
        joinTask.setInputData(joinInput);
        joinTask.setStatus(TaskModel.Status.IN_PROGRESS);
        return List.of(joinTask);
    }
}
