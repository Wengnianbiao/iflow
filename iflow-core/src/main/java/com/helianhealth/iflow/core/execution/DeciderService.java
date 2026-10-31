package com.helianhealth.iflow.core.execution;

import com.helianhealth.iflow.common.domain.metadata.task.TaskType;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowDef;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowTask;
import com.helianhealth.iflow.core.config.IflowCoreConfiguration;
import com.helianhealth.iflow.core.dao.ExecutionDAO;
import com.helianhealth.iflow.core.exception.TerminateWorkflowException;
import com.helianhealth.iflow.core.execution.mapper.TaskMapper;
import com.helianhealth.iflow.core.execution.mapper.TaskMapperContext;
import com.helianhealth.iflow.core.execution.tasks.SystemTaskRegistry;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import com.helianhealth.iflow.core.model.WorkflowExecutionMapper;
import com.helianhealth.iflow.core.execution.tasks.Terminate;
import com.helianhealth.iflow.core.utils.IDGenerator;
import com.helianhealth.iflow.core.utils.ParametersUtils;
import com.helianhealth.iflow.core.utils.TaskUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.helianhealth.iflow.common.domain.metadata.task.TaskType.SPI;
import static com.helianhealth.iflow.common.domain.metadata.task.TaskType.TERMINATE;
import static com.helianhealth.iflow.core.model.TaskModel.Status.SKIPPED;

/**
 * Decider：评估工作流当前状态，决定调度哪些任务、是否完结
 *
 * <p>职责边界：
 * <ul>
 *   <li>占位符解析 → {@link TaskMapper} + {@link ParametersUtils}</li>
 *   <li>任务执行 → {@link WorkflowExecutorOps} 调用各系统任务的 {@code start()} 或 {@code execute()}</li>
 *   <li>持久化 → {@link ExecutionDAO}（由 {@link WorkflowExecutorOps} 调用）</li>
 * </ul>
 */
@Service
public class DeciderService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeciderService.class);

    private final IDGenerator idGenerator;
    private final ParametersUtils parametersUtils;
    private final SystemTaskRegistry systemTaskRegistry;
    private final Map<String, TaskMapper> taskMappers;

    public DeciderService(
            IDGenerator idGenerator,
            ParametersUtils parametersUtils,
            SystemTaskRegistry systemTaskRegistry,
            @Qualifier(IflowCoreConfiguration.TASK_MAPPERS_BY_TASK_TYPE)
                    Map<String, TaskMapper> taskMappers) {
        this.idGenerator = idGenerator;
        this.parametersUtils = parametersUtils;
        this.systemTaskRegistry = systemTaskRegistry;
        this.taskMappers = taskMappers;
        LOGGER.debug("Registered task mappers: {}", taskMappers.keySet());
    }

    public DeciderOutcome decide(WorkflowModel workflow) throws TerminateWorkflowException {

        final List<TaskModel> tasks = workflow.getTasks();
        List<TaskModel> unprocessedTasks =
                tasks.stream()
                        .filter(t -> !t.getStatus().equals(SKIPPED) && !t.isExecuted())
                        .toList();

        List<TaskModel> tasksToBeScheduled = new LinkedList<>();
        if (unprocessedTasks.isEmpty()) {
            // 获取首次调度工作流首次被调度的tasks
            tasksToBeScheduled = getInitialTasksToBeScheduled(workflow);
            if (tasksToBeScheduled == null) {
                tasksToBeScheduled = new LinkedList<>();
            }
        }

        return decide(workflow, tasksToBeScheduled);
    }

    /**
     *  根据当前workflowModel(工作流运行时)和WorkflowTask(task定义)生成task运行时
      */
    public List<TaskModel> getTasksToBeScheduled(
            WorkflowModel workflow, WorkflowTask taskToSchedule, int retryCount) {
        return getTasksToBeScheduled(workflow, taskToSchedule, retryCount, null, null);
    }

    public List<TaskModel> getTasksToBeScheduled(
            WorkflowModel workflow,
            WorkflowTask taskToSchedule,
            int retryCount,
            String retriedTaskId,
            String forkTaskId) {
        String type = taskToSchedule.getType();
        String taskId = idGenerator.generate();
        Map<String, Object> input =
                parametersUtils.getTaskInput(taskToSchedule.getInputParameters(), workflow, taskId);
        List<String> tasksInWorkflow =
                workflow.getTasks().stream()
                        .filter(
                                runningTask ->
                                        runningTask.getStatus().equals(TaskModel.Status.SCHEDULED)
                                                || runningTask.getStatus().equals(TaskModel.Status.IN_PROGRESS)
                                                || runningTask.getStatus().isTerminal())
                        .map(TaskModel::getReferenceTaskName)
                        .toList();

        // 构建运行时task上下文
        TaskMapperContext taskMapperContext =
                TaskMapperContext.builder()
                        .workflowModel(workflow)
                        .workflowTask(taskToSchedule)
                        .taskInput(input)
                        .retryCount(retryCount)
                        .retryTaskId(retriedTaskId)
                        .taskId(taskId)
                        .deciderService(this)
                        .forkTaskId(forkTaskId)
                        .build();

        // 静态/动态 FORK 的各分支结束后，都会尝试创建同一个 JOIN。
        // 展开 FORK 时已创建对应的 JOIN，通常处于 SCHEDULED 或 IN_PROGRESS，因此需要避免重复创建。
        // 按 referenceTaskName，过滤当前工作流中已处于 SCHEDULED、IN_PROGRESS 或终态的同名任务。
        // 当前正常执行路径中，主要在 JOIN 场景触发，也作为公共调度入口的去重保护。
        return taskMappers.getOrDefault(type, taskMappers.get(SPI.name()))
                        .getMappedTasks(taskMapperContext)
                        .stream()
                        .filter(task -> !tasksInWorkflow.contains(task.getReferenceTaskName()))
                        .collect(Collectors.toList());
    }


    void checkWorkflowTimeout(WorkflowModel workflow) {
        if (workflow == null || workflow.getStatus() == null || workflow.getStatus().isTerminal()) {
            return;
        }
        WorkflowDef definition = workflow.getWorkflowDefinition();
        if (definition == null || definition.getTimeoutSeconds() <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        long origin = workflow.getLastRetriedTime() > 0
                ? workflow.getLastRetriedTime() : workflow.getStartTime();
        long elapsedMillis = now - origin;
        long timeoutSeconds = definition.getTimeoutSeconds();
        // 用除法比较，避免超大秒数乘 1000 溢出。
        if (origin <= 0 || elapsedMillis < 0 || elapsedMillis / 1000 < timeoutSeconds) {
            return;
        }
        String reason = timeoutReason(workflow, elapsedMillis / 1000, timeoutSeconds);
        switch (definition.getTimeoutPolicy()) {
            case ALERT_ONLY:
                LOGGER.info("{} {}，超时策略为 ALERT_ONLY，工作流继续执行", workflow.getWorkflowId(), reason);
                return;
            case TIME_OUT_WF:
                throw new TerminateWorkflowException(reason, WorkflowModel.Status.TIMED_OUT);
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

    public boolean checkForWorkflowCompletion(WorkflowModel workflow) {
        List<TaskModel> tasks = workflow.getTasks();
        if (tasks.isEmpty()) {
            return false;
        }
        for (TaskModel task : tasks) {
            if (!task.getStatus().isTerminal()) {
                return false;
            }
            if (TaskType.TASK_TYPE_TERMINATE.equals(task.getTaskType())
                    && task.getStatus().isSuccessful()) {
                return true;
            }
        }

        Map<String, TaskModel.Status> statusByRef = new HashMap<>();
        for (TaskModel task : tasks) {
            statusByRef.put(task.getReferenceTaskName(), task.getStatus());
        }

        List<WorkflowTask> defTasks = workflow.getWorkflowDefinition().getTasks();
        if (defTasks == null || defTasks.isEmpty()) {
            return true;
        }
        for (WorkflowTask defTask : defTasks) {
            TaskModel.Status status = statusByRef.get(defTask.getTaskReferenceName());
            if (status == null || !status.isTerminal()) {
                return false;
            }
        }
        return true;
    }

    public void updateWorkflowOutput(WorkflowModel workflow, TaskModel task) {
        List<TaskModel> allTasks = workflow.getTasks();
        if (allTasks.isEmpty()) {
            return;
        }

        for (TaskModel t : allTasks) {
            if (TaskType.TASK_TYPE_TERMINATE.equals(t.getTaskType())
                    && t.getStatus().isTerminal()
                    && t.getStatus().isSuccessful()
                    && t.getInputData().containsKey(Terminate.INPUT_WORKFLOW_OUTPUT)) {
                workflow.setOutput(WorkflowExecutionMapper.copyOutput(t.getOutputData()));
                return;
            }
        }

        WorkflowDef workflowDef = workflow.getWorkflowDefinition();
        if (workflowDef.getOutputParameters() != null
                && !workflowDef.getOutputParameters().isEmpty()) {
            workflow.setOutput(
                    parametersUtils.getTaskInput(
                            workflowDef.getOutputParameters(), workflow, null));
            return;
        }

        TaskModel last = task != null ? task : allTasks.get(allTasks.size() - 1);
        workflow.setOutput(WorkflowExecutionMapper.copyOutput(last.getOutputData()));
    }

    List<TaskModel> getNextTask(WorkflowModel workflow, TaskModel task) {
        WorkflowDef workflowDef = workflow.getWorkflowDefinition();

        // SWITCH TASK在Mapper中已经完成分支任务加入运行时,SWITCH TASK任务本身不需要待出
        if (systemTaskRegistry.isSystemTask(task.getTaskType())
                && TaskType.TASK_TYPE_SWITCH.equals(task.getTaskType())
                && task.getInputData().get("hasChildren") != null) {
            return List.of();
        }

        String taskReferenceName =
                task.isLoopOverTask()
                        ? TaskUtils.removeIterationFromTaskRefName(task.getReferenceTaskName())
                        : task.getReferenceTaskName();
        WorkflowTask taskToSchedule = workflowDef.getNextTask(taskReferenceName);
        while (isTaskSkipped(taskToSchedule, workflow)) {
            taskToSchedule = workflowDef.getNextTask(taskToSchedule.getTaskReferenceName());
        }

        if (taskToSchedule != null && TaskType.TASK_TYPE_DO_WHILE.equals(taskToSchedule.getType())) {
            // 循环体最后一格走完时,结构后继是 DO_WHILE 自己,此时DO_WHILE自己的状态是IN_PROGRESS中不能再建一个
            String nextTaskReferenceName = taskToSchedule.getTaskReferenceName();
            if (workflow.getTasks().stream()
                    .anyMatch(
                            runningTask ->
                                    runningTask
                                            .getReferenceTaskName()
                                            .equals(nextTaskReferenceName))) {
                return Collections.emptyList();
            }
        }

        if (taskToSchedule != null) {
            return getTasksToBeScheduled(workflow, taskToSchedule, 0, null, task.getForkTaskId());
        }
        return Collections.emptyList();
    }

    /**
     * 单次决策：根据当前工作流运行时decide出待scheduled的task、待updated的task、更新工作流状态
     */
    private DeciderOutcome decide(WorkflowModel workflow, List<TaskModel> preScheduledTasks) {

        DeciderOutcome outcome = new DeciderOutcome();
        if (workflow.getStatus().isTerminal()) {
            return outcome;
        }

        // 工作流超时时间check
        checkWorkflowTimeout(workflow);

        // 1、收集当前工作流运行时
        // decide本轮pending的task
        List<TaskModel> pendingTasks = new ArrayList<>();
        // executed状态是task生命周期的终态
        Set<String> executedTaskRefNames = new HashSet<>();
        boolean hasSuccessfulTerminateTask = false;
        for (TaskModel task : workflow.getTasks()) {
            if (!task.isRetried() && task.getStatus() != SKIPPED && !task.isExecuted()) {
                pendingTasks.add(task);
            }

            if (task.isExecuted()) {
                executedTaskRefNames.add(task.getReferenceTaskName());
            }

            if (TERMINATE.name().equals(task.getTaskType())
                    && task.getStatus().isTerminal()
                    && task.getStatus().isSuccessful()) {
                hasSuccessfulTerminateTask = true;
                outcome.terminateTask = task;
            }
        }

        // 2、处理工作流运行时task->主要就是针对pending task的处理
        Map<String, TaskModel> tasksToBeScheduled = new LinkedHashMap<>();
        // preScheduledTasks就是首批/外部传入的待调度的task视为直接scheduled的task
        preScheduledTasks.forEach(
                t -> tasksToBeScheduled.put(t.getReferenceTaskName(), t));

        for (TaskModel pendingTask : pendingTasks) {
            if (systemTaskRegistry.isSystemTask(pendingTask.getTaskType())
                    && !pendingTask.getStatus().isTerminal()) {
                tasksToBeScheduled.putIfAbsent(pendingTask.getReferenceTaskName(), pendingTask);
                executedTaskRefNames.remove(pendingTask.getReferenceTaskName());
            }

            // todo task的超时&重试处理
            if (pendingTask.getStatus().isTerminal() && !pendingTask.getStatus().isSuccessful()) {
                handleFailedTask(workflow, pendingTask);
            }

            // pendingTask终态处理并获取该task下一轮需要调度的tasks
            if (!pendingTask.isExecuted()
                    && !pendingTask.isRetried()
                    && pendingTask.getStatus().isTerminal()) {
                pendingTask.setExecuted(true);
                List<TaskModel> nextTasks = getNextTask(workflow, pendingTask);
                if (pendingTask.isLoopOverTask()
                        && !TaskType.DO_WHILE.name().equals(pendingTask.getTaskType())
                        && !nextTasks.isEmpty()) {
                    nextTasks = filterNextLoopOverTasks(nextTasks, pendingTask, workflow);
                }

                nextTasks.forEach(
                        next ->
                                tasksToBeScheduled.putIfAbsent(
                                        next.getReferenceTaskName(), next));
                outcome.tasksToBeUpdated.add(pendingTask);
                LOGGER.debug(
                        "Scheduling Tasks from {}, next = {} for workflowId: {}",
                        pendingTask.getTaskDefName(),
                        nextTasks.stream()
                                .map(TaskModel::getReferenceTaskName)
                                .toList(),
                        workflow.getWorkflowId());
            }
        }

        // 3、decide收尾确定待调度、待更新、工作流状态
        List<TaskModel> unScheduledTasks =
                tasksToBeScheduled.values().stream()
                        .filter(task -> !executedTaskRefNames.contains(task.getReferenceTaskName()))
                        .toList();
        if (!unScheduledTasks.isEmpty()) {
            LOGGER.debug(
                    "Scheduling Tasks: {} for workflow: {}",
                    unScheduledTasks.stream()
                            .map(TaskModel::getTaskDefName)
                            .collect(Collectors.toList()),
                    workflow.getWorkflowId());
            outcome.tasksToBeScheduled.addAll(unScheduledTasks);
        }

        if (hasSuccessfulTerminateTask
                || (outcome.tasksToBeScheduled.isEmpty()
                        && checkForWorkflowCompletion(workflow))) {
            outcome.isComplete = true;
        }

        return outcome;
    }

    List<TaskModel> filterNextLoopOverTasks(
            List<TaskModel> tasks, TaskModel pendingTask, WorkflowModel workflow) {

        // DO WHILE循环体内的任务需要加上__迭代号
        tasks.forEach(
                nextTask -> {
                    nextTask.setReferenceTaskName(
                            TaskUtils.appendIteration(
                                    nextTask.getReferenceTaskName(), pendingTask.getIteration()));
                    nextTask.setIteration(pendingTask.getIteration());
                });

        List<String> tasksInWorkflow =
                workflow.getTasks().stream()
                        .filter(
                                runningTask ->
                                        runningTask.getStatus().equals(TaskModel.Status.SCHEDULED)
                                                || runningTask.getStatus().equals(TaskModel.Status.IN_PROGRESS)
                                                || runningTask.getStatus().isTerminal())
                        .map(TaskModel::getReferenceTaskName)
                        .toList();

        return tasks.stream()
                .filter(
                        runningTask ->
                                !tasksInWorkflow.contains(runningTask.getReferenceTaskName()))
                .collect(Collectors.toList());
    }


    private List<TaskModel> getInitialTasksToBeScheduled(WorkflowModel workflow) {
        final WorkflowDef workflowDef = workflow.getWorkflowDefinition();

        LOGGER.debug("Starting workflow: {}", workflow);

        List<TaskModel> tasks = workflow.getTasks();
        // 运行时task为空表示是一个新的工作流运行时
        if (tasks.isEmpty()) {
            if (workflowDef.getTasks().isEmpty()) {
                throw new TerminateWorkflowException(
                        "No tasks found to be executed");
            }

            WorkflowTask taskToSchedule = workflowDef.getTasks().get(0);

            // Loop until a non-skipped task is found
            while (isTaskSkipped(taskToSchedule, workflow)) {
                taskToSchedule = workflowDef.getNextTask(taskToSchedule.getTaskReferenceName());
                if (taskToSchedule == null) {
                    throw new TerminateWorkflowException("All tasks are skipped");
                }
            }

            return getTasksToBeScheduled(workflow, taskToSchedule, 0);
        }

        return Collections.emptyList();
    }

    private boolean isTaskSkipped(WorkflowTask taskToSchedule, WorkflowModel workflow) {
        if (taskToSchedule == null) {
            return false;
        }
        TaskModel existing = workflow.getTaskByRefName(taskToSchedule.getTaskReferenceName());
        return existing != null && SKIPPED.equals(existing.getStatus());
    }

    /**
     * 对齐 Conductor：必选任务失败且无法重试时终止工作流；
     * optional 转为 COMPLETED_WITH_ERRORS 后继续；permissive 保留 FAILED 交由 JOIN 处理。
     */
    private void handleFailedTask(WorkflowModel workflow, TaskModel pendingTask) {
        WorkflowTask def = pendingTask.getWorkflowTask();
        if (def != null && def.isOptional()) {
            pendingTask.setStatus(TaskModel.Status.COMPLETED_WITH_ERRORS);
            return;
        }
        if (def != null && def.isPermissive()) {
            // permissive 非 optional：保持 FAILED，由 JOIN 统一裁决
            return;
        }

        updateWorkflowOutput(workflow, pendingTask);
        String reason =
                String.format(
                        "Task %s failed with status: %s and reason: '%s'",
                        pendingTask.getTaskId(),
                        pendingTask.getStatus(),
                        pendingTask.getReasonForInCompletion());
        throw new TerminateWorkflowException(reason, WorkflowModel.Status.FAILED, pendingTask);
    }

    /**
     * Decider 单次评估结果
     */
    public static class DeciderOutcome {

        List<TaskModel> tasksToBeScheduled = new LinkedList<>();
        List<TaskModel> tasksToBeUpdated = new LinkedList<>();
        boolean isComplete;
        TaskModel terminateTask;

        private DeciderOutcome() {}
    }
}
