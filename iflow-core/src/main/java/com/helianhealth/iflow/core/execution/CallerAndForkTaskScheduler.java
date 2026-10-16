package com.helianhealth.iflow.core.execution;

import com.helianhealth.iflow.core.config.AgentCoreConfiguration;
import com.helianhealth.iflow.core.execution.tasks.SystemTaskRegistry;
import com.helianhealth.iflow.core.execution.tasks.WorkflowSystemTask;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * 同步工作流默认任务调度器：控制节点和线性任务由调用线程推进，FORK 分支业务任务并行执行。
 */
@Component
public class CallerAndForkTaskScheduler implements TaskScheduler {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(CallerAndForkTaskScheduler.class);

    private final Executor forkBranchExecutor;
    private final SystemTaskRegistry systemTaskRegistry;

    public CallerAndForkTaskScheduler(
            @Qualifier(AgentCoreConfiguration.FORK_BRANCH_TASK_EXECUTOR)
                    Executor forkBranchExecutor,
            SystemTaskRegistry systemTaskRegistry) {
        this.forkBranchExecutor = Objects.requireNonNull(forkBranchExecutor);
        this.systemTaskRegistry = Objects.requireNonNull(systemTaskRegistry);
    }

    @Override
    public ScheduleMode getScheduleMode(TaskModel task) {
        return task.isForkBranchTask() ? ScheduleMode.FORK_BRANCH : ScheduleMode.CALLER;
    }

    @Override
    public void schedule(
            WorkflowModel workflow,
            List<TaskModel> tasks,
            WorkflowExecutor workflowExecutor) {
        if (tasks == null || tasks.isEmpty()) {
            return;
        }

        List<TaskModel> parallelBatch = new ArrayList<>();
        Set<String> scheduledTaskIds = new LinkedHashSet<>();
        for (TaskModel task : tasks) {
            if (task == null || !scheduledTaskIds.add(task.getTaskId())) {
                continue;
            }
            if (getScheduleMode(task) == ScheduleMode.FORK_BRANCH) {
                parallelBatch.add(task);
                continue;
            }

            executeParallelBatch(parallelBatch, workflow, workflowExecutor);
            parallelBatch.clear();
            executeTask(workflow, task, workflowExecutor);
        }
        executeParallelBatch(parallelBatch, workflow, workflowExecutor);
    }

    private void executeParallelBatch(
            List<TaskModel> tasks,
            WorkflowModel workflow,
            WorkflowExecutor workflowExecutor) {
        if (tasks.isEmpty()) {
            return;
        }

        List<CompletableFuture<Void>> executions = new ArrayList<>(tasks.size());
        for (TaskModel task : tasks) {
            try {
                executions.add(
                        CompletableFuture.runAsync(
                                () -> executeTask(workflow, task, workflowExecutor),
                                forkBranchExecutor));
            } catch (RejectedExecutionException e) {
                markRejected(task);
            }
        }

        if (executions.isEmpty()) {
            return;
        }

        try {
            CompletableFuture.anyOf(executions.toArray(CompletableFuture[]::new)).join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw e;
        }
    }

    private void executeTask(
            WorkflowModel workflow,
            TaskModel task,
            WorkflowExecutor workflowExecutor) {
        WorkflowSystemTask systemTask = systemTaskRegistry.get(task.getTaskType());
        prepareTaskForExecution(task);
        try {
            systemTask.execute(workflow, task, workflowExecutor);
        } catch (RuntimeException e) {
            failTask(task, "任务执行异常: " + rootCauseMessage(e));
            LOGGER.error(
                    "System task execution failed: workflow={}, task={}, type={}",
                    workflow.getWorkflowId(),
                    task.getTaskId(),
                    task.getTaskType(),
                    e);
        }
        finishTaskExecution(task);
    }

    private void prepareTaskForExecution(TaskModel task) {
        if (task.getStartTime() == 0) {
            task.setStartTime(System.currentTimeMillis());
        }
        if (task.getStatus() == TaskModel.Status.SCHEDULED) {
            task.setStatus(TaskModel.Status.IN_PROGRESS);
        }
    }

    private void finishTaskExecution(TaskModel task) {
        long now = System.currentTimeMillis();
        task.setUpdateTime(now);
        if (task.getStatus() != null
                && task.getStatus().isTerminal()
                && task.getEndTime() == 0) {
            task.setEndTime(now);
        }
    }

    private void failTask(TaskModel task, String reason) {
        task.setStatus(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR);
        task.setReasonForInCompletion(reason);
    }

    private String rootCauseMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() != null
                ? current.getMessage()
                : current.getClass().getName();
    }

    private void markRejected(TaskModel task) {
        long now = System.currentTimeMillis();
        task.setStatus(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR);
        task.setReasonForInCompletion("FORK 分支任务执行线程池已满");
        task.setUpdateTime(now);
        task.setEndTime(now);
    }
}
