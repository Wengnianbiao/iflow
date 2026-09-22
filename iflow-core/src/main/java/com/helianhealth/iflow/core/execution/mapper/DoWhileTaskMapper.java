package com.helianhealth.iflow.core.execution.mapper;

import com.helianhealth.iflow.common.domain.metadata.task.TaskType;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowTask;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import com.helianhealth.iflow.core.utils.ParametersUtils;
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
        WorkflowTask workflowTask = context.getWorkflowTask();
        WorkflowModel workflowModel = context.getWorkflowModel();

        TaskModel task = workflowModel.getTaskByRefName(workflowTask.getTaskReferenceName());
        if (task != null && task.getStatus().isTerminal()) {
            return List.of();
        }

        TaskModel doWhileTask = context.createTaskModel();
        doWhileTask.setTaskType(TaskType.TASK_TYPE_DO_WHILE);
        doWhileTask.setStatus(TaskModel.Status.IN_PROGRESS);
        doWhileTask.setStartTime(System.currentTimeMillis());
        doWhileTask.setRetryCount(context.getRetryCount());

        Map<String, Object> taskInput = parametersUtils.getTaskInput(
                workflowTask.getInputParameters(),
                workflowModel,
                doWhileTask.getTaskId());
        doWhileTask.setInputData(taskInput);
        return List.of(doWhileTask);
    }
}
