package com.helianhealth.agent.core.execution;

import com.helianhealth.agent.common.domain.metadata.workflow.WorkflowDef;
import com.helianhealth.agent.common.domain.metadata.workflow.WorkflowTask;
import com.helianhealth.agent.common.exception.WorkFlowErrorCode;
import com.helianhealth.agent.common.exception.WorkFlowException;
import com.helianhealth.agent.core.config.AgentCoreConfiguration;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Collectors;

/**
 * 工作流执行引擎默认实现：同步等待 DAG 完成，任务在线程池中执行，任务完成后回调单轮 Decider。
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
    private final Set<String> taskExecutionRequested = ConcurrentHashMap.newKeySet();
    private final Set<String> decisionCallbacksInFlight = ConcurrentHashMap.newKeySet();
    private final Set<String> decisionCallbacksRequested = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<String, CompletableFuture<WorkflowModel>> workflowCompletions =
            new ConcurrentHashMap<>();
    private Executor taskExecutor = ForkJoinPool.commonPool();

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

    /** Spring 环境使用专用有界线程池；commonPool 仅作为非 Spring 单元测试的兜底。 */
    @Autowired
    public void setTaskExecutor(
            @Qualifier(AgentCoreConfiguration.WORKFLOW_TASK_EXECUTOR) Executor taskExecutor) {
        this.taskExecutor = Objects.requireNonNull(taskExecutor);
    }

    @Override
    public WorkflowModel startWorkflow(StartWorkflowInput input) {
        WorkflowDef workflowDefinition = resolveWorkflowDefinition(input);
        String workflowId = idGenerator.generate();
        WorkflowModel workflow = createWorkflowModel(input, workflowDefinition, workflowId);
        CompletableFuture<WorkflowModel> completion = new CompletableFuture<>();
        workflowCompletions.put(workflowId, completion);

        try {
            createAndEvaluate(workflow);
            return awaitWorkflowCompletion(completion);
        } catch (Exception e) {
            LOGGER.error("Unable to start workflow: {}", workflowDefinition.getName(), e);
            try {
                executionDAOFacade.removeWorkflow(workflowId);
            } catch (Exception rwe) {
                LOGGER.error("Could not remove the workflowId: {}", workflowId, rwe);
            }
            throw e;
        } finally {
            workflowCompletions.remove(workflowId, completion);
        }
    }

    private WorkflowModel awaitWorkflowCompletion(CompletableFuture<WorkflowModel> completion) {
        try {
            return completion.join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new WorkFlowException(WorkFlowErrorCode.INTERNAL_ERROR, cause);
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

    private void createAndEvaluate(WorkflowModel workflow) {
        if (!executionLockService.acquireLock(workflow.getWorkflowId())) {
            throw new TransientException(
                    "Error acquiring lock when creating workflow: " + workflow);
        }
        try {
            createAndEvaluateWithLock(workflow);
        } finally {
            executionLockService.releaseLock(workflow.getWorkflowId());
        }
    }

    private void createAndEvaluateWithLock(WorkflowModel workflow) {
        executionDAOFacade.createWorkflow(workflow);
        LOGGER.debug(
                "A new instance of workflow: {} created with id: {}",
                workflow.getWorkflowName(),
                workflow.getWorkflowId());
        decide(workflow);
    }

    private WorkflowModel decide(WorkflowModel workflow) {
        if (workflow.getStatus().isTerminal()) {
            if (!workflow.getStatus().isSuccessful()) {
                cancelNonTerminalTasks(workflow, workflow.getReasonForInCompletion());
            }
            completeWorkflow(workflow);
            return workflow;
        }

        try {
            // 每次回调只做一轮决策。下一轮由本轮任务 execute 完成后触发。
            DeciderService.DeciderOutcome outcome = deciderService.decide(workflow);
            if (outcome.isComplete) {
                persistTaskChanges(outcome.tasksToBeUpdated);
                endExecution(workflow, outcome.terminateTask);
                return workflow;
            }

            persistTaskChanges(outcome.tasksToBeUpdated);
            boolean newTasksScheduled = scheduleTask(workflow, outcome.tasksToBeScheduled);

            if (!outcome.tasksToBeUpdated.isEmpty() || newTasksScheduled) {
                updateWorkflow(workflow);
            }
            return workflow;
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
            completeWorkflow(workflow);
            return workflow;
        } catch (RuntimeException e) {
            LOGGER.error("Error deciding workflow: {}", workflow.getWorkflowId(), e);
            completeWorkflowExceptionally(workflow, e);
            throw e;
        }
    }

    @Override
    public WorkflowModel decideWithLock(WorkflowModel workflow) {
        String workflowId = workflow.getWorkflowId();
        if (!executionLockService.acquireLock(workflowId)) {
            LOGGER.debug("Unable to acquire lock when deciding workflow: {}", workflowId);
            return workflow;
        }
        try {
            return decide(workflow);
        } finally {
            executionLockService.releaseLock(workflowId);
        }
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
        completeWorkflow(workflow);
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
        StopWatch watch = new StopWatch();
        watch.start();
        boolean lockAcquired = executionLockService.acquireLock(workflowId);
        if (!lockAcquired) {
            return null;
        }
        try {

            WorkflowModel workflow = executionDAOFacade.getWorkflowModel(workflowId);
            if (workflow == null) {
                // This can happen if the workflowId is incorrect
                return null;
            }
            return decide(workflow);

        } finally {
            executionLockService.releaseLock(workflowId);
            watch.stop();
        }
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
                        null);
        loopTasks.forEach(
                loopTask -> {
                    loopTask.setReferenceTaskName(
                            TaskUtils.appendIteration(
                                    loopTask.getReferenceTaskName(), task.getIteration()));
                    loopTask.setIteration(task.getIteration());
                });

        scheduleTask(workflow, loopTasks);
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
        completeWorkflow(workflow);
    }

    private boolean scheduleTask(WorkflowModel workflow, List<TaskModel> tasksToBeScheduled) {
        if (tasksToBeScheduled == null || tasksToBeScheduled.isEmpty()) {
            return false;
        }

        tasksToBeScheduled = dedupAndAddTasks(workflow, tasksToBeScheduled);
        int count = workflow.getTasks().stream().mapToInt(TaskModel::getSeq).max().orElse(0);
        long now = System.currentTimeMillis();
        for (TaskModel task : tasksToBeScheduled) {
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

        if (executionDAOFacade != null && !tasksToBeScheduled.isEmpty()) {
            executionDAOFacade.createTasks(tasksToBeScheduled);
        }

        for (TaskModel task : tasksToBeScheduled) {
            if (task.getStatus() == null) {
                continue;
            }
            if (task.getStatus().isTerminal()) {
                submitDecisionCallback(workflow);
                continue;
            }
            if (!systemTaskRegistry.isSystemTask(task.getTaskType())) {
                continue;
            }
            submitTaskExecution(workflow, task);
        }
        return !tasksToBeScheduled.isEmpty();
    }

    private void submitTaskExecution(WorkflowModel workflow, TaskModel task) {
        if (!executingTaskIds.add(task.getTaskId())) {
            // 记录任务执行期间到达的新一轮评估请求，避免 JOIN/DO_WHILE 丢失依赖完成事件。
            taskExecutionRequested.add(task.getTaskId());
            return;
        }
        try {
            taskExecutor.execute(
                    () -> {
                        boolean stateChanged = false;
                        try {
                            stateChanged = executeTask(workflow, task);
                        } catch (RuntimeException e) {
                            LOGGER.error(
                                    "Unable to finish workflow task: workflow={}, task={}, type={}",
                                    workflow.getWorkflowId(),
                                    task.getTaskId(),
                                    task.getTaskType(),
                                    e);
                            completeWorkflowExceptionally(workflow, e);
                        } finally {
                            executingTaskIds.remove(task.getTaskId());
                        }
                        boolean reevaluate = taskExecutionRequested.remove(task.getTaskId());
                        if (stateChanged) {
                            submitDecisionCallback(workflow);
                        } else if (reevaluate) {
                            submitTaskExecution(workflow, task);
                        }
                    });
        } catch (RejectedExecutionException e) {
            executingTaskIds.remove(task.getTaskId());
            taskExecutionRequested.remove(task.getTaskId());
            failTask(task, "任务执行线程池已满");
            finishTaskExecution(task);
            persistTask(task);
            LOGGER.error(
                    "Workflow task executor rejected task: workflow={}, task={}, type={}",
                    workflow.getWorkflowId(),
                    task.getTaskId(),
                    task.getTaskType(),
                    e);
            submitDecisionCallback(workflow);
        }
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

    private void submitDecisionCallback(WorkflowModel workflow) {
        String workflowId = workflow.getWorkflowId();
        if (!decisionCallbacksInFlight.add(workflowId)) {
            decisionCallbacksRequested.add(workflowId);
            return;
        }
        try {
            // 回调不占用任务池容量，避免任务池饱和时连“失败后的继续决策”也被拒绝。
            ForkJoinPool.commonPool()
                    .execute(
                            () -> {
                                try {
                                    decideAfterTaskExecution(workflow);
                                } finally {
                                    decisionCallbacksInFlight.remove(workflowId);
                                    if (decisionCallbacksRequested.remove(workflowId)
                                            && !workflow.getStatus().isTerminal()) {
                                        submitDecisionCallback(workflow);
                                    }
                                }
                            });
        } catch (RejectedExecutionException e) {
            decisionCallbacksInFlight.remove(workflowId);
            completeWorkflowExceptionally(workflow, e);
            throw e;
        }
    }

    /**
     * 任务完成回调不能丢。锁竞争失败时继续等待下一次获取机会，确保终态任务最终会被 Decider 消费。
     */
    private void decideAfterTaskExecution(WorkflowModel workflow) {
        String workflowId = workflow.getWorkflowId();
        while (!workflow.getStatus().isTerminal()) {
            if (!executionLockService.acquireLock(workflowId)) {
                continue;
            }
            try {
                decide(workflow);
                return;
            } finally {
                executionLockService.releaseLock(workflowId);
            }
        }
        completeWorkflow(workflow);
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

    private void completeWorkflow(WorkflowModel workflow) {
        if (workflow.getStatus() == null || !workflow.getStatus().isTerminal()) {
            return;
        }
        decisionCallbacksRequested.remove(workflow.getWorkflowId());
        CompletableFuture<WorkflowModel> completion =
                workflowCompletions.get(workflow.getWorkflowId());
        if (completion != null) {
            completion.complete(workflow);
        }
    }

    private void completeWorkflowExceptionally(WorkflowModel workflow, RuntimeException error) {
        CompletableFuture<WorkflowModel> completion =
                workflowCompletions.get(workflow.getWorkflowId());
        if (completion != null) {
            completion.completeExceptionally(error);
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

}
