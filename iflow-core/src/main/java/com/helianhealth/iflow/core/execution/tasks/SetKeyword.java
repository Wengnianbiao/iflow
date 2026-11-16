package com.helianhealth.iflow.core.execution.tasks;

import com.helianhealth.iflow.common.domain.metadata.task.TaskType;
import com.helianhealth.iflow.core.execution.WorkflowExecutor;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import org.springframework.stereotype.Component;

/** 使用已解析的任务入参覆盖当前工作流实例的关键字。 */
@Component(TaskType.TASK_TYPE_SET_KEYWORD)
public class SetKeyword extends WorkflowSystemTask {

    private static final int MAX_KEYWORD_LENGTH = 255;

    public SetKeyword() {
        super(TaskType.TASK_TYPE_SET_KEYWORD);
    }

    @Override
    public void start(WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        Object value = task.getInputData().get("keyword");
        if (!(value instanceof String || value instanceof Number || value instanceof Boolean)) {
            fail(task, "关键字 keyword 必须为字符串、数字或布尔值");
            return;
        }

        String keyword = value.toString().strip();
        if (keyword.isEmpty() || keyword.codePointCount(0, keyword.length()) > MAX_KEYWORD_LENGTH) {
            fail(task, "关键字 keyword 必须包含 1 到 255 个字符");
            return;
        }

        String previousKeyword = workflow.getCorrelationId();
        workflow.setCorrelationId(keyword);
        try {
            workflowExecutor.updateWorkflow(workflow);
        } catch (RuntimeException error) {
            workflow.setCorrelationId(previousKeyword);
            throw error;
        }
        task.setStatus(TaskModel.Status.COMPLETED);
    }

    private void fail(TaskModel task, String reason) {
        task.setReasonForInCompletion(reason);
        task.setStatus(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR);
    }
}
