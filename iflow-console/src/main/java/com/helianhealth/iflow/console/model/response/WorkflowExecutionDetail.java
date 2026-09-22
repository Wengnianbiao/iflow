package com.helianhealth.iflow.console.model.response;

import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowDef;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import lombok.Builder;
import lombok.Data;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class WorkflowExecutionDetail {

    private String workflowId;
    private String status;
    private String previousStatus;
    private String correlationId;
    private Map<String, Object> input;
    private Object output;
    private WorkflowDef workflowDefinition;
    private List<TaskExecutionView> tasks;
    private long startTime;
    private long endTime;
    private long updateTime;
    private String reasonForIncompletion;
    private String failedTaskId;

    public static WorkflowExecutionDetail from(WorkflowModel workflow) {
        List<TaskExecutionView> taskViews =
                workflow.getTasks() == null
                        ? List.of()
                        : workflow.getTasks().stream()
                                .sorted(Comparator.comparingInt(TaskModel::getSeq))
                                .map(TaskExecutionView::from)
                                .toList();
        return WorkflowExecutionDetail.builder()
                .workflowId(workflow.getWorkflowId())
                .status(workflow.getStatus() == null ? null : workflow.getStatus().name())
                .previousStatus(
                        workflow.getPreviousStatus() == null
                                ? null
                                : workflow.getPreviousStatus().name())
                .correlationId(workflow.getCorrelationId())
                .input(workflow.getInput())
                .output(workflow.getOutput())
                .workflowDefinition(workflow.getWorkflowDefinition())
                .tasks(taskViews)
                .startTime(workflow.getStartTime())
                .endTime(workflow.getEndTime())
                .updateTime(workflow.getUpdateTime())
                .reasonForIncompletion(workflow.getReasonForInCompletion())
                .failedTaskId(workflow.getFailedTaskId())
                .build();
    }
}
