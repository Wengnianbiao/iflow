package com.helianhealth.iflow.core.execution;

import com.helianhealth.iflow.common.domain.metadata.task.TaskType;
import com.helianhealth.iflow.core.config.AgentCoreConfiguration;
import com.helianhealth.iflow.core.execution.tasks.SystemTaskRegistry;
import com.helianhealth.iflow.core.execution.tasks.WorkflowSystemTask;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowExecutionMapper;
import com.helianhealth.iflow.core.model.WorkflowModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/** 无实例状态的调度器：主链路和控制节点由 caller 执行，FORK 业务 start 交给分支池。 */
@Component
public class CallerAndForkTaskScheduler implements TaskScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(CallerAndForkTaskScheduler.class);
    private final Executor forkBranchExecutor;
    private final SystemTaskRegistry systemTaskRegistry;

    public CallerAndForkTaskScheduler(
            @Qualifier(AgentCoreConfiguration.FORK_BRANCH_TASK_EXECUTOR) Executor forkBranchExecutor,
            SystemTaskRegistry systemTaskRegistry) {
        this.forkBranchExecutor = Objects.requireNonNull(forkBranchExecutor);
        this.systemTaskRegistry = Objects.requireNonNull(systemTaskRegistry);
    }

    private boolean isControlTask(TaskModel task) {
        return TaskType.isBuiltIn(task.getTaskType())
                || TaskType.TASK_TYPE_TERMINATE.equals(task.getTaskType());
    }

    @Override
    public ScheduleMode getScheduleMode(TaskModel task) {
        return task.isForkBranchTask() && !isControlTask(task)
                ? ScheduleMode.FORK_BRANCH : ScheduleMode.CALLER;
    }

    @Override
    public boolean schedule(WorkflowModel workflow, List<TaskModel> tasks,
                            WorkflowExecutor executor, WorkflowDriveContext context) {
        context.checkOwner();
        boolean stateChanged = false;
        for (TaskModel task : tasks) {
            if (context.isClosed() || workflow.getStatus().isTerminal()) {
                break;
            }
            if (task.getStatus() != TaskModel.Status.SCHEDULED) {
                continue;
            }
            if (getScheduleMode(task) == ScheduleMode.FORK_BRANCH) {
                submitBranch(workflow, task, executor, context);
            } else {
                context.markStarted(task, System.currentTimeMillis());
                invokeStart(workflow, task, executor);
                context.markDirty(task);
                stateChanged = true;
            }
        }
        return stateChanged;
    }

    @Override
    public boolean evaluate(WorkflowModel workflow, List<TaskModel> tasks,
                            WorkflowExecutor executor, WorkflowDriveContext context) {
        context.checkOwner();
        boolean stateChanged = false;
        for (TaskModel task : tasks) {
            if (workflow.getStatus().isTerminal()) {
                break;
            }
            if (!isControlTask(task) || task.getStatus() != TaskModel.Status.IN_PROGRESS
                    || task.isExecuted() || task.isRetried()) {
                continue;
            }
            try {
                WorkflowSystemTask systemTask = systemTaskRegistry.get(task.getTaskType());
                if (systemTask.execute(workflow, task, executor)) {
                    stateChanged = true;
                }
            } catch (RuntimeException error) {
                failTask(task, "任务评估异常: " + rootCauseMessage(error));
                stateChanged = true;
            }
            finishTask(task);
            context.markDirty(task);
        }
        return stateChanged;
    }

    private void submitBranch(WorkflowModel workflow, TaskModel target,
                              WorkflowExecutor executor, WorkflowDriveContext context) {
        if (!context.submitBranch(target)) {
            return;
        }
        TaskModel isolated = copyForExecution(target);
        try {
            forkBranchExecutor.execute(() -> {
                try {
                    if (context.isClosed() || workflow.getStatus().isTerminal()
                            || target.getStatus().isTerminal()) {
                        isolated.setStatus(TaskModel.Status.CANCELED);
                        return;
                    }
                    long now = System.currentTimeMillis();
                    isolated.setStartTime(now);
                    isolated.setStatus(TaskModel.Status.IN_PROGRESS);
                    context.publishStarted(target, now);
                    invokeStart(workflow, isolated, executor);
                } catch (Throwable error) {
                    failTask(isolated, "任务执行异常: " + rootCauseMessage(error));
                    finishTask(isolated);
                } finally {
                    context.publishCompleted(target, isolated);
                }
            });
        } catch (RuntimeException error) {
            failTask(isolated, error instanceof RejectedExecutionException
                    ? "FORK 分支任务执行线程池已满" : "分支任务提交异常: " + rootCauseMessage(error));
            finishTask(isolated);
            context.publishCompleted(target, isolated);
        }
    }

    private void invokeStart(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
        try {
            WorkflowSystemTask systemTask = systemTaskRegistry.get(task.getTaskType());
            systemTask.start(workflow, task, executor);
            if (!isControlTask(task) && !task.getStatus().isTerminal()) {
                failTask(task, "业务任务 start() 返回时必须进入终态");
            }
        } catch (RuntimeException error) {
            failTask(task, "任务执行异常: " + rootCauseMessage(error));
            LOGGER.error("Task start failed: workflow={}, task={}", workflow.getWorkflowId(), task.getTaskId(), error);
        }
        finishTask(task);
    }

    @SuppressWarnings("unchecked")
    private TaskModel copyForExecution(TaskModel task) {
        TaskModel copy = TaskModel.fromTask(task.toTask());
        copy.setInputData((Map<String, Object>) WorkflowExecutionMapper.copyOutput(task.getInputData()));
        return copy;
    }

    private void failTask(TaskModel task, String reason) {
        task.setReasonForInCompletion(reason);
        task.setStatus(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR);
    }

    private void finishTask(TaskModel task) {
        long now = System.currentTimeMillis();
        task.setUpdateTime(now);
        if (task.getStatus().isTerminal() && task.getEndTime() == 0) {
            task.setEndTime(now);
        }
    }

    private String rootCauseMessage(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getMessage() != null ? root.getMessage() : root.getClass().getSimpleName();
    }
}
