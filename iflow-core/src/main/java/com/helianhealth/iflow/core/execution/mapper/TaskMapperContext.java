package com.helianhealth.iflow.core.execution.mapper;

import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowTask;
import com.helianhealth.iflow.core.execution.DeciderService;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;

import java.util.Map;

/**
 * Decider 与 TaskMapper 之间的上下文
 */
public class TaskMapperContext {

    private final WorkflowModel workflowModel;
    private final WorkflowTask workflowTask;
    private final Map<String, Object> taskInput;
    private final String taskId;

    private final int retryCount;
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

    TaskMapperContext(
            WorkflowModel workflowModel,
            WorkflowTask workflowTask,
            Map<String, Object> taskInput,
            String taskId,
            int retryCount,
            String retryTaskId,
            DeciderService deciderService,
            String forkTaskId) {
        this.workflowModel = workflowModel;
        this.workflowTask = workflowTask;
        this.taskInput = taskInput;
        this.taskId = taskId;
        this.retryCount = retryCount;
        this.retryTaskId = retryTaskId;
        this.deciderService = deciderService;
        this.forkTaskId = forkTaskId;
    }

    public TaskMapperContextBuilder toBuilder() {
        return new TaskMapperContextBuilder()
                .workflowModel(this.workflowModel)
                .workflowTask(this.workflowTask)
                .taskInput(this.taskInput)
                .taskId(this.taskId)
                .retryCount(this.retryCount)
                .retryTaskId(this.retryTaskId)
                .deciderService(this.deciderService)
                .forkTaskId(this.forkTaskId);
    }

    public WorkflowModel getWorkflowModel() {
        return this.workflowModel;
    }

    public WorkflowTask getWorkflowTask() {
        return this.workflowTask;
    }

    public Map<String, Object> getTaskInput() {
        return this.taskInput;
    }

    public String getTaskId() {
        return this.taskId;
    }

    public int getRetryCount() {
        return this.retryCount;
    }

    public String getRetryTaskId() {
        return this.retryTaskId;
    }

    public DeciderService getDeciderService() {
        return this.deciderService;
    }

    public String getForkTaskId() {
        return this.forkTaskId;
    }

    public static class TaskMapperContextBuilder {

        private WorkflowModel workflowModel;
        private WorkflowTask workflowTask;
        private Map<String, Object> taskInput;
        private String taskId;
        private int retryCount = 0;
        private String retryTaskId;
        private DeciderService deciderService;
        private String forkTaskId;

        TaskMapperContextBuilder() {
        }

        public TaskMapperContextBuilder workflowModel(WorkflowModel workflowModel) {
            this.workflowModel = workflowModel;
            return this;
        }

        public TaskMapperContextBuilder workflowTask(WorkflowTask workflowTask) {
            this.workflowTask = workflowTask;
            return this;
        }

        public TaskMapperContextBuilder taskInput(Map<String, Object> taskInput) {
            this.taskInput = taskInput;
            return this;
        }

        public TaskMapperContextBuilder taskId(String taskId) {
            this.taskId = taskId;
            return this;
        }

        public TaskMapperContextBuilder retryCount(int retryCount) {
            this.retryCount = retryCount;
            return this;
        }

        public TaskMapperContextBuilder retryTaskId(String retryTaskId) {
            this.retryTaskId = retryTaskId;
            return this;
        }

        public TaskMapperContextBuilder deciderService(DeciderService deciderService) {
            this.deciderService = deciderService;
            return this;
        }

        public TaskMapperContextBuilder forkTaskId(String forkTaskId) {
            this.forkTaskId = forkTaskId;
            return this;
        }

        public TaskMapperContext build() {
            return new TaskMapperContext(
                    workflowModel,
                    workflowTask,
                    taskInput,
                    taskId,
                    retryCount,
                    retryTaskId,
                    deciderService,
                    forkTaskId);
        }

        @Override
        public String toString() {
            return "TaskMapperContext.TaskMapperContextBuilder(workflowModel=" + workflowModel
                    + ", workflowTask=" + workflowTask
                    + ", taskInput=" + taskInput
                    + ", taskId=" + taskId
                    + ", retryCount=" + retryCount
                    + ", retryTaskId=" + retryTaskId
                    + ", deciderService=" + deciderService
                    + ", forkTaskId=" + forkTaskId + ")";
        }
    }

    public static TaskMapperContextBuilder builder() {
        return new TaskMapperContextBuilder();
    }
}
