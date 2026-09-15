package com.helianhealth.agent.core.execution.tasks;

import com.helianhealth.agent.common.domain.metadata.task.TaskType;
import com.helianhealth.agent.core.execution.WorkflowExecutor;
import com.helianhealth.agent.core.execution.function.FunctionExecutor;
import com.helianhealth.agent.core.execution.function.FunctionType;
import com.helianhealth.agent.core.model.TaskModel;
import com.helianhealth.agent.core.model.WorkflowModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 调用平台白名单纯函数，返回值直接作为 {@code outputData}。
 */
@Component(TaskType.TASK_TYPE_FUNCTION)
public class FunctionTask extends WorkflowSystemTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(FunctionTask.class);

    public static final String INPUT_FUNCTION = "function";
    public static final String INPUT_VALUE = "value";

    private final FunctionExecutor functionExecutor;

    public FunctionTask(FunctionExecutor functionExecutor) {
        super(TaskType.TASK_TYPE_FUNCTION);
        this.functionExecutor = functionExecutor;
    }

    @Override
    public void execute(WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        Map<String, Object> input =
                task.getInputData() == null ? Map.of() : task.getInputData();
        try {
            FunctionType functionType = FunctionType.from(input.get(INPUT_FUNCTION));
            if (!input.containsKey(INPUT_VALUE)) {
                throw new IllegalArgumentException("FUNCTION input 'value' must be provided");
            }

            Object rawValue = input.get(INPUT_VALUE);
            Object result;
            if (rawValue == null) {
                result = null;
            } else if (rawValue instanceof String text) {
                result = functionExecutor.execute(functionType, text);
            } else {
                throw new IllegalArgumentException("FUNCTION input 'value' must be a String or null");
            }

            task.setOutputData(result);
            task.setStatus(TaskModel.Status.COMPLETED);
        } catch (Exception e) {
            String message = e.getMessage() != null ? e.getMessage() : "FUNCTION execution failed";
            LOGGER.error(
                    "FUNCTION task failed: workflow={}, task={}",
                    workflow.getWorkflowId(),
                    task.getTaskId(),
                    e);
            task.setOutputData(null);
            addError(task, message);
            task.setReasonForInCompletion(message);
            task.setStatus(TaskModel.Status.FAILED);
        }
    }
}
