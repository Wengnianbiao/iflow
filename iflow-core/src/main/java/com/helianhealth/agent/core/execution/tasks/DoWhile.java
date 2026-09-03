package com.helianhealth.agent.core.execution.tasks;

import com.helianhealth.agent.common.domain.metadata.task.TaskType;
import com.helianhealth.agent.common.domain.metadata.workflow.WorkflowTask;
import com.helianhealth.agent.core.dal.ExecutionDAOFacade;
import com.helianhealth.agent.core.execution.WorkflowExecutor;
import com.helianhealth.agent.core.execution.evaluators.Evaluator;
import com.helianhealth.agent.core.model.TaskModel;
import com.helianhealth.agent.core.model.WorkflowModel;
import com.helianhealth.agent.core.utils.ParametersUtils;
import com.helianhealth.agent.core.utils.TaskUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
 * <p>首次执行先调度第 1 轮循环体；每一轮循环体全部结束后求值
 * {@link WorkflowTask#getLoopCondition()}：
 * <ul>
 *   <li>返回真值 —— 更新迭代号，由下一轮主线程 Decider 创建循环体任务；</li>
 *   <li>返回假值 —— 本 DO_WHILE 完成。</li>
 * </ul>
 */
@Component(TaskType.TASK_TYPE_DO_WHILE)
public class DoWhile extends WorkflowSystemTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(DoWhile.class);

    public static final String OUTPUT_ITERATION = "iteration";

    private final Map<String, Evaluator> evaluators;
    private final ParametersUtils parametersUtils;
    private final ExecutionDAOFacade executionDAOFacade;

    public DoWhile(Map<String, Evaluator> evaluators, ParametersUtils parametersUtils) {
        this(evaluators, parametersUtils, null);
    }

    @Autowired
    public DoWhile(
            Map<String, Evaluator> evaluators,
            ParametersUtils parametersUtils,
            @Autowired(required = false) ExecutionDAOFacade executionDAOFacade) {
        super(TaskType.TASK_TYPE_DO_WHILE);
        this.evaluators = evaluators;
        this.parametersUtils = parametersUtils;
        this.executionDAOFacade = executionDAOFacade;
    }

    @Override
    public void cancel(WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        task.setStatus(TaskModel.Status.CANCELED);
    }

    @Override
    public void execute(
            WorkflowModel workflow,
            TaskModel doWhileTaskModel,
            WorkflowExecutor workflowExecutor) {
        WorkflowTask definition = doWhileTaskModel.getWorkflowTask();
        if (definition == null
                || definition.getLoopOver() == null
                || definition.getLoopOver().isEmpty()) {
            markTaskFailure(
                    doWhileTaskModel,
                    TaskModel.Status.FAILED_WITH_TERMINAL_ERROR,
                    "DO_WHILE loopOver must contain at least one task");
            return;
        }
        if (!isListIteration(doWhileTaskModel)
                && (definition.getLoopCondition() == null
                        || definition.getLoopCondition().isBlank())) {
            markTaskFailure(
                    doWhileTaskModel,
                    TaskModel.Status.FAILED_WITH_TERMINAL_ERROR,
                    "DO_WHILE loopCondition is required");
            return;
        }

        boolean hasFailures = false;
        StringBuilder failureReason = new StringBuilder();
        Map<String, Object> output = new HashMap<>();

        Map<String, TaskModel> relevantTasks = new LinkedHashMap<>();
        TaskModel relevantTask;
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
            // For list iteration, check if the items list is empty before scheduling iteration 1.
            // An empty items list means there is nothing to iterate over, so complete immediately.
            if (isListIteration(doWhileTaskModel)) {
                List<Object> itemsList = evaluateItemsList(workflow, doWhileTaskModel);
                if (itemsList.isEmpty()) {
                    LOGGER.debug(
                            "Task {} has an empty items list, completing without executing loop tasks",
                            doWhileTaskModel.getTaskId());
                    doWhileTaskModel.addOutput("iteration", 0);
                    markTaskSuccess(doWhileTaskModel);
                    return;
                }
            }

            doWhileTaskModel.setIteration(1);
            doWhileTaskModel.addOutput("iteration", doWhileTaskModel.getIteration());

            // For list iteration, inject loopItem and loopIndex
            injectLoopVariables(workflow, doWhileTaskModel);

            return;
        }

        for (TaskModel loopOverTask : loopOverTasks) {
            TaskModel.Status taskStatus = loopOverTask.getStatus();
            hasFailures = !taskStatus.isSuccessful();
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
                doWhileTaskModel.getOutputData().remove(String.valueOf(oldIteration));
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
            return;
        }

        if (!isIterationComplete(doWhileTaskModel, relevantTasks)) {
            // current iteration is not complete (all tasks inside the loop are not terminal)
            return;
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

                return;
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

        if (executionDAOFacade != null) {
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
            task.addOutput("loopItem", items.get(loopIndex));
        }
    }

    private boolean isListIteration(TaskModel task) {
        WorkflowTask definition = task.getWorkflowTask();
        if (definition == null) {
            return false;
        }
        if (definition.getItems() != null && !definition.getItems().isBlank()) {
            return true;
        }
        Map<String, Object> inputParameters = definition.getInputParameters();
        if (inputParameters == null) {
            return false;
        }
        Object items = inputParameters.get("_items");
        return items instanceof Collection<?>
                || items instanceof Object[]
                || (items instanceof String value && !value.isBlank());
    }

    private List<Object> evaluateItemsList(WorkflowModel workflow, TaskModel task) {
        Object items = null;
        String itemsExpression = task.getWorkflowTask().getItems();
        if (itemsExpression != null && !itemsExpression.isBlank()) {
            items =
                    parametersUtils
                            .getTaskInput(
                                    Map.of("items", itemsExpression), workflow, task.getTaskId())
                            .get("items");
        }
        if (items == null && task.getWorkflowTask().getInputParameters() != null) {
            items =
                    parametersUtils
                            .getTaskInput(
                                    task.getWorkflowTask().getInputParameters(),
                                    workflow,
                                    task.getTaskId())
                            .get("_items");
        }

        if (items instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        if (items instanceof Collection<?> collection) {
            return new ArrayList<>(collection);
        }
        if (items instanceof Object[] array) {
            return new ArrayList<>(Arrays.asList(array));
        }
        return items == null ? Collections.emptyList() : List.of(items);
    }

    private boolean evaluateCondition(
            WorkflowModel workflow, TaskModel task, Collection<TaskModel> loopTasks) {
        WorkflowTask def = task.getWorkflowTask();
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

        if (isListIteration(task)) {
            List<Object> items = evaluateItemsList(workflow, task);
            int loopIndex = task.getIteration() - 1;
            ctx.put("loopIndex", loopIndex);
            if (loopIndex >= 0 && loopIndex < items.size()) {
                ctx.put("loopItem", items.get(loopIndex));
            }
            boolean hasMoreItems = loopIndex < items.size() - 1;
            if (def.getLoopCondition() == null || def.getLoopCondition().isBlank()) {
                return hasMoreItems;
            }
            return evaluate(def, ctx) && hasMoreItems;
        }

        return evaluate(def, ctx);
    }

    private boolean evaluate(WorkflowTask definition, Map<String, Object> context) {
        String evaluatorType = definition.getEvaluatorType();
        if (evaluatorType == null || evaluatorType.isBlank()) {
            Object configured = context.get(Inline.INPUT_EVALUATOR_TYPE);
            evaluatorType =
                    configured == null ? Inline.DEFAULT_EVALUATOR : String.valueOf(configured);
        }
        Evaluator evaluator = evaluators.get(evaluatorType);
        if (evaluator == null) {
            throw new IllegalArgumentException("unknown evaluatorType: " + evaluatorType);
        }
        return toBoolean(evaluator.evaluate(definition.getLoopCondition(), context));
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

    private boolean toBoolean(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof Number n) {
            return n.doubleValue() != 0;
        }
        return Boolean.parseBoolean(value.toString());
    }
}
