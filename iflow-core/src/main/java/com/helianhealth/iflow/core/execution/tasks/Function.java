package com.helianhealth.iflow.core.execution.tasks;

import com.helianhealth.iflow.common.domain.metadata.task.TaskType;
import com.helianhealth.iflow.core.execution.WorkflowExecutor;
import com.helianhealth.iflow.core.execution.function.FunctionExecutor;
import com.helianhealth.iflow.core.execution.function.FunctionType;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component(TaskType.TASK_TYPE_FUNCTION)
public class Function extends WorkflowSystemTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(Function.class);

    public static final String INPUT_FUNCTION = "function";
    public static final String INPUT_VALUE = "value";
    public static final String INPUT_ROOT_NAME = "rootName";

    private final FunctionExecutor functionExecutor;

    public Function(FunctionExecutor functionExecutor) {
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
            if (functionType.requiresStringInput()) {
                if (rawValue == null) {
                    result = null;
                } else if (rawValue instanceof String text) {
                    result = functionExecutor.execute(functionType, text);
                } else {
                    throw new IllegalArgumentException("FUNCTION input 'value' must be a String or null");
                }
            } else {
                result = functionExecutor.execute(functionType, rawValue, readRootName(input.get(INPUT_ROOT_NAME)));
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

    private static String readRootName(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof String text) {
            return text.isBlank() ? null : text;
        }
        throw new IllegalArgumentException("FUNCTION input 'rootName' must be a String");
    }
}
