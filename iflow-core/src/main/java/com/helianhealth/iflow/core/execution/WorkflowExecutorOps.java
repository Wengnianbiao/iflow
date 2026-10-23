package com.helianhealth.iflow.core.execution;

import com.helianhealth.iflow.common.domain.metadata.task.TaskType;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowDef;
import com.helianhealth.iflow.common.exception.WorkFlowErrorCode;
import com.helianhealth.iflow.common.exception.WorkFlowException;
import com.helianhealth.iflow.core.dal.ExecutionDAOFacade;
import com.helianhealth.iflow.core.exception.TerminateWorkflowException;
import com.helianhealth.iflow.core.exception.TransientException;
import com.helianhealth.iflow.core.execution.tasks.SystemTaskRegistry;
import com.helianhealth.iflow.core.execution.tasks.Terminate;
import com.helianhealth.iflow.core.execution.tasks.WorkflowSystemTask;
import com.helianhealth.iflow.core.lock.ExecutionLockService;
import com.helianhealth.iflow.core.listener.WorkflowStatusListener;
import com.helianhealth.iflow.core.listener.WorkflowStatusListener.WorkflowEventType;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowExecutionMapper;
import com.helianhealth.iflow.core.model.WorkflowModel;
import com.helianhealth.iflow.core.queue.ExecutionQueue;
import com.helianhealth.iflow.core.queue.QueueUtils;
import com.helianhealth.iflow.core.service.MetadataService;
import com.helianhealth.iflow.core.utils.IDGenerator;
import com.helianhealth.iflow.core.utils.ParametersUtils;
import com.helianhealth.iflow.core.utils.TaskUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class WorkflowExecutorOps implements WorkflowExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkflowExecutorOps.class);

    private final DeciderService deciderService;
    private final SystemTaskRegistry systemTaskRegistry;
    private final ExecutionDAOFacade executionDAOFacade;
    private final MetadataService metadataService;
    private final IDGenerator idGenerator;
    private final ParametersUtils parametersUtils;
    private final ExecutionLockService executionLockService;
    private final WorkflowCompletionRegistry completionRegistry;
    private final List<WorkflowStatusListener> workflowStatusListeners;
    private final ExecutionQueue executionQueue;

    public WorkflowExecutorOps(
            DeciderService deciderService,
            SystemTaskRegistry systemTaskRegistry,
            ExecutionDAOFacade executionDAOFacade,
            MetadataService metadataService,
            IDGenerator idGenerator,
            ParametersUtils parametersUtils,
            ExecutionLockService executionLockService,
            ExecutionQueue executionQueue,
            WorkflowCompletionRegistry completionRegistry,
            List<WorkflowStatusListener> workflowStatusListeners) {
        this.deciderService = deciderService;
        this.systemTaskRegistry = systemTaskRegistry;
        this.executionDAOFacade = executionDAOFacade;
        this.metadataService = metadataService;
        this.idGenerator = idGenerator;
        this.parametersUtils = parametersUtils;
        this.executionLockService = executionLockService;
        this.executionQueue = executionQueue;
        this.completionRegistry = completionRegistry;
        this.workflowStatusListeners = List.copyOf(workflowStatusListeners);
    }

    @Override
    public String startWorkflow(StartWorkflowInput input) {
        WorkflowDef workflowDefinition = resolveWorkflowDefinition(input);
        String workflowId = Optional.ofNullable(input.getWorkflowId()).orElseGet(idGenerator::generate);
        WorkflowModel workflow = createWorkflowModel(input, workflowDefinition, workflowId);
        try {
            createAndEvaluate(workflow);
            return workflowId;
        } catch (RuntimeException error) {
            LOGGER.error("Unable to start workflow: {}", input.getRoute(), error);
            try {
                executionDAOFacade.removeWorkflow(workflowId);
            } catch (RuntimeException cleanupError) {
                LOGGER.error("Could not remove workflow: {}", workflowId, cleanupError);
            }
            throw error;
        }
    }

    private void createAndEvaluate(WorkflowModel workflow) {
        if (!executionLockService.acquireLock(workflow.getWorkflowId())) {
            throw new TransientException("Error acquiring lock when creating workflow: {}");
        }
        try {
            createAndEvaluateWithLock(workflow);
        } finally {
            executionLockService.releaseLock(workflow.getWorkflowId());
        }
    }

    private void createAndEvaluateWithLock(WorkflowModel workflow) {
        executionDAOFacade.createWorkflow(workflow);
        notifyWorkflowStatusListeners(workflow, WorkflowEventType.STARTED);
        LOGGER.debug(
                "A new instance of workflow: {} created with id: {}",
                workflow.getWorkflowName(),
                workflow.getWorkflowId());
        decide(workflow);
    }

    @Override
    public WorkflowModel startWorkflowSync(StartWorkflowInput input) {
        WorkflowDef workflowDefinition = resolveWorkflowDefinition(input);
        String workflowId = Optional.ofNullable(input.getWorkflowId()).orElseGet(idGenerator::generate);
        WorkflowModel workflow = createWorkflowModel(input, workflowDefinition, workflowId);
        // 同步入口自行创建和推进实例，首次决策前登记通知，释放实例锁后再等待。
        try (WorkflowCompletionRegistry.Registration completion =
                completionRegistry.register(workflowId)) {
            lockWorkflow(workflowId);
            try {
                executionDAOFacade.createWorkflow(workflow);
                notifyWorkflowStatusListeners(workflow, WorkflowEventType.STARTED);
                try {
                    decide(workflow);
                } catch (RuntimeException error) {
                    persistTerminatedWorkflow(workflow,
                            new TerminateWorkflowException("工作流决策异常: " + error.getMessage(), error));
                }
            } finally {
                executionLockService.releaseLock(workflowId);
            }
            if (!workflow.getStatus().isTerminal()) {
                completion.await();
                workflow = executionDAOFacade.getWorkflowModel(workflowId);
                if (workflow == null || !workflow.getStatus().isTerminal()) {
                    throw new IllegalStateException("工作流终态不存在: " + workflowId);
                }
            }
            TaskModel failedTask = findTask(workflow, workflow.getFailedTaskId());
            if (workflow.getStatus() == WorkflowModel.Status.FAILED
                    && (failedTask == null
                            || !TaskType.TASK_TYPE_TERMINATE.equals(failedTask.getTaskType()))) {
                throw new TerminateWorkflowException(workflow.getReasonForInCompletion(),
                        workflow.getStatus(), failedTask);
            }
            return workflow;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            // 停止等待不改变工作流状态，取消执行需要显式调用 terminateWorkflow。
            throw new IllegalStateException("同步等待工作流完成时被中断: " + workflowId, error);
        }
    }

    private WorkflowDef resolveWorkflowDefinition(StartWorkflowInput input) {
        if (!StringUtils.hasText(input.getRoute())) {
            throw new WorkFlowException(
                    WorkFlowErrorCode.REPOSITORY_SAVE_ERROR, "工作流 route 不能为空");
        }
        return metadataService.getWorkflowDef(input.getRoute(), input.getVersion());
    }

    /** 决策由触发它的线程直接执行，持锁后重新加载，避免丢失并发完成通知。 */
    private void decideWithLock(String workflowId) {
        lockWorkflow(workflowId);
        WorkflowModel workflow = null;
        try {
            workflow = executionDAOFacade.getWorkflowModel(workflowId);
            if (workflow != null) {
                decide(workflow);
            }
        } catch (RuntimeException error) {
            LOGGER.error("Error deciding workflow: {}", workflowId, error);
            if (workflow != null) {
                persistTerminatedWorkflow(workflow,
                        new TerminateWorkflowException("工作流决策异常: " + error.getMessage(), error));
            } else {
                throw error;
            }
        } finally {
            executionLockService.releaseLock(workflowId);
        }
    }

    private WorkflowModel decide(WorkflowModel workflow) {
        if (workflow.getStatus().isTerminal()) {
            if (!workflow.getStatus().isSuccessful()) {
                cancelNonTerminalTasks(workflow, workflow.getReasonForInCompletion());
            }
            return workflow;
        }
        try {
            boolean continueLoop = true;
            while (continueLoop) {
                continueLoop = false;
                DeciderService.DeciderOutcome outcome = deciderService.decide(workflow);

                if (outcome.isComplete) {
                    executionDAOFacade.updateTasks(outcome.tasksToBeUpdated);
                    endExecution(workflow, outcome.terminateTask);
                    return workflow;
                }

                List<TaskModel> tasksToBeScheduled = outcome.tasksToBeScheduled;
                List<TaskModel> tasksToBeUpdated = outcome.tasksToBeUpdated;
                tasksToBeScheduled = dedupAndAddTasks(workflow, tasksToBeScheduled);
                boolean stateChanged = scheduleTask(workflow, tasksToBeScheduled);

                for (TaskModel task : outcome.tasksToBeScheduled) {
                    if (systemTaskRegistry.isSystemTask(task.getTaskType())
                            && !task.getStatus().isTerminal()) {
                        WorkflowSystemTask workflowSystemTask =
                                systemTaskRegistry.get(task.getTaskType());
                        if (!workflowSystemTask.isAsync()
                                && workflowSystemTask.execute(workflow, task, this)) {
                            tasksToBeUpdated.add(task);
                            stateChanged = true;
                        }
                    }
                }

                if (!outcome.tasksToBeUpdated.isEmpty() || !tasksToBeScheduled.isEmpty()) {
                    executionDAOFacade.updateTasks(tasksToBeUpdated);
                }

                if (stateChanged) {
                    continueLoop = true;
                    continue;
                }

                if (!outcome.tasksToBeUpdated.isEmpty() || !tasksToBeScheduled.isEmpty()) {
                    updateWorkflow(workflow);
                }
            }
            return workflow;
        } catch (TerminateWorkflowException error) {
            LOGGER.info("Execution terminated of workflow: {}", workflow.getWorkflowId());
            if (workflow.getStatus() == null || !workflow.getStatus().isTerminal()) {
                persistTerminatedWorkflow(workflow, error);
            }
            return workflow;
        } catch (RuntimeException e) {
            LOGGER.error("Error deciding workflow: {}", workflow.getWorkflowId(), e);
            throw e;
        }
    }

    /** 持久化并调度已去重的新任务；同步任务立即 start，异步任务入队。 */
    private boolean scheduleTask(WorkflowModel workflow, List<TaskModel> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return false;
        }
        List<TaskModel> tasksToBeQueued;
        boolean startedSystemTasks = false;

        try {
            // Get the highest seq number
            int count = workflow.getTasks().stream().mapToInt(TaskModel::getSeq).max().orElse(0);

            for (TaskModel task : tasks) {
                if (task.getSeq() == 0) { // Set only if the seq was not set
                    task.setSeq(++count);
                }
                // Stamp the very first time this task enters the queue. Retried tasks carry
                // this value forward (via TaskModel.copy()), so it is never overwritten here.
                if (task.getFirstScheduledTime() == 0) {
                    task.setFirstScheduledTime(System.currentTimeMillis());
                }
            }

            executionDAOFacade.createTasks(tasks);

            List<TaskModel> systemTasks =
                    tasks.stream()
                            .filter(task -> systemTaskRegistry.isSystemTask(task.getTaskType()))
                            .toList();

            tasksToBeQueued =
                    tasks.stream()
                            .filter(task -> !systemTaskRegistry.isSystemTask(task.getTaskType()))
                            .collect(Collectors.toList());

            // Traverse through all the system tasks, start the sync tasks, in case of async
            // queue
            // the tasks
            for (TaskModel task : systemTasks) {
                WorkflowSystemTask workflowSystemTask = systemTaskRegistry.get(task.getTaskType());
                if (workflowSystemTask == null) {
                    throw new Exception();
                }
                if (!workflowSystemTask.isAsync()) {
                    if (task.getStatus() != null
                            && !task.getStatus().isTerminal()
                            && task.getStartTime() == 0) {
                        task.setStartTime(System.currentTimeMillis());
                    }
                    try {
                        workflowSystemTask.start(workflow, task, this);
                    } catch (Exception e) {
                        String errorMsg =
                                String.format(
                                        "Unable to start system task: %s, {id: %s, name: %s}",
                                        task.getTaskType(),
                                        task.getTaskId(),
                                        task.getTaskDefName());
                        LOGGER.error(errorMsg, e);
                        throw e;
                    }
                    if (!isControlTask(task)
                            && (task.getStatus() == null || !task.getStatus().isTerminal())) {
                        failTask(task, "业务任务 start() 返回时必须进入终态");
                        executionDAOFacade.updateTask(task);
                        throw new TerminateWorkflowException(
                                "业务任务 start() 返回时必须进入终态",
                                WorkflowModel.Status.FAILED,
                                task);
                    }
                    startedSystemTasks = true;
                    executionDAOFacade.updateTask(task);
                } else {
                    tasksToBeQueued.add(task);
                }
            }

        } catch (TerminateWorkflowException error) {
            throw error;
        } catch (Exception e) {
            List<String> taskIds =
                    tasks.stream().map(TaskModel::getTaskId).collect(Collectors.toList());
            String errorMsg =
                    String.format(
                            "Error scheduling tasks: %s, for workflow: %s",
                            taskIds, workflow.getWorkflowId());
            LOGGER.error(errorMsg, e);
            throw new TerminateWorkflowException(errorMsg);
        }

        addTaskToQueue(tasksToBeQueued);
        return startedSystemTasks;
    }

    private void addTaskToQueue(List<TaskModel> tasks) {
        for (TaskModel task : tasks) {
            try {
                executionQueue.submitTask(QueueUtils.getQueueName(task), task.getTaskId(),
                        this::executeSystemTask);
            } catch (RuntimeException error) {
                String reason = "内存队列提交失败: " + rootCauseMessage(error);
                failTask(task, reason);
                executionDAOFacade.updateTask(task);
                throw new TerminateWorkflowException(reason, WorkflowModel.Status.FAILED, task);
            }
        }
    }

    private boolean isControlTask(TaskModel task) {
        return TaskType.isBuiltIn(task.getTaskType())
                || TaskType.TASK_TYPE_TERMINATE.equals(task.getTaskType());
    }

    /** 对齐 Conductor 异步执行器：取任务、执行、更新任务，再通知工作流重新 decide。 */
    private void executeSystemTask(String taskId) {
        TaskModel scheduled = executionDAOFacade.getTask(taskId).orElse(null);
        if (scheduled == null) {
            return;
        }
        String workflowId = scheduled.getWorkflowInstanceId();
        WorkflowModel workflow = null;
        try {
            WorkflowModel execution;
            lockWorkflow(workflowId);
            try {
                workflow = executionDAOFacade.getWorkflowModel(workflowId);
                if (workflow == null || workflow.getStatus().isTerminal()) {
                    return;
                }
                TaskModel task = findTask(workflow, taskId);
                if (task == null || task.getStatus() != TaskModel.Status.SCHEDULED) {
                    return;
                }
                markStarted(task);
                executionDAOFacade.updateTask(task);
                execution = copyWorkflow(workflow);
            } finally {
                executionLockService.releaseLock(workflowId);
            }

            TaskModel result = findTask(execution, taskId);
            startSystemTask(execution, result);

            lockWorkflow(workflowId);
            try {
                workflow = executionDAOFacade.getWorkflowModel(workflowId);
                if (workflow == null || workflow.getStatus().isTerminal()) {
                    return;
                }
                TaskModel task = findTask(workflow, taskId);
                if (task == null || task.getStatus() != TaskModel.Status.IN_PROGRESS) {
                    return;
                }
                task.setInputData(result.getInputData());
                task.setOutputData(result.getOutputData());
                task.setReasonForInCompletion(result.getReasonForInCompletion());
                task.setStartTime(result.getStartTime());
                task.setEndTime(result.getEndTime());
                task.setUpdateTime(result.getUpdateTime());
                task.setStatus(result.getStatus());
                executionDAOFacade.updateTask(task);
            } finally {
                executionLockService.releaseLock(workflowId);
            }
            decideWithLock(workflowId);
        } catch (RuntimeException error) {
            if (workflow == null) {
                throw error;
            }
            failWorkflow(workflow, "异步任务执行或重新决策失败", error);
        }
    }

    private void startSystemTask(WorkflowModel workflow, TaskModel task) {
        try {
            systemTaskRegistry.get(task.getTaskType()).start(workflow, task, this);
            if (!isControlTask(task) && !task.getStatus().isTerminal()) {
                failTask(task, "业务任务 start() 返回时必须进入终态");
            }
        } catch (Throwable error) {
            failTask(task, "任务执行异常: " + rootCauseMessage(error));
            LOGGER.error("Task start failed: workflow={}, task={}",
                    workflow.getWorkflowId(), task.getTaskId(), error);
        }
        finishTask(task);
    }

    private void markStarted(TaskModel task) {
        long now = System.currentTimeMillis();
        task.setStartTime(now);
        task.setUpdateTime(now);
        task.setStatus(TaskModel.Status.IN_PROGRESS);
    }

    private void finishTask(TaskModel task) {
        long now = System.currentTimeMillis();
        task.setUpdateTime(now);
        if (task.getStatus().isTerminal() && task.getEndTime() == 0) {
            task.setEndTime(now);
        }
    }

    private void failTask(TaskModel task, String reason) {
        task.setReasonForInCompletion(reason);
        task.setStatus(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR);
        finishTask(task);
    }

    private void failWorkflow(WorkflowModel fallback, String reason, Throwable error) {
        String workflowId = fallback.getWorkflowId();
        lockWorkflow(workflowId);
        try {
            WorkflowModel stored = executionDAOFacade.getWorkflowModel(workflowId);
            WorkflowModel workflow = stored != null ? stored : fallback;
            if (workflow.getStatus().isTerminal()) {
                return;
            }
            persistTerminatedWorkflow(workflow,
                    new TerminateWorkflowException(reason + ": " + rootCauseMessage(error), error));
        } finally {
            executionLockService.releaseLock(workflowId);
        }
    }

    private void lockWorkflow(String workflowId) {
        boolean interrupted = Thread.interrupted();
        while (!executionLockService.acquireLock(workflowId)) {
            if (Thread.interrupted()) {
                interrupted = true;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException error) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private String rootCauseMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
    }

    private TaskModel findTask(WorkflowModel workflow, String taskId) {
        for (TaskModel task : workflow.getTasks()) {
            if (Objects.equals(taskId, task.getTaskId())) {
                return task;
            }
        }
        return null;
    }

    private WorkflowModel copyWorkflow(WorkflowModel workflow) {
        WorkflowModel copy = WorkflowExecutionMapper.toModel(
                WorkflowExecutionMapper.toWorkflowHeader(workflow));
        copy.setInput(copyInput(workflow.getInput()));
        List<TaskModel> tasks = new ArrayList<>();
        for (TaskModel task : workflow.getTasks()) {
            TaskModel taskCopy = TaskModel.fromTask(task.toTask());
            taskCopy.setInputData(copyInput(task.getInputData()));
            tasks.add(taskCopy);
        }
        copy.setTasks(tasks);
        return copy;
    }

    private void applyTerminalSnapshot(WorkflowModel workflow, WorkflowModel terminal) {
        workflow.setTasks(terminal.getTasks());
        workflow.setOutput(WorkflowExecutionMapper.copyOutput(terminal.getOutput()));
        workflow.setReasonForInCompletion(terminal.getReasonForInCompletion());
        workflow.setFailedTaskId(terminal.getFailedTaskId());
        workflow.setEndTime(terminal.getEndTime());
        workflow.setUpdateTime(terminal.getUpdateTime());
        workflow.setStatus(terminal.getStatus());
    }

    @Override
    public void scheduleNextIteration(TaskModel loopTask, WorkflowModel workflow) {
        // 只调度本轮入口；入口完成后的其余任务由 Decider 推进。
        List<TaskModel> iterationTasks = deciderService.getTasksToBeScheduled(
                workflow, loopTask.getWorkflowTask().getLoopOver().get(0),
                loopTask.getRetryCount(), null, loopTask.getForkTaskId());
        for (TaskModel task : iterationTasks) {
            task.setReferenceTaskName(TaskUtils.appendIteration(
                    task.getReferenceTaskName(), loopTask.getIteration()));
            task.setIteration(loopTask.getIteration());
        }
        scheduleTask(workflow, iterationTasks);
        workflow.getTasks().addAll(iterationTasks);
    }

    private void persistTerminatedWorkflow(
            WorkflowModel workflow, TerminateWorkflowException termination) {
        WorkflowModel.Status terminalStatus = termination.getWorkflowStatus();
        workflow.setStatus(
                terminalStatus != null ? terminalStatus : WorkflowModel.Status.FAILED);
        workflow.setReasonForInCompletion(termination.getMessage());
        if (termination.getTask() != null) {
            workflow.setFailedTaskId(termination.getTask().getTaskId());
            executionDAOFacade.updateTask(termination.getTask());
        }
        workflow.setEndTime(System.currentTimeMillis());
        cancelNonTerminalTasks(workflow, termination.getMessage());
        updateWorkflow(workflow);
    }

    @Override
    public void terminateWorkflow(WorkflowModel workflow, String reason) {
        String workflowId = workflow.getWorkflowId();
        lockWorkflow(workflowId);
        try {
            WorkflowModel current = executionDAOFacade != null
                    ? executionDAOFacade.getWorkflowModel(workflowId) : null;
            if (current == null) {
                current = workflow;
            }
            if (!current.getStatus().isTerminal()) {
                cancelNonTerminalTasks(current, reason);
                current.setStatus(WorkflowModel.Status.TERMINATED);
                current.setReasonForInCompletion(reason);
                current.setEndTime(System.currentTimeMillis());
                updateWorkflow(current);
            }
            if (current != workflow) {
                applyTerminalSnapshot(workflow, current);
            }
        } finally {
            executionLockService.releaseLock(workflowId);
        }
    }

    private void cancelNonTerminalTasks(WorkflowModel workflow, String reason) {
        long now = System.currentTimeMillis();
        for (TaskModel task : workflow.getTasks()) {
            if (task.getStatus() == null || task.getStatus().isTerminal()) {
                continue;
            }
            if (systemTaskRegistry.isSystemTask(task.getTaskType())) {
                try {
                    systemTaskRegistry.get(task.getTaskType()).cancel(workflow, task, this);
                } catch (RuntimeException e) {
                    LOGGER.warn(
                            "Error cancelling system task {} for workflow {}",
                            task.getTaskId(),
                            workflow.getWorkflowId(),
                            e);
                }
            }
            task.setStatus(TaskModel.Status.CANCELED);
            task.setExecuted(true);
            task.setReasonForInCompletion(reason);
            task.setEndTime(now);
            task.setUpdateTime(now);
            if (executionDAOFacade != null) {
                executionDAOFacade.updateTask(task);
            }
        }
    }

    List<TaskModel> dedupAndAddTasks(WorkflowModel workflow, List<TaskModel> tasks) {
        Set<String> tasksInWorkflow =
                workflow.getTasks().stream()
                        .map(task -> task.getReferenceTaskName() + "_" + task.getRetryCount())
                        .collect(Collectors.toSet());

        List<TaskModel> dedupedTasks =
                tasks.stream()
                        .filter(
                                task ->
                                        !tasksInWorkflow.contains(
                                                task.getReferenceTaskName()
                                                        + "_"
                                                        + task.getRetryCount()))
                        .collect(Collectors.toList());

        workflow.getTasks().addAll(dedupedTasks);
        return dedupedTasks;
    }

    @Override
    public void updateWorkflow(WorkflowModel workflow) {
        executionDAOFacade.updateWorkflow(workflow);
        WorkflowModel.Status status = workflow.getStatus();
        if (status == null || !status.isTerminal()) {
            return;
        }
        WorkflowEventType event = switch (status) {
            case COMPLETED -> WorkflowEventType.COMPLETED;
            case FAILED -> WorkflowEventType.FAILED;
            case TIMED_OUT -> WorkflowEventType.TIMED_OUT;
            case TERMINATED -> WorkflowEventType.TERMINATED;
            default -> throw new IllegalStateException("Unsupported terminal workflow status: " + status);
        };
        notifyWorkflowStatusListeners(workflow, event);
    }

    private void notifyWorkflowStatusListeners(WorkflowModel workflow, WorkflowEventType event) {
        for (WorkflowStatusListener listener : workflowStatusListeners) {
            try {
                switch (event) {
                    case STARTED -> listener.onWorkflowStarted(workflow);
                    case COMPLETED -> listener.onWorkflowCompleted(workflow);
                    case FAILED -> listener.onWorkflowFailed(workflow);
                    case TIMED_OUT -> listener.onWorkflowTimedOut(workflow);
                    case TERMINATED -> listener.onWorkflowTerminated(workflow);
                }
            } catch (RuntimeException error) {
                LOGGER.error("Error notifying workflow status listener {} for workflow: {}, event: {}",
                        listener.getClass().getName(), workflow.getWorkflowId(), event, error);
            }
        }
    }

    private void endExecution(WorkflowModel workflow, TaskModel terminateTask) {
        deciderService.updateWorkflowOutput(workflow, terminateTask);
        if (terminateTask != null) {
            String terminationStatus =
                    (String)
                            terminateTask
                                    .getInputData()
                                    .get(Terminate.getTerminationStatusParameter());
            String reason =
                    (String)
                            terminateTask
                                    .getInputData()
                                    .get(Terminate.getTerminationReasonParameter());
            if (!StringUtils.hasText(reason)) {
                reason =
                        String.format(
                                "Workflow is %s by TERMINATE task: %s",
                                terminationStatus, terminateTask.getTaskId());
            }
            workflow.setReasonForInCompletion(reason);
            if (WorkflowModel.Status.FAILED.name().equals(terminationStatus)) {
                workflow.setStatus(WorkflowModel.Status.FAILED);
                workflow.setFailedTaskId(terminateTask.getTaskId());
            } else if (WorkflowModel.Status.TERMINATED.name().equals(terminationStatus)) {
                workflow.setStatus(WorkflowModel.Status.TERMINATED);
                workflow.setFailedTaskId(terminateTask.getTaskId());
            } else {
                workflow.setStatus(WorkflowModel.Status.COMPLETED);
            }
        } else {
            workflow.setStatus(WorkflowModel.Status.COMPLETED);
        }
        workflow.setEndTime(System.currentTimeMillis());
        cancelNonTerminalTasks(workflow, workflow.getReasonForInCompletion());
        updateWorkflow(workflow);
    }

    private WorkflowModel createWorkflowModel(
            StartWorkflowInput input, WorkflowDef workflowDefinition, String workflowId) {
        long now = System.currentTimeMillis();

        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowId(workflowId);
        workflow.setWorkflowDefinition(workflowDefinition);
        workflow.setStatus(WorkflowModel.Status.RUNNING);
        workflow.setStartTime(now);
        workflow.setUpdateTime(now);

        if (input.getWorkflowInput() != null && !input.getWorkflowInput().isEmpty()) {
            workflow.setInput(copyInput(input.getWorkflowInput()));
        }
        workflow.setCorrelationId(resolveCorrelationId(workflow, workflowDefinition));

        return workflow;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> copyInput(Map<String, Object> input) {
        return (Map<String, Object>) WorkflowExecutionMapper.copyOutput(input);
    }

    /**
     * 根据定义上的 {@link WorkflowDef#getCorrelationExpression()} 表达式解析业务关键字，写入运行时 correlationId。
     */
    private String resolveCorrelationId(WorkflowModel workflow, WorkflowDef definition) {
        String expression = definition != null ? definition.getCorrelationExpression() : null;
        if (!StringUtils.hasText(expression)) {
            return null;
        }
        Map<String, Object> resolved =
                parametersUtils.getTaskInput(Map.of("_correlation_key", expression), workflow, null);
        Object value = resolved.get("_correlation_key");
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return StringUtils.hasText(text) ? text : null;
    }

}
