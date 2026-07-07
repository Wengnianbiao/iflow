package com.helianhealth.agent.core.execution.mapper;

import com.helianhealth.agent.common.domain.metadata.task.TaskType;
import com.helianhealth.agent.core.exception.TerminateWorkflowException;
import com.helianhealth.agent.core.execution.evaluators.Evaluator;
import com.helianhealth.agent.core.execution.evaluators.ValueParamEvaluator;
import com.helianhealth.agent.core.model.TaskModel;
import com.helianhealth.agent.core.utils.ParametersUtils;
import org.springframework.stereotype.Component;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * SWITCH 任务 Mapper。
 */
@Component
public class SwitchTaskMapper implements TaskMapper {

    private static final String DIRECT_VALUE_KEY = "__switchDirectValue";

    private final Map<String, Evaluator> evaluators;
    private final ParametersUtils parametersUtils;

    public SwitchTaskMapper(Map<String, Evaluator> evaluators, ParametersUtils parametersUtils) {
        this.evaluators = evaluators;
        this.parametersUtils = parametersUtils;
    }

    @Override
    public String getTaskType() {
        return TaskType.TASK_TYPE_SWITCH;
    }

    @Override
    public List<TaskModel> getMappedTasks(TaskMapperContext taskMapperContext) {
        List<TaskModel> tasksToBeScheduled = new LinkedList<>();
        var workflowTask = taskMapperContext.getWorkflowTask();
        Map<String, Object> taskInput = requireTaskInput(taskMapperContext);

        String evaluatorType = workflowTask.getEvaluatorType();
        Evaluator evaluator = evaluators.get(evaluatorType);
        if (evaluator == null) {
            throw new TerminateWorkflowException(
                    "No evaluator registered for type: " + evaluatorType);
        }

        String evalResult;
        try {
            Object evaluatedValue;
            if (ValueParamEvaluator.NAME.equals(evaluatorType)
                    && isDirectValueExpression(workflowTask.getExpression())) {
                Map<String, Object> resolved =
                        parametersUtils.getTaskInput(
                                Map.of(DIRECT_VALUE_KEY, workflowTask.getExpression()),
                                taskMapperContext.getWorkflowModel(),
                                taskMapperContext.getTaskId());
                evaluatedValue = resolved.get(DIRECT_VALUE_KEY);
            } else {
                evaluatedValue = evaluator.evaluate(workflowTask.getExpression(), taskInput);
            }
            evalResult = String.valueOf(evaluatedValue);
        } catch (Exception exception) {
            TaskModel switchTask = failedSwitchTask(taskMapperContext, taskInput, exception.getMessage());
            tasksToBeScheduled.add(switchTask);
            return tasksToBeScheduled;
        }

        TaskModel switchTask = taskMapperContext.createTaskModel();
        switchTask.setTaskType(TaskType.TASK_TYPE_SWITCH);
        switchTask.setTaskDefName(TaskType.TASK_TYPE_SWITCH);
        switchTask.getInputData().putAll(taskInput);
        switchTask.getInputData().put("case", evalResult);
        switchTask.addOutput("evaluationResult", List.of(evalResult));
        switchTask.addOutput("selectedCase", evalResult);
        switchTask.setStartTime(System.currentTimeMillis());
        switchTask.setStatus(TaskModel.Status.IN_PROGRESS);
        tasksToBeScheduled.add(switchTask);

        List<com.helianhealth.agent.common.domain.metadata.workflow.WorkflowTask> selectedTasks =
                workflowTask.getDecisionCases().get(evalResult);
        if (selectedTasks == null || selectedTasks.isEmpty()) {
            selectedTasks = workflowTask.getDefaultCase();
        }
        if (selectedTasks != null && !selectedTasks.isEmpty()) {
            var selectedTask = selectedTasks.get(0);
            List<TaskModel> caseTasks =
                    taskMapperContext.getDeciderService()
                            .getTasksToBeScheduled(
                                    taskMapperContext.getWorkflowModel(),
                                    selectedTask,
                                    taskMapperContext.getRetryCount(),
                                    taskMapperContext.getRetryTaskId());
            tasksToBeScheduled.addAll(caseTasks);
            switchTask.getInputData().put("hasChildren", "true");
        }
        return tasksToBeScheduled;
    }

    private static TaskModel failedSwitchTask(
            TaskMapperContext context, Map<String, Object> taskInput, String reason) {
        TaskModel switchTask = context.createTaskModel();
        switchTask.setTaskType(TaskType.TASK_TYPE_SWITCH);
        switchTask.setTaskDefName(TaskType.TASK_TYPE_SWITCH);
        switchTask.getInputData().putAll(taskInput);
        switchTask.setStartTime(System.currentTimeMillis());
        switchTask.setStatus(TaskModel.Status.FAILED);
        switchTask.setReasonForInCompletion(reason);
        return switchTask;
    }

    private static Map<String, Object> requireTaskInput(TaskMapperContext context) {
        if (context.getTaskInput() == null) {
            throw new IllegalStateException(
                    "SwitchTaskMapper requires pre-resolved taskInput on TaskMapperContext");
        }
        return context.getTaskInput();
    }

    private static boolean isDirectValueExpression(String expression) {
        return expression != null && expression.contains("${") && expression.contains("}");
    }
}
