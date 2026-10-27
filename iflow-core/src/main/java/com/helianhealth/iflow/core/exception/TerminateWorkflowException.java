package com.helianhealth.iflow.core.exception;

import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;

/**
 * 工作流应被终止的异常。
 *
 * <p>由 TaskMapper / Decider 在定义不合法、必选任务失败或无法继续调度时抛出。
 */
public class TerminateWorkflowException extends RuntimeException {

    private final WorkflowModel.Status workflowStatus;
    private final TaskModel task;

    public TerminateWorkflowException(String message) {
        this(message, WorkflowModel.Status.FAILED, null);
    }

    public TerminateWorkflowException(String message, Throwable cause) {
        super(message, cause);
        this.workflowStatus = WorkflowModel.Status.FAILED;
        this.task = null;
    }

    public TerminateWorkflowException(String message, WorkflowModel.Status workflowStatus) {
        this(message, workflowStatus, null);
    }

    public TerminateWorkflowException(
            String message, WorkflowModel.Status workflowStatus, TaskModel task) {
        super(message);
        this.workflowStatus = workflowStatus != null ? workflowStatus : WorkflowModel.Status.FAILED;
        this.task = task;
    }

    public WorkflowModel.Status getWorkflowStatus() {
        return this.workflowStatus;
    }

    public TaskModel getTask() {
        return this.task;
    }
}
