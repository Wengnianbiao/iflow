package com.helianhealth.iflow.core.execution.tasks;

import com.helianhealth.iflow.common.domain.metadata.task.TaskType;
import com.helianhealth.iflow.core.execution.WorkflowExecutor;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import org.springframework.stereotype.Component;

/**
 * 主动终止工作流任务。
 *
 * <p>常用于「条件命中即返回」场景：终止状态写入 {@code terminationStatus}（仅允许 COMPLETED
 * / FAILED / TERMINATED），同时可通过 {@code workflowOutput} 指定工作流输出。
 *
 * <pre>
 * "inputParameters": {
 *   "terminationStatus": "COMPLETED",
 *   "terminationReason": "no more data",
 *   "workflowOutput": "${task0.output}"
 * }
 * </pre>
 */
@Component(TaskType.TASK_TYPE_TERMINATE)
public class Terminate extends WorkflowSystemTask {

    public static final String INPUT_TERMINATION_STATUS = "terminationStatus";
    public static final String INPUT_TERMINATION_REASON = "terminationReason";
    public static final String INPUT_WORKFLOW_OUTPUT = "workflowOutput";

    public Terminate() {
        super(TaskType.TASK_TYPE_TERMINATE);
    }

    @Override
    public void start(WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        String returnStatus = (String) task.getInputData().get(INPUT_TERMINATION_STATUS);
        if (!isValidStatus(returnStatus)) {
            task.setReasonForInCompletion("invalid terminationStatus: " + returnStatus);
            task.setStatus(TaskModel.Status.FAILED);
            return;
        }
        task.setOutputData(task.getInputData().get(INPUT_WORKFLOW_OUTPUT));
        task.setStatus(TaskModel.Status.COMPLETED);
    }

    public static boolean isValidStatus(String status) {
        return WorkflowModel.Status.COMPLETED.name().equals(status)
                || WorkflowModel.Status.FAILED.name().equals(status)
                || WorkflowModel.Status.TERMINATED.name().equals(status);
    }

    public static String getTerminationStatusParameter() {
        return INPUT_TERMINATION_STATUS;
    }

    public static String getTerminationReasonParameter() {
        return INPUT_TERMINATION_REASON;
    }

    public static String getTerminationWorkflowOutputParameter() {
        return INPUT_WORKFLOW_OUTPUT;
    }

}
