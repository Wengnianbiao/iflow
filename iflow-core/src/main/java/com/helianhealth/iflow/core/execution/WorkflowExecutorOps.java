package com.helianhealth.iflow.core.execution;

import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowDef;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowTask;
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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

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
        while (!workflow.getStatus().isTerminal()) {

            DeciderService.DeciderOutcome outcome = deciderService.decide(workflow);
            if (outcome.isComplete) {
                executionDAOFacade.updateTasks(outcome.tasksToBeUpdated);
                endExecution(workflow, outcome.terminateTask);
                return workflow;
            }

            List<TaskModel> tasksToBeScheduled = outcome.tasksToBeScheduled;
            tasksToBeScheduled = dedupAndAddTasks(workflow, tasksToBeScheduled);
            scheduleTask(workflow, tasksToBeScheduled);

            if (!outcome.tasksToBeUpdated.isEmpty() || !tasksToBeScheduled.isEmpty()) {
                Map<String, TaskModel> tasksToBeUpdated = new LinkedHashMap<>();
                outcome.tasksToBeUpdated.forEach(task -> tasksToBeUpdated.put(task.getTaskId(), task));
                tasksToBeScheduled.forEach(task -> tasksToBeUpdated.put(task.getTaskId(), task));
                executionDAOFacade.updateTasks(List.copyOf(tasksToBeUpdated.values()));
                executionDAOFacade.updateWorkflow(workflow);
            }
        }
        return workflow;
    }

    private void scheduleTask(WorkflowModel workflow, List<TaskModel> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return;
        }

        int count = workflow.getTasks().stream().mapToInt(TaskModel::getSeq).max().orElse(0);
        long now = System.currentTimeMillis();
        for (TaskModel task : tasks) {
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
        if (executionDAOFacade != null && !tasks.isEmpty()) {
            executionDAOFacade.createTasks(tasks);
        }
        taskScheduler.schedule(workflow, tasks, this);
    }

    @Override
    public void addNextIterationTasks(TaskModel loopTask, WorkflowModel workflow) {
        List<TaskModel> iterationTasks = deciderService.getTasksToBeScheduled(
                workflow,
                loopTask.getWorkflowTask().getLoopOver().get(0),
                loopTask.getRetryCount(),
                null,
                loopTask.getForkTaskId());
        iterationTasks.forEach(task -> {
            task.setReferenceTaskName(TaskUtils.appendIteration(
                    task.getReferenceTaskName(), loopTask.getIteration()));
            task.setIteration(loopTask.getIteration());
        });
        dedupAndAddTasks(workflow, iterationTasks);
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
    public WorkflowModel decideWithLock(WorkflowModel workflow) {
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
                                        tasksInWorkflow.add(
                                                task.getReferenceTaskName()
                                                        + "_"
                                                        + task.getRetryCount()))
                        .collect(Collectors.toList());

        workflow.getTasks().addAll(dedupedTasks);
        return dedupedTasks;
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
