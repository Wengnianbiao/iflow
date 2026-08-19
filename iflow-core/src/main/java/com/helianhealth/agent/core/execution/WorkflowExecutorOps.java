package com.helianhealth.agent.core.execution;

import com.helianhealth.agent.common.domain.metadata.workflow.WorkflowDef;
import com.helianhealth.agent.common.domain.metadata.workflow.WorkflowTask;
import com.helianhealth.agent.common.exception.WorkFlowErrorCode;
import com.helianhealth.agent.common.exception.WorkFlowException;
import com.helianhealth.agent.core.dal.ExecutionDAOFacade;
import com.helianhealth.agent.core.exception.TerminateWorkflowException;
import com.helianhealth.agent.core.exception.TransientException;
import com.helianhealth.agent.core.execution.tasks.SystemTaskRegistry;
import com.helianhealth.agent.core.execution.tasks.WorkflowSystemTask;
import com.helianhealth.agent.core.lock.ExecutionLockService;
import com.helianhealth.agent.core.lock.InMemoryExecutionLockService;
import com.helianhealth.agent.core.model.TaskModel;
import com.helianhealth.agent.core.model.WorkflowModel;
import com.helianhealth.agent.core.service.MetadataService;
import com.helianhealth.agent.core.utils.IDGenerator;
import com.helianhealth.agent.core.utils.ParametersUtils;
import com.helianhealth.agent.core.utils.TaskUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Collectors;

/**
 * 工作流执行引擎默认实现：调用线程同步推进 DAG，FORK 分支任务交给专用 Dispatcher 并行执行。
 */
@Service
public class WorkflowExecutorOps implements WorkflowExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkflowExecutorOps.class);

    private final DeciderService deciderService;
    private final SystemTaskRegistry systemTaskRegistry;
    private final ExecutionDAOFacade executionDAOFacade;
    private final MetadataService metadataService;
    private final IDGenerator idGenerator;
    private final ParametersUtils parametersUtils;
    private final ExecutionLockService executionLockService;
    private final Set<String> executingTaskIds = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<String, SynchronousWorkflowRun> activeRuns =
            new ConcurrentHashMap<>();
    private TaskDispatcher taskDispatcher =
            new CallerAndForkTaskDispatcher(ForkJoinPool.commonPool());

    @Autowired
    public WorkflowExecutorOps(
            DeciderService deciderService,
            SystemTaskRegistry systemTaskRegistry,
            @Autowired(required = false) ExecutionDAOFacade executionDAOFacade,
            @Autowired(required = false) MetadataService metadataService,
            @Autowired(required = false) IDGenerator idGenerator,
            @Autowired(required = false) ParametersUtils parametersUtils,
            @Autowired(required = false) ExecutionLockService executionLockService) {
        this.deciderService = deciderService;
        this.systemTaskRegistry = systemTaskRegistry;
        this.executionDAOFacade = executionDAOFacade;
        this.metadataService = metadataService;
        this.idGenerator = idGenerator != null ? idGenerator : new IDGenerator();
        this.parametersUtils = parametersUtils != null ? parametersUtils : new ParametersUtils();
        this.executionLockService =
                executionLockService != null
                        ? executionLockService
                        : new InMemoryExecutionLockService();
    }

    @Autowired
    public void setTaskDispatcher(TaskDispatcher taskDispatcher) {
        this.taskDispatcher = Objects.requireNonNull(taskDispatcher);
    }

    @Override
    public WorkflowModel startWorkflow(StartWorkflowInput input) {
        WorkflowDef workflowDefinition = resolveWorkflowDefinition(input);
        String workflowId = idGenerator.generate();
        WorkflowModel workflow = createWorkflowModel(input, workflowDefinition, workflowId);

        try {
            createWorkflow(workflow);
            return driveWorkflow(workflow);
        } catch (Exception e) {
            LOGGER.error("Unable to start workflow: {}", workflowDefinition.getName(), e);
            try {
                executionDAOFacade.removeWorkflow(workflowId);
            } catch (Exception rwe) {
                LOGGER.error("Could not remove the workflowId: {}", workflowId, rwe);
            }
            throw e;
        }
    }

    private WorkflowDef resolveWorkflowDefinition(StartWorkflowInput input) {
        if (!StringUtils.hasText(input.getName())) {
            throw new WorkFlowException(
                    WorkFlowErrorCode.REPOSITORY_SAVE_ERROR, "工作流 name 不能为空");
        }

        // version 为 null 时 MetadataService 取最新版本
        return metadataService.getWorkflowDef(input.getName(), input.getVersion());
    }

    private void createWorkflow(WorkflowModel workflow) {
        if (!executionLockService.acquireLock(workflow.getWorkflowId())) {
            throw new TransientException(
                    "Error acquiring lock when creating workflow: " + workflow);
        }
        try {
            executionDAOFacade.createWorkflow(workflow);
            LOGGER.debug(
                    "A new instance of workflow: {} created with id: {}",
                    workflow.getWorkflowName(),
                    workflow.getWorkflowId());
        } finally {
            executionLockService.releaseLock(workflow.getWorkflowId());
        }
    }

    private WorkflowModel driveWorkflow(WorkflowModel workflow) {
        String workflowId = workflow.getWorkflowId();
        SynchronousWorkflowRun run = new SynchronousWorkflowRun();
        SynchronousWorkflowRun existing = activeRuns.putIfAbsent(workflowId, run);
        if (existing != null) {
            existing.signalProgress();
            return workflow;
        }

        try {
            while (!workflow.getStatus().isTerminal()
                    && workflow.getStatus() != WorkflowModel.Status.PAUSED) {
                run.throwIfFailed();
                long observedProgress = run.progressVersion();
                ExecutionBatch batch = evaluateWithLock(workflow);
                if (workflow.getStatus().isTerminal()) {
                    break;
                }

                DispatchSummary dispatchSummary =
                        dispatchTasks(workflow, batch.tasksToDispatch(), run);
                if (batch.stateChanged() || dispatchSummary.inlineStateChanged()) {
                    continue;
                }

                run.throwIfFailed();
                if (run.hasProgressedSince(observedProgress)) {
                    continue;
                }
                if (run.hasForkTasksInFlight()) {
                    run.awaitProgress(observedProgress, workflow);
                    continue;
                }

                throw new WorkFlowException(
                        WorkFlowErrorCode.INTERNAL_ERROR,
                        "Workflow " + workflowId + " has no runnable task and made no progress");
            }
            run.throwIfFailed();
            return workflow;
        } finally {
            activeRuns.remove(workflowId, run);
        }
    }

    private ExecutionBatch evaluateWithLock(WorkflowModel workflow) {
        String workflowId = workflow.getWorkflowId();
        if (!executionLockService.acquireLock(workflowId)) {
            throw new TransientException(
                    "Error acquiring lock when deciding workflow: " + workflowId);
        }
        try {
            return evaluate(workflow);
        } finally {
            executionLockService.releaseLock(workflowId);
        }
    }

    private ExecutionBatch evaluate(WorkflowModel workflow) {
        if (workflow.getStatus().isTerminal()) {
            if (!workflow.getStatus().isSuccessful()) {
                cancelNonTerminalTasks(workflow, workflow.getReasonForInCompletion());
            }
            return ExecutionBatch.empty();
        }

        try {
            DeciderService.DeciderOutcome outcome = deciderService.decide(workflow);
            if (outcome.isComplete) {
                persistTaskChanges(outcome.tasksToBeUpdated);
                endExecution(workflow, outcome.terminateTask);
                return new ExecutionBatch(List.of(), true);
            }

            persistTaskChanges(outcome.tasksToBeUpdated);
            List<TaskModel> newTasks = registerTasks(workflow, outcome.tasksToBeDispatched);
            boolean stateChanged = !outcome.tasksToBeUpdated.isEmpty() || !newTasks.isEmpty();

            if (stateChanged) {
                updateWorkflow(workflow);
            }
            return new ExecutionBatch(outcome.tasksToBeDispatched, stateChanged);
        } catch (TerminateWorkflowException e) {
            LOGGER.info("Execution terminated for workflow: {}", workflow.getWorkflowId(), e);
            workflow.setStatus(
                    e.getWorkflowStatus() != null
                            ? e.getWorkflowStatus()
                            : WorkflowModel.Status.FAILED);
            workflow.setReasonForInCompletion(e.getMessage());
            if (e.getTask() != null) {
                workflow.setFailedTaskId(e.getTask().getTaskId());
            }
            workflow.setEndTime(System.currentTimeMillis());
            cancelNonTerminalTasks(workflow, e.getMessage());
            updateWorkflow(workflow);
            return new ExecutionBatch(List.of(), true);
        } catch (RuntimeException e) {
            LOGGER.error("Error deciding workflow: {}", workflow.getWorkflowId(), e);
            throw e;
        }
    }

    @Override
    public WorkflowModel decideWithLock(WorkflowModel workflow) {
        SynchronousWorkflowRun run = activeRuns.get(workflow.getWorkflowId());
        if (run != null) {
            run.signalProgress();
            return workflow;
        }
        return driveWorkflow(workflow);
    }

    @Override
    public void terminateWorkflow(WorkflowModel workflow, String reason) {
        cancelNonTerminalTasks(workflow, reason);
        workflow.setStatus(WorkflowModel.Status.TERMINATED);
        workflow.setReasonForInCompletion(reason);
        workflow.setEndTime(System.currentTimeMillis());
        if (executionDAOFacade != null) {
            executionDAOFacade.updateWorkflow(workflow);
        }
        SynchronousWorkflowRun run = activeRuns.get(workflow.getWorkflowId());
        if (run != null) {
            run.signalProgress();
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

    @Override
    public void updateWorkflow(WorkflowModel workflow) {
        if (executionDAOFacade != null) {
            executionDAOFacade.updateWorkflow(workflow);
        }
    }

    @Override
    public WorkflowModel decide(String workflowId) {
        boolean lockAcquired = executionLockService.acquireLock(workflowId);
        if (!lockAcquired) {
            return null;
        }
        WorkflowModel workflow;
        try {
            workflow = executionDAOFacade.getWorkflowModel(workflowId);
            if (workflow == null) {
                return null;
            }
        } finally {
            executionLockService.releaseLock(workflowId);
        }
        return driveWorkflow(workflow);
    }

    @Override
    public void scheduleNextIteration(TaskModel task, WorkflowModel workflow) {
        WorkflowTask workflowTask = task.getWorkflowTask();
        if (workflowTask == null
                || workflowTask.getLoopOver() == null
                || workflowTask.getLoopOver().isEmpty()) {
            throw new IllegalArgumentException("DO_WHILE loopOver must contain at least one task");
        }

        List<TaskModel> loopTasks =
                deciderService.getTasksToBeScheduled(
                        workflow,
                        workflowTask.getLoopOver().get(0),
                        task.getRetryCount(),
                        null,
                        task.getForkTaskId());
        loopTasks.forEach(
                loopTask -> {
                    loopTask.setReferenceTaskName(
                            TaskUtils.appendIteration(
                                    loopTask.getReferenceTaskName(), task.getIteration()));
                    loopTask.setIteration(task.getIteration());
                });

        SynchronousWorkflowRun run = activeRuns.get(workflow.getWorkflowId());
        if (run == null) {
            throw new IllegalStateException(
                    "DO_WHILE iteration can only be scheduled by an active synchronous workflow");
        }

        if (!executionLockService.acquireLock(workflow.getWorkflowId())) {
            throw new TransientException(
                    "Error acquiring lock when scheduling DO_WHILE iteration: "
                            + workflow.getWorkflowId());
        }
        List<TaskModel> newTasks;
        try {
            newTasks = registerTasks(workflow, loopTasks);
            if (!newTasks.isEmpty()) {
                updateWorkflow(workflow);
            }
        } finally {
            executionLockService.releaseLock(workflow.getWorkflowId());
        }
        dispatchTasks(workflow, newTasks, run);
    }

    private void endExecution(WorkflowModel workflow, TaskModel terminateTask) {
        deciderService.updateWorkflowOutput(workflow, terminateTask);
        if (terminateTask != null) {
            Object status = terminateTask.getOutputData().get("workflowStatus");
            if ("FAILED".equals(status)) {
                workflow.setStatus(WorkflowModel.Status.FAILED);
            } else {
                workflow.setStatus(WorkflowModel.Status.COMPLETED);
            }
        } else {
            workflow.setStatus(WorkflowModel.Status.COMPLETED);
        }
        workflow.setEndTime(System.currentTimeMillis());
        updateWorkflow(workflow);
    }

    /**
     * 登记新任务并持久化。传入列表也可能包含已经存在但需要再次评估的 JOIN/DO_WHILE，
     * 因而“创建去重”和“派发去重”必须分开处理。
     */
    private List<TaskModel> registerTasks(
            WorkflowModel workflow, List<TaskModel> tasksToBeScheduled) {
        if (tasksToBeScheduled == null || tasksToBeScheduled.isEmpty()) {
            return List.of();
        }

        List<TaskModel> newTasks = dedupAndAddTasks(workflow, tasksToBeScheduled);
        int count = workflow.getTasks().stream().mapToInt(TaskModel::getSeq).max().orElse(0);
        long now = System.currentTimeMillis();
        for (TaskModel task : newTasks) {
            if (task.getSeq() == 0) {
                task.setSeq(++count);
            }
            if (task.getFirstScheduledTime() == 0) {
                task.setFirstScheduledTime(now);
            }
            if (task.getScheduledTime() == 0) {
                task.setScheduledTime(now);
            }
        }

        if (executionDAOFacade != null && !newTasks.isEmpty()) {
            executionDAOFacade.createTasks(newTasks);
        }
        return newTasks;
    }

    private DispatchSummary dispatchTasks(
            WorkflowModel workflow,
            List<TaskModel> tasksToDispatch,
            SynchronousWorkflowRun run) {
        if (tasksToDispatch == null || tasksToDispatch.isEmpty()) {
            return DispatchSummary.NO_PROGRESS;
        }

        boolean inlineStateChanged = false;
        for (TaskModel task : tasksToDispatch) {
            if (task.getStatus() == null) {
                continue;
            }
            if (task.getStatus().isTerminal()) {
                continue;
            }
            if (!systemTaskRegistry.isSystemTask(task.getTaskType())) {
                continue;
            }
            if (!run.shouldEvaluate(task.getTaskId())) {
                continue;
            }
            if (!executingTaskIds.add(task.getTaskId())) {
                continue;
            }

            TaskDispatcher.DispatchMode mode = taskDispatcher.getDispatchMode(task);
            TaskExecutionAttempt attempt = new TaskExecutionAttempt();
            long evaluatedAtVersion = run.semanticVersion();
            if (mode == TaskDispatcher.DispatchMode.FORK_BRANCH) {
                run.forkTaskStarted();
            }

            Runnable command =
                    () -> {
                        try {
                            attempt.stateChanged = executeTask(workflow, task);
                        } catch (RuntimeException e) {
                            attempt.failure = e;
                            LOGGER.error(
                                    "Unable to finish workflow task: workflow={}, task={}, type={}",
                                    workflow.getWorkflowId(),
                                    task.getTaskId(),
                                    task.getTaskType(),
                                    e);
                        } finally {
                            executingTaskIds.remove(task.getTaskId());
                            run.taskFinished(
                                    task.getTaskId(),
                                    evaluatedAtVersion,
                                    attempt.stateChanged,
                                    mode == TaskDispatcher.DispatchMode.FORK_BRANCH,
                                    attempt.failure);
                        }
                    };

            try {
                taskDispatcher.dispatch(mode, command);
            } catch (RejectedExecutionException e) {
                executingTaskIds.remove(task.getTaskId());
                failTask(task, "FORK 分支任务执行线程池已满");
                finishTaskExecution(task);
                persistTask(task);
                run.taskRejected(
                        task.getTaskId(),
                        mode == TaskDispatcher.DispatchMode.FORK_BRANCH);
                LOGGER.error(
                        "Fork branch executor rejected task: workflow={}, task={}, type={}",
                        workflow.getWorkflowId(),
                        task.getTaskId(),
                        task.getTaskType(),
                        e);
                continue;
            }

            if (mode == TaskDispatcher.DispatchMode.CALLER) {
                if (attempt.failure != null) {
                    throw attempt.failure;
                }
                inlineStateChanged |= attempt.stateChanged;
            }
        }
        return new DispatchSummary(inlineStateChanged);
    }

    List<TaskModel> dedupAndAddTasks(WorkflowModel workflow, List<TaskModel> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return List.of();
        }

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

    private boolean executeTask(WorkflowModel workflow, TaskModel task) {
        WorkflowSystemTask systemTask = systemTaskRegistry.get(task.getTaskType());
        TaskModel.Status statusBefore = task.getStatus();
        prepareTaskForExecution(task);
        persistTask(task);
        boolean stateChanged;
        try {
            stateChanged = systemTask.execute(workflow, task, this);
        } catch (RuntimeException e) {
            failTask(task, "任务执行异常: " + rootCauseMessage(e));
            LOGGER.error(
                    "System task execution failed: workflow={}, task={}, type={}",
                    workflow.getWorkflowId(),
                    task.getTaskId(),
                    task.getTaskType(),
                    e);
            stateChanged = true;
        }
        finishTaskExecution(task);
        persistTask(task);
        return stateChanged || !Objects.equals(statusBefore, task.getStatus());
    }

    private void persistTask(TaskModel task) {
        if (executionDAOFacade != null) {
            executionDAOFacade.updateTask(task);
        }
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
        if (task.getStatus() != null && task.getStatus().isTerminal() && task.getEndTime() == 0) {
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
        return current.getMessage() != null ? current.getMessage() : current.getClass().getName();
    }

    private void persistTaskChanges(List<TaskModel> tasksToBeUpdated) {
        if (executionDAOFacade == null) {
            return;
        }
        for (TaskModel task : tasksToBeUpdated) {
            executionDAOFacade.updateTask(task);
        }
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
            workflow.setInput(new HashMap<>(input.getWorkflowInput()));
        }
        workflow.setCorrelationId(resolveCorrelationId(workflow, workflowDefinition));

        return workflow;
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
                parametersUtils.getTaskInput(Map.of("_key", expression), workflow, null);
        Object value = resolved.get("_key");
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return StringUtils.hasText(text) ? text : null;
    }

    private record ExecutionBatch(List<TaskModel> tasksToDispatch, boolean stateChanged) {

        private static ExecutionBatch empty() {
            return new ExecutionBatch(List.of(), false);
        }
    }

    private record DispatchSummary(boolean inlineStateChanged) {

        private static final DispatchSummary NO_PROGRESS = new DispatchSummary(false);
    }

    private static final class TaskExecutionAttempt {

        private boolean stateChanged;
        private RuntimeException failure;
    }

    /**
     * 单次同步工作流的进度信号。这里只保存版本号和在途数量，不保存任务或完成事件，
     * 因此不是工作流任务队列。
     */
    private static final class SynchronousWorkflowRun {

        private long progressVersion;
        private long semanticVersion;
        private int forkTasksInFlight;
        private RuntimeException failure;
        private final Map<String, Long> waitingTaskVersions = new HashMap<>();

        private synchronized long progressVersion() {
            return progressVersion;
        }

        private synchronized void forkTaskStarted() {
            forkTasksInFlight++;
        }

        private synchronized long semanticVersion() {
            return semanticVersion;
        }

        private synchronized boolean shouldEvaluate(String taskId) {
            Long waitingAtVersion = waitingTaskVersions.get(taskId);
            if (waitingAtVersion == null) {
                return true;
            }
            if (waitingAtVersion != semanticVersion) {
                waitingTaskVersions.remove(taskId);
                return true;
            }
            return false;
        }

        private synchronized void taskFinished(
                String taskId,
                long evaluatedAtVersion,
                boolean stateChanged,
                boolean forkBranch,
                RuntimeException taskFailure) {
            if (forkBranch && forkTasksInFlight > 0) {
                forkTasksInFlight--;
            }
            if (failure == null && taskFailure != null) {
                failure = taskFailure;
            }
            if (stateChanged) {
                waitingTaskVersions.remove(taskId);
                semanticVersion++;
            } else if (taskFailure == null) {
                waitingTaskVersions.put(taskId, evaluatedAtVersion);
            }
            if (forkBranch || stateChanged || taskFailure != null) {
                progressVersion++;
                notifyAll();
            }
        }

        private synchronized void taskRejected(String taskId, boolean forkBranch) {
            if (forkBranch && forkTasksInFlight > 0) {
                forkTasksInFlight--;
            }
            waitingTaskVersions.remove(taskId);
            semanticVersion++;
            progressVersion++;
            notifyAll();
        }

        private synchronized void signalProgress() {
            semanticVersion++;
            progressVersion++;
            notifyAll();
        }

        private synchronized boolean hasForkTasksInFlight() {
            return forkTasksInFlight > 0;
        }

        private synchronized boolean hasProgressedSince(long observedProgress) {
            return progressVersion != observedProgress;
        }

        private synchronized void awaitProgress(
                long observedProgress, WorkflowModel workflow) {
            while (progressVersion == observedProgress
                    && forkTasksInFlight > 0
                    && failure == null
                    && !workflow.getStatus().isTerminal()) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new WorkFlowException(
                            WorkFlowErrorCode.WORKFLOW_EXECUTION_INTERRUPTED, e);
                }
            }
        }

        private synchronized void throwIfFailed() {
            if (failure != null) {
                throw failure;
            }
        }
    }

}
