package com.helianhealth.agent.core.execution.tasks;

import com.helianhealth.agent.common.domain.metadata.task.TaskType;
import com.helianhealth.agent.core.execution.WorkflowExecutor;
import com.helianhealth.agent.core.model.TaskModel;
import com.helianhealth.agent.core.model.WorkflowModel;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 主动终止工作流任务。
 *
 * <p>常用于「条件命中即返回」场景：终止状态写入 {@code terminationStatus}（仅允许 COMPLETED
 * / FAILED / TERMINATED），同时可通过 {@code workflowOutput} 指定工作流输出。
 *
 * <pre>
 * "inputParameters": {
 *   "terminationStatus": "COMPLETED",
 *   "terminationReason": "no more data",
 *   "workflowOutput": "${task0.output}"
 * }
 * </pre>
 */
@Component(TaskType.TASK_TYPE_TERMINATE)
public class Terminate extends WorkflowSystemTask {

    public static final String INPUT_TERMINATION_STATUS = "terminationStatus";
    public static final String INPUT_TERMINATION_REASON = "terminationReason";
    public static final String INPUT_WORKFLOW_OUTPUT = "workflowOutput";

    public Terminate() {
        super(TaskType.TASK_TYPE_TERMINATE);
    }

    @Override
    public boolean execute(WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        String returnStatus = (String) task.getInputData().get(INPUT_TERMINATION_STATUS);
        if (!isValidStatus(returnStatus)) {
            task.setReasonForInCompletion("invalid terminationStatus: " + returnStatus);
            task.setStatus(TaskModel.Status.FAILED);
            return false;
        }
        task.setOutputData(extractOutput(task.getInputData()));
        task.setStatus(TaskModel.Status.COMPLETED);
        return true;
    }

    public static boolean isValidStatus(String status) {
        return WorkflowModel.Status.COMPLETED.name().equals(status)
                || WorkflowModel.Status.FAILED.name().equals(status)
                || WorkflowModel.Status.TERMINATED.name().equals(status);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractOutput(Map<String, Object> input) {
        Map<String, Object> output = new HashMap<>();
        Object value = input.get(INPUT_WORKFLOW_OUTPUT);
        if (value == null) {
            return output;
        }
        if (value instanceof Map<?, ?>) {
            output.putAll((Map<String, Object>) value);
        } else {
            output.put("output", value);
        }
        return output;
    }
}
