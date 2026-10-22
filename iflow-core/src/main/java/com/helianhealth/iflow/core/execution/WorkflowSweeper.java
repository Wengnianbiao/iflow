package com.helianhealth.iflow.core.execution;

import com.helianhealth.iflow.common.domain.metadata.task.TaskType;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowDef;
import com.helianhealth.iflow.core.dal.ExecutionDAOFacade;
import com.helianhealth.iflow.core.execution.tasks.SystemTaskRegistry;
import com.helianhealth.iflow.core.lock.ExecutionLockService;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 运行中工作流的超时 sweep。
 *
 * <p>Conductor 的 sweeper 会重新 decide，让工作流继续往下调度。这里只处理已经到期的当前运行实例：
 * 工作流记为 {@link WorkflowModel.Status#TIMED_OUT}，进行中的任务记为
 * {@link TaskModel.Status#TIMED_OUT}，其余未完成任务记为 {@link TaskModel.Status#CANCELED}。
 * 不创建后续任务，也不再进入 Decider。
 */
@Component
public class WorkflowSweeper {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkflowSweeper.class);

    private final SystemTaskRegistry systemTaskRegistry;
    private final ExecutionDAOFacade executionDAOFacade;
    private final ExecutionLockService executionLockService;
    private final long defaultTimeoutSeconds;

    @Autowired
    public WorkflowSweeper(
            SystemTaskRegistry systemTaskRegistry,
            @Autowired(required = false) ExecutionDAOFacade executionDAOFacade,
            @Autowired(required = false) ExecutionLockService executionLockService,
            @Value("${iflow.workflow-execution.default-timeout-seconds:3600}")
                    long defaultTimeoutSeconds) {
        this.systemTaskRegistry = systemTaskRegistry;
        this.executionDAOFacade = executionDAOFacade;
        this.executionLockService = executionLockService;
        this.defaultTimeoutSeconds = Math.max(defaultTimeoutSeconds, 0);
    }

    /**
     * 扫描全部 RUNNING 实例。拿不到执行锁的实例留给持锁的调用线程处理。
     */
    public void sweepRunningWorkflows(WorkflowExecutor workflowExecutor) {
        if (executionDAOFacade == null) {
            return;
        }
        List<String> workflowIds = executionDAOFacade.getRunningWorkflowIds();
        for (String workflowId : workflowIds) {
            try {
                sweepStored(workflowId, workflowExecutor);
            } catch (RuntimeException e) {
                LOGGER.error("Failed to sweep workflow {}", workflowId, e);
            }
        }
    }

    /**
     * 从持久化状态 sweep 一个实例。执行锁被占用时直接跳过，避免和正在跑的调用线程抢写。
     *
     * @return 本次是否把工作流终止
     */
    public boolean sweepStored(String workflowId, WorkflowExecutor workflowExecutor) {
        if (executionDAOFacade == null || workflowId == null || workflowId.isBlank()) {
            return false;
        }
        boolean locked = false;
        if (executionLockService != null) {
            locked = executionLockService.acquireLock(workflowId);
            if (!locked) {
                LOGGER.debug(
                        "Skip sweep for workflow {} because execution is in progress", workflowId);
                return false;
            }
        }
        try {
            WorkflowModel workflow = executionDAOFacade.getWorkflowModel(workflowId);
            if (workflow == null) {
                return false;
            }
            return sweep(workflow, workflowExecutor);
        } finally {
            if (locked) {
                executionLockService.releaseLock(workflowId);
            }
        }
    }

    /**
     * 检查当前运行实例是否已经超过执行期限。未到期时不改状态。
     *
     * @return 本次是否把工作流终止
     */
    public boolean sweep(WorkflowModel workflow, WorkflowExecutor workflowExecutor) {
        if (workflow == null
                || workflow.getStatus() == null
                || workflow.getStatus().isTerminal()) {
            return false;
        }
        long timeoutSeconds = resolveTimeoutSeconds(workflow);
        if (timeoutSeconds <= 0) {
            return false;
        }
        long origin =
                workflow.getLastRetriedTime() > 0
                        ? workflow.getLastRetriedTime()
                        : workflow.getStartTime();
        if (origin <= 0) {
            return false;
        }
        long now = System.currentTimeMillis();
        long elapsedMillis = now - origin;
        if (elapsedMillis < timeoutSeconds * 1000L) {
            return false;
        }
        String reason = timeoutReason(workflow, elapsedMillis / 1000L, timeoutSeconds);
        terminate(workflow, workflowExecutor, reason, now);
        LOGGER.info(
                "Swept workflow {} after {} seconds: {}",
                workflow.getWorkflowId(),
                elapsedMillis / 1000L,
                reason);
        return true;
    }

    private long resolveTimeoutSeconds(WorkflowModel workflow) {
        WorkflowDef definition = workflow.getWorkflowDefinition();
        if (definition != null && definition.getTimeoutSeconds() > 0) {
            return definition.getTimeoutSeconds();
        }
        return defaultTimeoutSeconds;
    }

    private void terminate(
            WorkflowModel workflow,
            WorkflowExecutor workflowExecutor,
            String reason,
            long now) {
        String failedTaskId = null;
        for (TaskModel task : workflow.getTasks()) {
            if (task.getStatus() != null && task.getStatus().isTerminal()) {
                continue;
            }
            boolean inProgress = task.getStatus() == TaskModel.Status.IN_PROGRESS;
            if (workflowExecutor != null
                    && systemTaskRegistry.isSystemTask(task.getTaskType())) {
                try {
                    systemTaskRegistry
                            .get(task.getTaskType())
                            .cancel(workflow, task, workflowExecutor);
                } catch (RuntimeException e) {
                    LOGGER.warn(
                            "Error cancelling system task {} for workflow {}",
                            task.getTaskId(),
                            workflow.getWorkflowId(),
                            e);
                }
            }
            task.setStatus(
                    inProgress ? TaskModel.Status.TIMED_OUT : TaskModel.Status.CANCELED);
            task.setExecuted(true);
            task.setReasonForInCompletion(reason);
            task.setEndTime(now);
            task.setUpdateTime(now);
            if (inProgress && failedTaskId == null) {
                failedTaskId = task.getTaskId();
            }
            if (executionDAOFacade != null) {
                executionDAOFacade.updateTask(task);
            }
        }
        if (failedTaskId != null) {
            workflow.setFailedTaskId(failedTaskId);
        }
        workflow.setStatus(WorkflowModel.Status.TIMED_OUT);
        workflow.setReasonForInCompletion(reason);
        workflow.setEndTime(now);
        if (workflowExecutor != null) {
            workflowExecutor.updateWorkflow(workflow);
        } else if (executionDAOFacade != null) {
            executionDAOFacade.updateWorkflow(workflow);
        }
    }

    private String timeoutReason(
            WorkflowModel workflow, long elapsedSeconds, long timeoutSeconds) {
        List<TaskModel> openTasks =
                workflow.getTasks().stream()
                        .filter(task -> task.getStatus() == null || !task.getStatus().isTerminal())
                        .toList();
        TaskModel joinTask =
                openTasks.stream().filter(task -> isJoin(task.getTaskType())).findFirst().orElse(null);
        String pending =
                openTasks.stream()
                        .filter(task -> joinTask == null || task != joinTask)
                        .map(
                                task ->
                                        task.getReferenceTaskName()
                                                + "("
                                                + (task.getStatus() == null
                                                        ? "UNKNOWN"
                                                        : task.getStatus().name())
                                                + ")")
                        .collect(Collectors.joining(", "));
        if (joinTask != null) {
            String joinRef =
                    joinTask.getReferenceTaskName() == null
                            ? joinTask.getTaskId()
                            : joinTask.getReferenceTaskName();
            if (pending.isEmpty()) {
                return String.format("工作流执行超过 %d 秒，等待 JOIN %s", elapsedSeconds, joinRef);
            }
            return String.format(
                    "工作流执行超过 %d 秒，等待 JOIN %s；未完成任务：%s",
                    elapsedSeconds, joinRef, pending);
        }
        if (pending.isEmpty()) {
            return String.format(
                    "工作流执行超过 %d 秒，超时配置为 %d 秒", elapsedSeconds, timeoutSeconds);
        }
        return String.format(
                "工作流执行超过 %d 秒，超时配置为 %d 秒；未完成任务：%s",
                elapsedSeconds, timeoutSeconds, pending);
    }

    private boolean isJoin(String taskType) {
        return TaskType.TASK_TYPE_JOIN.equals(taskType)
                || TaskType.TASK_TYPE_EXCLUSIVE_JOIN.equals(taskType);
    }
}
