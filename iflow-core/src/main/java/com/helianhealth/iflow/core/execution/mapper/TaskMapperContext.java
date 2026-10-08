package com.helianhealth.iflow.core.execution.mapper;

import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowTask;
import com.helianhealth.iflow.core.execution.DeciderService;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

/**
 * Decider 与 TaskMapper 之间的上下文
 */
@Getter
@Builder(toBuilder = true)
public class TaskMapperContext {

    private final WorkflowModel workflowModel;
    private final WorkflowTask workflowTask;
    private final Map<String, Object> taskInput;
    private final String taskId;
    @Builder.Default
    private final int retryCount = 0;
    private final String retryTaskId;
    private final DeciderService deciderService;
    private final String forkTaskId;

    public TaskModel createTaskModel() {
        TaskModel taskModel = new TaskModel();
        taskModel.setReferenceTaskName(workflowTask.getTaskReferenceName());
        taskModel.setWorkflowInstanceId(workflowModel.getWorkflowId());
        taskModel.setWorkflowType(workflowModel.getWorkflowRoute());
        taskModel.setCorrelationId(workflowModel.getCorrelationId());
        taskModel.setScheduledTime(System.currentTimeMillis());
        taskModel.setTaskId(taskId);
        taskModel.setWorkflowTask(workflowTask);
        taskModel.setTaskType(workflowTask.getType());
        taskModel.setTaskDefName(workflowTask.getName());
        taskModel.setRetriedTaskId(retryTaskId);
        taskModel.setForkTaskId(forkTaskId);
        return taskModel;
    }
}
