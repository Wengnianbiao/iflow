package com.helianhealth.agent.core.execution.mapper;

import com.helianhealth.agent.common.domain.metadata.task.TaskType;
import com.helianhealth.agent.core.model.TaskModel;
import com.helianhealth.agent.core.model.WorkflowModel;
import com.helianhealth.agent.core.utils.ParametersUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class DoWhileTaskMapper implements TaskMapper {

    private final ParametersUtils parametersUtils;

    public DoWhileTaskMapper(ParametersUtils parametersUtils) {
        this.parametersUtils = parametersUtils;
    }

    @Override
    public String getTaskType() {
        return TaskType.TASK_TYPE_DO_WHILE;
    }

    @Override
    public List<TaskModel> getMappedTasks(TaskMapperContext context) {
        var workflowTask = context.getWorkflowTask();
        WorkflowModel workflowModel = context.getWorkflowModel();

        TaskModel existing = workflowModel.getTaskByRefName(workflowTask.getTaskReferenceName());
        if (existing != null && existing.getStatus().isTerminal()) {
            return List.of();
        }

        TaskModel doWhileTask = context.createTaskModel();
        doWhileTask.setTaskType(TaskType.TASK_TYPE_DO_WHILE);
        doWhileTask.setStatus(TaskModel.Status.IN_PROGRESS);
        doWhileTask.setStartTime(System.currentTimeMillis());
        doWhileTask.setRetryCount(context.getRetryCount());

        Map<String, Object> taskInput = parametersUtils.getTaskInput(
                context.getWorkflowTask().getInputParameters(),
                context.getWorkflowModel(),
                context.getTaskId());
        doWhileTask.setInputData(taskInput);
        return List.of(doWhileTask);
    }
}
