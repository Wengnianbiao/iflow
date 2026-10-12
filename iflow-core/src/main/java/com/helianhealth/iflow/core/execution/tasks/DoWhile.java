package com.helianhealth.iflow.core.execution.tasks;

import com.helianhealth.iflow.common.domain.metadata.task.TaskType;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowTask;
import com.helianhealth.iflow.core.dal.ExecutionDAOFacade;
import com.helianhealth.iflow.core.execution.WorkflowExecutor;
import com.helianhealth.iflow.core.execution.evaluators.GraalJSEvaluator;
import com.helianhealth.iflow.core.execution.script.ScriptEvaluator;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import com.helianhealth.iflow.core.utils.ParametersUtils;
import com.helianhealth.iflow.core.utils.TaskUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 循环任务。
 *
 * <p>首次执行先调度第 1 轮循环体；每一轮循环体全部结束后求值
 * {@link WorkflowTask#getLoopCondition()}。配置 loopItems 时遍历进入时确定的数组快照，
 * 两种配置互斥，空数组执行零轮：
 * <ul>
 *   <li>返回真值 —— 更新迭代号，调用执行器启动下一轮循环体；</li>
 *   <li>返回假值 —— 本 DO_WHILE 完成。</li>
 * </ul>
 */
@Component(TaskType.TASK_TYPE_DO_WHILE)
public class DoWhile extends WorkflowSystemTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(DoWhile.class);

    public static final String OUTPUT_ITERATION = "iteration";
    public static final String INPUT_LOOP_ITEMS = "loopItems";
    private static final Pattern COLLECTION_EXPRESSION = Pattern.compile("^\\$\\{[^{}]+}$");

    private final GraalJSEvaluator evaluator;
    private final ParametersUtils parametersUtils;
    private final ExecutionDAOFacade executionDAOFacade;

    public DoWhile(
            GraalJSEvaluator evaluator,
            ParametersUtils parametersUtils,
            ExecutionDAOFacade executionDAOFacade) {
        super(TaskType.TASK_TYPE_DO_WHILE);
        this.evaluator = Objects.requireNonNull(evaluator);
        this.parametersUtils = Objects.requireNonNull(parametersUtils);
        this.executionDAOFacade = Objects.requireNonNull(executionDAOFacade);
    }

    /** 定义写入与执行共用的配置校验，不解析动态数组来源。 */
    public static void validateDefinition(WorkflowTask definition) {
        if (definition == null) {
            throw new IllegalArgumentException("DO_WHILE task definition is required");
        }
        if (definition.getLoopOver() == null || definition.getLoopOver().isEmpty()) {
            throw new IllegalArgumentException("DO_WHILE loopOver must contain at least one task");
        }
        boolean hasItems = definition.getLoopItems() != null;
        boolean hasCondition = definition.getLoopCondition() != null;
        if (hasItems == hasCondition) {
            throw new IllegalArgumentException("DO_WHILE requires exactly one of loopItems or loopCondition");
        }
        if (hasItems) {
            String expression = definition.getLoopItems().trim();
            if (!COLLECTION_EXPRESSION.matcher(expression).matches()
                    || expression.substring(2, expression.length() - 1).isBlank()) {
                throw new IllegalArgumentException("DO_WHILE loopItems must be a complete ${...} expression");
            }
        } else if (definition.getLoopCondition().isBlank()) {
            throw new IllegalArgumentException("DO_WHILE loopCondition must not be blank");
        }
        String evaluatorType = definition.getEvaluatorType();
        if (evaluatorType != null && !evaluatorType.isBlank()
                && !GraalJSEvaluator.NAME.equals(evaluatorType)) {
            throw new IllegalArgumentException("DO_WHILE only supports the graaljs evaluator");
        }
        if (definition.getInputParameters() != null
                && definition.getInputParameters().containsKey(INPUT_LOOP_ITEMS)) {
            throw new IllegalArgumentException("DO_WHILE inputParameters.loopItems is reserved for the array snapshot");
        }
    }

    @Override
    public void cancel(WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        task.setStatus(TaskModel.Status.CANCELED);
    }

    @Override
    public void execute(
            WorkflowModel workflow, TaskModel doWhileTaskModel, WorkflowExecutor workflowExecutor) {
        try {
            validateDefinition(doWhileTaskModel.getWorkflowTask());
        } catch (IllegalArgumentException error) {
            markTaskFailure(doWhileTaskModel, TaskModel.Status.FAILED_WITH_TERMINAL_ERROR, error.getMessage());
            return;
        }

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
            // For list iteration, check if the items list is empty before scheduling iteration 1.
            // An empty items list means there is nothing to iterate over, so complete immediately.
            if (isListIteration(doWhileTaskModel)) {
                List<Object> itemsList;
                try {
                    itemsList = itemsSnapshot(workflow, doWhileTaskModel);
                } catch (RuntimeException error) {
                    markTaskFailure(
                            doWhileTaskModel,
                            TaskModel.Status.FAILED_WITH_TERMINAL_ERROR,
                            "Unable to evaluate DO_WHILE loopItems: " + rootCauseMessage(error));
                    return;
                }
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

            workflowExecutor.scheduleNextIteration(doWhileTaskModel, workflow);
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

                workflowExecutor.scheduleNextIteration(doWhileTaskModel, workflow);
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
        List<Object> items = itemsSnapshot(workflow, task);
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
    private List<Object> itemsSnapshot(WorkflowModel workflow, TaskModel task) {
        if (task.getInputData().containsKey(INPUT_LOOP_ITEMS)) {
            Object snapshot = task.getInputData().get(INPUT_LOOP_ITEMS);
            if (snapshot instanceof List<?> list) {
                return (List<Object>) list;
            }
            throw new IllegalArgumentException("DO_WHILE loopItems snapshot must be an array");
        }
        Object resolved = parametersUtils.getTaskInput(
                Map.of(INPUT_LOOP_ITEMS, task.getWorkflowTask().getLoopItems().trim()),
                workflow, task.getTaskId()).get(INPUT_LOOP_ITEMS);
        List<?> items;
        if (resolved instanceof Collection<?> collection) {
            items = new ArrayList<>(collection);
        } else if (resolved != null && resolved.getClass().isArray()) {
            List<Object> array = new ArrayList<>();
            for (int index = 0; index < java.lang.reflect.Array.getLength(resolved); index++) {
                array.add(java.lang.reflect.Array.get(resolved, index));
            }
            items = array;
        } else {
            throw new IllegalArgumentException("DO_WHILE loopItems must resolve to an array or collection, got "
                    + (resolved == null ? "null" : resolved.getClass().getSimpleName()));
        }
        List<Object> snapshot = (List<Object>) ScriptEvaluator.deepCopy(items);
        task.getInputData().put(INPUT_LOOP_ITEMS, snapshot);
        return snapshot;
    }

    private boolean evaluateCondition(
            WorkflowModel workflow, TaskModel task, Collection<TaskModel> loopTasks) {
        WorkflowTask def = task.getWorkflowTask();
        if (isListIteration(task)) {
            return task.getIteration() < itemsSnapshot(workflow, task).size();
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

        return toBoolean(evaluator.evaluate(def.getLoopCondition(), ctx));
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
