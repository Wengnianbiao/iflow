package com.helianhealth.agent.console.model.response;

import com.helianhealth.agent.core.model.TaskModel;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class TaskExecutionView {

    private String taskId;
    private String workflowInstanceId;
    private String referenceTaskName;
    private int retryCount;
    private int seq;
    private String status;
    private String taskType;
    private String taskDefName;
    private Map<String, Object> inputData;
    private Map<String, Object> outputData;
    private String reasonForIncompletion;
    private long scheduledTime;
    private long startTime;
    private long endTime;
    private long updateTime;
    private String workerId;
    private String parentTaskId;
    private String keyword;

    public static TaskExecutionView from(TaskModel task) {
        return TaskExecutionView.builder()
                .taskId(task.getTaskId())
                .workflowInstanceId(task.getWorkflowInstanceId())
                .referenceTaskName(task.getReferenceTaskName())
                .retryCount(task.getRetryCount())
                .seq(task.getSeq())
                .status(task.getStatus() == null ? null : task.getStatus().name())
                .taskType(task.getTaskType())
                .taskDefName(task.getTaskDefName())
                .inputData(task.getInputData())
                .outputData(task.getOutputData())
                .reasonForIncompletion(task.getReasonForInCompletion())
                .scheduledTime(task.getScheduledTime())
                .startTime(task.getStartTime())
                .endTime(task.getEndTime())
                .updateTime(task.getUpdateTime())
                .parentTaskId(task.getParentTaskId())
                .build();
    }
}
