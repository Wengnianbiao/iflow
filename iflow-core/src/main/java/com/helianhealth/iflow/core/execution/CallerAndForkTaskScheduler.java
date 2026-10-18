package com.helianhealth.iflow.core.execution;

import com.helianhealth.iflow.common.domain.metadata.task.TaskType;
import com.helianhealth.iflow.core.config.AgentCoreConfiguration;
import com.helianhealth.iflow.core.execution.tasks.SystemTaskRegistry;
import com.helianhealth.iflow.core.execution.tasks.WorkflowSystemTask;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
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
    // 同一工作流运行时跨 Decider 轮次复用；完成信号由调用线程消费，避免错过快分支。
    private final ConcurrentMap<WorkflowExecutionKey, WorkflowExecutions> workflowExecutions =
            new ConcurrentHashMap<>();

    public CallerAndForkTaskScheduler(
            @Qualifier(AgentCoreConfiguration.FORK_BRANCH_TASK_EXECUTOR)
                    Executor forkBranchExecutor,
            SystemTaskRegistry systemTaskRegistry) {
        this.forkBranchExecutor = Objects.requireNonNull(forkBranchExecutor);
        this.systemTaskRegistry = Objects.requireNonNull(systemTaskRegistry);
    }

    @Override
    public ScheduleMode getScheduleMode(TaskModel task) {
        return task.isForkBranchTask()
                        && !TaskType.isBuiltIn(task.getTaskType())
                        && !TaskType.TASK_TYPE_TERMINATE.equals(task.getTaskType())
                ? ScheduleMode.FORK_BRANCH
                : ScheduleMode.CALLER;
    }

    @Override
    public void schedule(
            WorkflowModel workflow,
            List<TaskModel> tasks,
            WorkflowExecutor workflowExecutor) {
        WorkflowExecutions executions = workflowExecutions.computeIfAbsent(
                WorkflowExecutionKey.of(workflow), ignored -> new WorkflowExecutions());
        executions.released = false;
        boolean completedExecution = consumeCompleted(executions);
        boolean callerProgress = false;
        Set<String> scheduledTaskIds = new LinkedHashSet<>();
        for (TaskModel task : tasks == null ? List.<TaskModel>of() : tasks) {
            if (task == null
                    || task.isExecuted()
                    || task.isRetried()
                    || (task.getStatus() != null && task.getStatus().isTerminal())
                    || !scheduledTaskIds.add(task.getTaskId())) {
                continue;
            }
            if (getScheduleMode(task) == ScheduleMode.FORK_BRANCH) {
                submitBranch(workflow, task, workflowExecutor, executions);
                continue;
            }

            TaskModel.Status previousStatus = task.getStatus();
            int previousIteration = task.getIteration();
            int previousTaskCount = workflow.getTasks().size();
            executeTask(workflow, task, workflowExecutor);
            // 更新时间或重复评估等待中的 JOIN 不算推进；循环注册新一轮则需要立即再 decide。
            callerProgress |= previousStatus != task.getStatus()
                    || previousIteration != task.getIteration()
                    || previousTaskCount != workflow.getTasks().size();
        }

        // 处理完整轮候选后再等待，不能让旧的慢分支挡住快分支后继的提交。
        if (!callerProgress && !completedExecution && !executions.tasks.isEmpty()) {
            await(CompletableFuture.anyOf(
                    executions.tasks.values().toArray(CompletableFuture[]::new)));
        }
        consumeCompleted(executions);
    }

    private void submitBranch(
            WorkflowModel workflow,
            TaskModel task,
            WorkflowExecutor workflowExecutor,
            WorkflowExecutions executions) {
        CompletableFuture<Void> execution = new CompletableFuture<>();
        // 在线程池接收之前占位，排队中和执行中的任务都不能被重复提交。
        if (executions.tasks.putIfAbsent(task.getTaskId(), execution) != null) {
            return;
        }
        execution.whenComplete((ignored, error) -> {
            if (executions.released) {
                executions.tasks.remove(task.getTaskId(), execution);
                removeReleasedExecutions(workflow, executions);
            }
        });
        try {
            forkBranchExecutor.execute(() -> {
                try {
                    if (!workflow.getStatus().isTerminal()
                            && (task.getStatus() == null || !task.getStatus().isTerminal())) {
                        executeTask(workflow, task, workflowExecutor);
                    }
                    execution.complete(null);
                } catch (Throwable error) {
                    execution.completeExceptionally(error);
                }
            });
        } catch (RejectedExecutionException e) {
            markRejected(task);
            execution.complete(null);
        } catch (RuntimeException e) {
            execution.completeExceptionally(e);
            throw e;
        }
    }

    private boolean consumeCompleted(WorkflowExecutions executions) {
        boolean completed = false;
        for (var entry : executions.tasks.entrySet()) {
            CompletableFuture<Void> execution = entry.getValue();
            if (execution.isDone()) {
                try {
                    await(execution);
                } finally {
                    executions.tasks.remove(entry.getKey(), execution);
                }
                completed = true;
            }
        }
        return completed;
    }

    private void await(CompletableFuture<?> execution) {
        try {
            execution.join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw e;
        }
    }

    @Override
    public void release(WorkflowModel workflow) {
        WorkflowExecutions executions = workflowExecutions.get(WorkflowExecutionKey.of(workflow));
        if (executions == null) {
            return;
        }
        executions.released = true;
        executions.tasks.entrySet().removeIf(entry -> entry.getValue().isDone());
        removeReleasedExecutions(workflow, executions);
    }

    private void removeReleasedExecutions(WorkflowModel workflow, WorkflowExecutions executions) {
        if (executions.released && executions.tasks.isEmpty()) {
            workflowExecutions.remove(WorkflowExecutionKey.of(workflow), executions);
        }
    }

    private static final class WorkflowExecutions {
        private final ConcurrentMap<String, CompletableFuture<Void>> tasks =
                new ConcurrentHashMap<>();
        private volatile boolean released;
    }

    private record WorkflowExecutionKey(String workflowId, WorkflowModel transientWorkflow) {
        private static WorkflowExecutionKey of(WorkflowModel workflow) {
            // 尚未分配 ID 的内存运行时按对象隔离；生产实例按稳定的 workflowId 复用。
            String workflowId = workflow.getWorkflowId();
            return new WorkflowExecutionKey(workflowId, workflowId == null ? workflow : null);
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
