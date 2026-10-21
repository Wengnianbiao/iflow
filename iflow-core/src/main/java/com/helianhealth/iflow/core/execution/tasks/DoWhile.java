package com.helianhealth.iflow.core.execution.tasks;

import com.helianhealth.iflow.common.domain.metadata.task.TaskType;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowTask;
import com.helianhealth.iflow.core.dal.ExecutionDAOFacade;
import com.helianhealth.iflow.core.execution.WorkflowExecutor;
import com.helianhealth.iflow.core.execution.script.ScriptEvaluator;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import com.helianhealth.iflow.core.utils.ParametersUtils;
import com.helianhealth.iflow.core.utils.TaskUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 循环任务。
 *
 * <p>首次执行先生成并注册第 1 轮循环体入口任务，由 Decider 统一调度并推进；每一轮循环体全部结束后求值
 * {@link WorkflowTask#getLoopCondition()}。配置 loopItems 时遍历进入时确定的数组快照，
 * 两种配置互斥，空数组执行零轮：
 * <ul>
 *   <li>返回真值 —— 更新迭代号，生成并登记下一轮循环体入口任务；</li>
 *   <li>返回假值 —— 本 DO_WHILE 完成。</li>
 * </ul>
 */
@Component(TaskType.TASK_TYPE_DO_WHILE)
public class DoWhile extends WorkflowSystemTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(DoWhile.class);

    public static final String OUTPUT_ITERATION = "iteration";
    private final ParametersUtils parametersUtils;
    private final ExecutionDAOFacade executionDAOFacade;

    public DoWhile(
            ParametersUtils parametersUtils,
            ExecutionDAOFacade executionDAOFacade) {
        super(TaskType.TASK_TYPE_DO_WHILE);
        this.parametersUtils = parametersUtils;
        this.executionDAOFacade = executionDAOFacade;
    }

    @Override
    public void cancel(WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        task.setStatus(TaskModel.Status.CANCELED);
    }

    @Override
    public boolean execute(
            WorkflowModel workflow, TaskModel doWhileTaskModel, WorkflowExecutor workflowExecutor) {

        boolean hasFailures = false;
        StringBuilder failureReason = new StringBuilder();
        Map<String, Object> output = new HashMap<>();

        Map<String, TaskModel> relevantTasks = new LinkedHashMap<>();
        TaskModel relevantTask;
        // 收集当前doWhile轮次中处于运行时的子任务(并且只取the highest retry count task)
        for (TaskModel t : workflow.getTasks()) {
            if (doWhileTaskModel
                    .getWorkflowTask()
                    .has(TaskUtils.removeIterationFromTaskRefName(t.getReferenceTaskName()))
                    && !doWhileTaskModel.getReferenceTaskName().equals(t.getReferenceTaskName())
                    && doWhileTaskModel.getIteration() == t.getIteration()) {
                relevantTask = relevantTasks.get(t.getReferenceTaskName());
                if (relevantTask == null || t.getRetryCount() > relevantTask.getRetryCount()) {
                    relevantTasks.put(t.getReferenceTaskName(), t);
                }
            }
        }
        Collection<TaskModel> loopOverTasks = relevantTasks.values();
        LOGGER.debug(
                "Workflow {} waiting for tasks {} to complete DO_WHILE iteration {}",
                workflow.getWorkflowId(),
                relevantTasks.keySet(),
                doWhileTaskModel.getIteration());

        if (loopOverTasks.isEmpty()) {
            // For list iteration, check if the items list is empty before creating iteration 1.
            // An empty items list means there is nothing to iterate over, so complete immediately.
            if (isListIteration(doWhileTaskModel)) {
                List<Object> itemsList;
                try {
                    itemsList = evaluateItemsList(workflow, doWhileTaskModel);
                } catch (RuntimeException error) {
                    markTaskFailure(
                            doWhileTaskModel,
                            TaskModel.Status.FAILED_WITH_TERMINAL_ERROR,
                            "Unable to evaluate DO_WHILE loopItems: " + rootCauseMessage(error));
                    return true;
                }
                if (itemsList.isEmpty()) {
                    LOGGER.debug(
                            "Task {} has an empty items list, completing without executing loop tasks",
                            doWhileTaskModel.getTaskId());
                    doWhileTaskModel.addOutput("iteration", 0);
                    markTaskSuccess(doWhileTaskModel);
                    return true;
                }
            }

            doWhileTaskModel.setIteration(1);
            doWhileTaskModel.addOutput("iteration", doWhileTaskModel.getIteration());

            // For list iteration, inject loopItem and loopIndex
            injectLoopVariables(workflow, doWhileTaskModel);
            // DO_WHILE 只推进循环状态，注册本轮入口任务，由下一轮 Decider 统一调度。
            // 避免在 FORK 线程池内递归调度并同步等待循环体，造成线程池饥饿死锁。
            workflowExecutor.addNextIterationTasks(doWhileTaskModel, workflow);
            return true;
        }

        for (TaskModel loopOverTask : loopOverTasks) {
            TaskModel.Status taskStatus = loopOverTask.getStatus();
            hasFailures = taskStatus != null && taskStatus.isTerminal() && !taskStatus.isSuccessful();
            if (hasFailures) {
                failureReason.append(loopOverTask.getReasonForInCompletion()).append(" ");
            }
            output.put(
                    TaskUtils.removeIterationFromTaskRefName(loopOverTask.getReferenceTaskName()),
                    loopOverTask.getOutputData());
            if (hasFailures) {
                break;
            }
        }
        doWhileTaskModel.addOutput(String.valueOf(doWhileTaskModel.getIteration()), output);

        Integer keepLastN = getKeepLastN(doWhileTaskModel);
        if (keepLastN != null && doWhileTaskModel.getIteration() > keepLastN) {
            int iteration = doWhileTaskModel.getIteration();
            for (int oldIteration = 1;
                    oldIteration <= iteration - keepLastN;
                    oldIteration++) {
                doWhileTaskModel.outputAsMap().remove(String.valueOf(oldIteration));
            }

            // Remove old iteration tasks from the database
            removeIterations(workflow, doWhileTaskModel, keepLastN);
        }

        if (hasFailures) {
            LOGGER.debug(
                    "Task {} failed in {} iteration",
                    doWhileTaskModel.getTaskId(),
                    doWhileTaskModel.getIteration());
            markTaskFailure(
                    doWhileTaskModel, TaskModel.Status.FAILED, failureReason.toString());
            return true;
        }

        if (!isIterationComplete(doWhileTaskModel, relevantTasks)) {
            // current iteration is not complete (all tasks inside the loop are not terminal)
            return false;
        }

        // if we are here, the iteration is complete, and we need to check if there is a next
        // iteration by evaluating the loopCondition
        boolean shouldContinue;
        try {
            shouldContinue = evaluateCondition(workflow, doWhileTaskModel, loopOverTasks);
            LOGGER.debug(
                    "Task {} condition evaluated to {}",
                    doWhileTaskModel.getTaskId(),
                    shouldContinue);
            if (shouldContinue) {
                doWhileTaskModel.setIteration(doWhileTaskModel.getIteration() + 1);
                doWhileTaskModel.addOutput("iteration", doWhileTaskModel.getIteration());

                // For list iteration, inject loopItem and loopIndex for next iteration
                injectLoopVariables(workflow, doWhileTaskModel);

                workflowExecutor.addNextIterationTasks(doWhileTaskModel, workflow);
            } else {
                LOGGER.debug(
                        "Task {} took {} iterations to complete",
                        doWhileTaskModel.getTaskId(),
                        doWhileTaskModel.getIteration());
                markTaskSuccess(doWhileTaskModel);
            }
        } catch (Exception e) {
            String message =
                    String.format(
                            "Unable to evaluate condition %s, exception %s",
                            doWhileTaskModel.getWorkflowTask().getLoopCondition(),
                            rootCauseMessage(e));
            LOGGER.error(message, e);
            markTaskFailure(
                    doWhileTaskModel, TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, message);
        }
        return true;
    }

    private void removeIterations(
            WorkflowModel workflow, TaskModel doWhileTaskModel, int keepLastN) {
        int oldestIterationToKeep = doWhileTaskModel.getIteration() - keepLastN + 1;
        if (oldestIterationToKeep <= 1) {
            return;
        }

        List<TaskModel> tasksToRemove =
                workflow.getTasks().stream()
                        .filter(task -> task.getIteration() > 0)
                        .filter(task -> task.getIteration() < oldestIterationToKeep)
                        .filter(
                                task ->
                                        doWhileTaskModel
                                                .getWorkflowTask()
                                                .has(
                                                        TaskUtils.removeIterationFromTaskRefName(
                                                                task.getReferenceTaskName())))
                        .toList();

        for (TaskModel task : tasksToRemove) {
            try {
                executionDAOFacade.removeTask(task.getTaskId());
            } catch (RuntimeException e) {
                LOGGER.warn(
                        "Unable to remove retained DO_WHILE task {} from workflow {}",
                        task.getTaskId(),
                        workflow.getWorkflowId(),
                        e);
            }
        }
        workflow.getTasks().removeAll(tasksToRemove);
    }

    private Integer getKeepLastN(TaskModel task) {
        Map<String, Object> inputParameters = task.getWorkflowTask().getInputParameters();
        if (inputParameters == null) {
            return null;
        }
        Object configured = inputParameters.get("keepLastN");
        if (configured == null) {
            return null;
        }
        int value;
        if (configured instanceof Number number) {
            value = number.intValue();
        } else {
            try {
                value = Integer.parseInt(String.valueOf(configured));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "DO_WHILE keepLastN must be a positive integer", e);
            }
        }
        if (value <= 0) {
            throw new IllegalArgumentException("DO_WHILE keepLastN must be a positive integer");
        }
        return value;
    }

    private boolean isIterationComplete(
            TaskModel doWhileTask, Map<String, TaskModel> relevantTasks) {
        int iteration = doWhileTask.getIteration();
        for (WorkflowTask loopTask : doWhileTask.getWorkflowTask().getLoopOver()) {
            TaskModel runtimeTask =
                    relevantTasks.get(
                            TaskUtils.appendIteration(
                                    loopTask.getTaskReferenceName(), iteration));
            if (runtimeTask == null
                    || runtimeTask.getStatus() == null
                    || !runtimeTask.getStatus().isTerminal()) {
                return false;
            }
        }

        if (relevantTasks.values().stream()
                .anyMatch(t -> t.getStatus() == null || !t.getStatus().isTerminal())) {
            return false;
        }

        String doWhileRef = doWhileTask.getReferenceTaskName();
        for (TaskModel runtimeTask : relevantTasks.values()) {
            String refWithoutIteration =
                    TaskUtils.removeIterationFromTaskRefName(runtimeTask.getReferenceTaskName());
            WorkflowTask next =
                    doWhileTask.getWorkflowTask().next(refWithoutIteration, null);
            if (next == null
                    || doWhileRef.equals(next.getTaskReferenceName())
                    || !doWhileTask.getWorkflowTask().has(next.getTaskReferenceName())) {
                continue;
            }
            String nextRef =
                    TaskUtils.appendIteration(next.getTaskReferenceName(), iteration);
            if (!relevantTasks.containsKey(nextRef)) {
                return false;
            }
        }
        return true;
    }

    private void markTaskFailure(
            TaskModel task, TaskModel.Status status, String reason) {
        task.setReasonForInCompletion(reason);
        task.setStatus(status);
    }

    private void markTaskSuccess(TaskModel task) {
        task.setStatus(TaskModel.Status.COMPLETED);
    }

    private void injectLoopVariables(WorkflowModel workflow, TaskModel task) {
        if (!isListIteration(task)) {
            return;
        }
        List<Object> items = evaluateItemsList(workflow, task);
        int loopIndex = task.getIteration() - 1;
        task.addOutput("loopIndex", loopIndex);
        if (loopIndex >= 0 && loopIndex < items.size()) {
            task.addOutput("loopItem", ScriptEvaluator.deepCopy(items.get(loopIndex)));
        }
    }

    private boolean isListIteration(TaskModel task) {
        return task.getWorkflowTask().getLoopItems() != null;
    }

    @SuppressWarnings("unchecked")
    private List<Object> evaluateItemsList(WorkflowModel workflow, TaskModel task) {
        Object itemsValue = null;
        String itemsParam = task.getWorkflowTask().getLoopItems();
        if (itemsParam != null && !itemsParam.trim().isEmpty()) {
            Map<String, Object> tempInputParams = new HashMap<>();
            tempInputParams.put("loopItems", itemsParam);
            Map<String, Object> evaluatedParams =
                    parametersUtils.getTaskInput(
                            tempInputParams, workflow, task.getTaskId());

            itemsValue = evaluatedParams.get("loopItems");
        }

        // Convert itemsValue to List<Object>
        if (itemsValue instanceof List) {
            return (List<Object>) itemsValue;
        } else if (itemsValue instanceof Collection) {
            return new ArrayList<>((Collection<?>) itemsValue);
        } else if (itemsValue instanceof Object[]) {
            return Arrays.asList((Object[]) itemsValue);
        } else if (itemsValue != null) {
            // If it's a single value, wrap it in a list
            return Collections.singletonList(itemsValue);
        }

        return Collections.emptyList();
    }

    private boolean evaluateCondition(
            WorkflowModel workflow, TaskModel task, Collection<TaskModel> loopTasks) {
        WorkflowTask def = task.getWorkflowTask();
        if (isListIteration(task)) {
            return task.getIteration() < evaluateItemsList(workflow, task).size();
        }
        Map<String, Object> ctx = new HashMap<>(task.getInputData());
        ctx.putAll(
                parametersUtils.getTaskInput(
                        def.getInputParameters(), workflow, task.getTaskId()));
        ctx.put(OUTPUT_ITERATION, task.getIteration());
        ctx.put(task.getReferenceTaskName(), task.getOutputData());
        for (TaskModel loopTask : loopTasks) {
            ctx.put(
                    TaskUtils.removeIterationFromTaskRefName(loopTask.getReferenceTaskName()),
                    loopTask.getOutputData());
        }
        ctx.put("workflow", workflowContext(workflow));

        return ScriptEvaluator.evalBool(def.getLoopCondition(), ctx);
    }

    private Map<String, Object> workflowContext(WorkflowModel workflow) {
        Map<String, Object> workflowContext = new HashMap<>();
        workflowContext.put("input", workflow.getInput());
        workflowContext.put("output", workflow.getOutput());
        workflowContext.put("status", workflow.getStatus());
        return workflowContext;
    }

    private String rootCauseMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getName() : current.getMessage();
    }
}
