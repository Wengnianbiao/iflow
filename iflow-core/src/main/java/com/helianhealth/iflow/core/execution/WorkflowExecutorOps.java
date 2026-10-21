package com.helianhealth.iflow.core.execution;

import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowDef;
import com.helianhealth.iflow.common.exception.WorkFlowErrorCode;
import com.helianhealth.iflow.common.exception.WorkFlowException;
import com.helianhealth.iflow.core.dal.ExecutionDAOFacade;
import com.helianhealth.iflow.core.exception.TerminateWorkflowException;
import com.helianhealth.iflow.core.exception.TransientException;
import com.helianhealth.iflow.core.execution.tasks.SystemTaskRegistry;
import com.helianhealth.iflow.core.execution.tasks.Terminate;
import com.helianhealth.iflow.core.lock.ExecutionLockService;
import com.helianhealth.iflow.core.lock.InMemoryExecutionLockService;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import com.helianhealth.iflow.core.service.MetadataService;
import com.helianhealth.iflow.core.utils.IDGenerator;
import com.helianhealth.iflow.core.utils.ParametersUtils;
import com.helianhealth.iflow.core.utils.TaskUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;

/**
 * 工作流执行引擎默认实现：调用线程同步推进 DAG，FORK 分支任务交给专用 Scheduler 并行执行。
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
    private TaskScheduler taskScheduler;

    @Autowired
    public WorkflowExecutorOps(
            DeciderService deciderService,
            SystemTaskRegistry systemTaskRegistry,
            @Autowired(required = false) ExecutionDAOFacade executionDAOFacade,
            @Autowired(required = false) MetadataService metadataService,
            @Autowired(required = false) IDGenerator idGenerator,
            ParametersUtils parametersUtils,
            @Autowired(required = false) ExecutionLockService executionLockService) {
        this.deciderService = deciderService;
        this.systemTaskRegistry = systemTaskRegistry;
        this.executionDAOFacade = executionDAOFacade;
        this.metadataService = metadataService;
        this.idGenerator = idGenerator != null ? idGenerator : new IDGenerator();
        this.parametersUtils = parametersUtils;
        this.executionLockService =
                executionLockService != null
                        ? executionLockService
                        : new InMemoryExecutionLockService();
        this.taskScheduler =
                new CallerAndForkTaskScheduler(
                        ForkJoinPool.commonPool(), systemTaskRegistry);
    }

    @Autowired
    public void setTaskScheduler(TaskScheduler taskScheduler) {
        this.taskScheduler = Objects.requireNonNull(taskScheduler);
    }

    @Override
    public WorkflowModel startWorkflow(StartWorkflowInput input) {
        WorkflowDef workflowDefinition = resolveWorkflowDefinition(input);
        String workflowId = idGenerator.generate();
        WorkflowModel workflow = createWorkflowModel(input, workflowDefinition, workflowId);

        try {
            return createAndDriveWithLock(workflow);
        } catch (TerminateWorkflowException e) {
            LOGGER.info("Execution terminated for workflow: {}", workflowId, e);
            persistTerminatedWorkflow(workflow, e);
            throw e;
        } catch (Exception e) {
            LOGGER.error("Unable to start workflow: {}", workflowDefinition.getRoute(), e);
            try {
                executionDAOFacade.removeWorkflow(workflowId);
            } catch (Exception rwe) {
                LOGGER.error("Could not remove the workflowId: {}", workflowId, rwe);
            }
            throw e;
        }
    }

    private WorkflowDef resolveWorkflowDefinition(StartWorkflowInput input) {
        if (!StringUtils.hasText(input.getRoute())) {
            throw new WorkFlowException(
                    WorkFlowErrorCode.REPOSITORY_SAVE_ERROR, "工作流 route 不能为空");
        }

        return metadataService.getWorkflowDef(input.getRoute(), input.getVersion());
    }

    private WorkflowModel createAndDriveWithLock(WorkflowModel workflow) {
        if (!executionLockService.acquireLock(workflow.getWorkflowId())) {
            throw new TransientException(
                    "Error acquiring lock when creating workflow: " + workflow);
        }
        try {
            executionDAOFacade.createWorkflow(workflow);
            LOGGER.debug(
                    "A new instance of workflow: {} created with id: {}",
                    workflow.getWorkflowRoute(),
                    workflow.getWorkflowId());
            return driveWorkflow(workflow);
        } finally {
            executionLockService.releaseLock(workflow.getWorkflowId());
        }
    }

    private WorkflowModel driveWorkflow(WorkflowModel workflow) {
        try (WorkflowDriveContext context = new WorkflowDriveContext(executionDAOFacade)) {
            while (!workflow.getStatus().isTerminal()) {
                boolean receivedEvent = context.consumeEvents(workflow);
                persistDriveChanges(workflow, context);

                DeciderService.DeciderOutcome outcome = deciderService.decide(workflow);
                for (TaskModel task : outcome.tasksToBeUpdated) {
                    context.markDirty(task);
                }
                if (outcome.isComplete) {
                    persistDriveChanges(workflow, context);
                    endExecution(workflow, outcome.terminateTask);
                    return workflow;
                }

                List<TaskModel> newTasks = executionDAOFacade.registerTasks(workflow, outcome.tasksToBeScheduled);
                List<TaskModel> tasksToSchedule = canonicalCandidates(workflow, outcome.tasksToBeScheduled);
                boolean tasksStarted = taskScheduler.schedule(
                        workflow, tasksToSchedule, this, context);
                boolean controlTasksAdvanced = taskScheduler.evaluate(
                        workflow, tasksToSchedule, this, context);
                boolean stateChanged = !newTasks.isEmpty() || tasksStarted || controlTasksAdvanced;
                persistDriveChanges(workflow, context);

                if (workflow.getStatus().isTerminal()) {
                    return workflow;
                }
                if (stateChanged || receivedEvent || context.hasEvents()) {
                    continue;
                }
                if (context.outstandingBranches() == 0) {
                    throw new TerminateWorkflowException("工作流无法推进：没有可执行任务或运行中的 FORK 分支");
                }
                try {
                    context.awaitChange();
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new TerminateWorkflowException("工作流调用线程等待分支时被中断", error);
                }
            }
            return workflow;
        }
    }

    private void persistDriveChanges(WorkflowModel workflow, WorkflowDriveContext context) {
        List<TaskModel> changedTasks = context.drainDirtyTasks();
        if (executionDAOFacade != null && !changedTasks.isEmpty()) {
            executionDAOFacade.updateTasks(changedTasks);
            executionDAOFacade.updateWorkflow(workflow);
        }
    }

    private List<TaskModel> canonicalCandidates(WorkflowModel workflow, List<TaskModel> candidates) {
        Map<String, TaskModel> registered = new LinkedHashMap<>();
        for (TaskModel task : workflow.getTasks()) {
            registered.put(taskKey(task), task);
        }
        List<TaskModel> tasks = new ArrayList<>();
        Set<String> selectedKeys = new HashSet<>();
        for (TaskModel candidate : candidates) {
            String key = taskKey(candidate);
            TaskModel task = registered.get(key);
            if (task == null || selectedKeys.contains(key)) {
                continue;
            }
            selectedKeys.add(key);
            tasks.add(task);
        }
        return tasks;
    }

    private String taskKey(TaskModel task) {
        return task.getReferenceTaskName() + "_" + task.getRetryCount();
    }

    @Override
    public void addNextIterationTasks(TaskModel loopTask, WorkflowModel workflow) {
        List<TaskModel> iterationTasks = deciderService.getTasksToBeScheduled(
                workflow, loopTask.getWorkflowTask().getLoopOver().get(0),
                loopTask.getRetryCount(), null, loopTask.getForkTaskId());
        for (TaskModel task : iterationTasks) {
            task.setReferenceTaskName(TaskUtils.appendIteration(
                    task.getReferenceTaskName(), loopTask.getIteration()));
            task.setIteration(loopTask.getIteration());
        }
        executionDAOFacade.registerTasks(workflow, iterationTasks);
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
        executionDAOFacade.updateWorkflow(workflow);
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
                parametersUtils.getTaskInput(Map.of("_correlation_key", expression), workflow, null);
        Object value = resolved.get("_correlation_key");
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return StringUtils.hasText(text) ? text : null;
    }

}
