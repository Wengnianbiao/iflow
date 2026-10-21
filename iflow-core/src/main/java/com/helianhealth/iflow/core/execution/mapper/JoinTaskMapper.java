package com.helianhealth.iflow.core.execution.mapper;

import com.helianhealth.iflow.common.domain.metadata.task.TaskType;
import com.helianhealth.iflow.core.model.TaskModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class JoinTaskMapper implements TaskMapper {

    public static final Logger LOGGER = LoggerFactory.getLogger(JoinTaskMapper.class);

    @Override
    public String getTaskType() {
        return TaskType.TASK_TYPE_JOIN;
    }

    @Override
    public List<TaskModel> getMappedTasks(TaskMapperContext taskMapperContext) {
        LOGGER.debug("TaskMapperContext {} in JoinTaskMapper", taskMapperContext);
        Map<String, Object> joinInput = new HashMap<>();
        joinInput.put("joinOn", taskMapperContext.getWorkflowTask().getJoinOn());

        TaskModel joinTask = taskMapperContext.createTaskModel();
        joinTask.setTaskType(TaskType.TASK_TYPE_JOIN);
        joinTask.setTaskDefName(TaskType.TASK_TYPE_JOIN);

        joinTask.setInputData(joinInput);
        joinTask.setStatus(TaskModel.Status.SCHEDULED);
        return List.of(joinTask);
    }
}
