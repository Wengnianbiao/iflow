package com.helianhealth.iflow.core.execution;

import com.helianhealth.iflow.core.dal.ExecutionDAOFacade;
import com.helianhealth.iflow.core.exception.TerminateWorkflowException;
import com.helianhealth.iflow.core.execution.tasks.SystemTaskRegistry;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 执行器与 sweeper 共用的失败终态收尾；调用方持有实例锁。 */
final class WorkflowTermination {
    private static final Logger LOGGER = LoggerFactory.getLogger(WorkflowTermination.class);
    private final SystemTaskRegistry registry;
    private final ExecutionDAOFacade dao;

    WorkflowTermination(SystemTaskRegistry registry, ExecutionDAOFacade dao) {
        this.registry = registry;
        this.dao = dao;
    }

    void terminate(WorkflowModel workflow, TerminateWorkflowException termination, WorkflowExecutor executor) {
        workflow.setStatus(termination.getWorkflowStatus());
        workflow.setReasonForInCompletion(termination.getMessage());
        if (termination.getTask() != null) {
            workflow.setFailedTaskId(termination.getTask().getTaskId());
            if (dao != null) {
                dao.updateTask(termination.getTask());
            }
        }
        workflow.setEndTime(System.currentTimeMillis());
        cancelNonTerminalTasks(workflow, termination.getMessage(), executor);
        executor.updateWorkflow(workflow);
    }

    void cancelNonTerminalTasks(WorkflowModel workflow, String reason, WorkflowExecutor executor) {
        long now = System.currentTimeMillis();
        for (TaskModel task : workflow.getTasks()) {
            if (task.getStatus() != null && task.getStatus().isTerminal()) {
                continue;
            }
            boolean timedOut = workflow.getStatus() == WorkflowModel.Status.TIMED_OUT
                    && task.getStatus() == TaskModel.Status.IN_PROGRESS;
            if (registry != null && registry.isSystemTask(task.getTaskType())) {
                try {
                    registry.get(task.getTaskType()).cancel(workflow, task, executor);
                } catch (RuntimeException error) {
                    LOGGER.warn("Error cancelling system task {} for workflow {}",
                            task.getTaskId(), workflow.getWorkflowId(), error);
                }
            }
            task.setStatus(timedOut ? TaskModel.Status.TIMED_OUT : TaskModel.Status.CANCELED);
            task.setExecuted(true);
            task.setReasonForInCompletion(reason);
            task.setEndTime(now);
            task.setUpdateTime(now);
            if (timedOut && workflow.getFailedTaskId() == null) {
                workflow.setFailedTaskId(task.getTaskId());
            }
            if (dao != null) {
                dao.updateTask(task);
            }
        }
    }
}
