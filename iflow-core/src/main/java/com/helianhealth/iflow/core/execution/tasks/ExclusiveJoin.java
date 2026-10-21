package com.helianhealth.iflow.core.execution.tasks;

import com.helianhealth.iflow.common.domain.metadata.task.TaskType;
import com.helianhealth.iflow.core.execution.WorkflowExecutor;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 排他汇聚任务。
 *
 * <p>用于 SWITCH 之后的多分支收口：等待第一个走入终态的分支即完成，其余分支视为未被选中。
 * 配置：{@code joinOn} 候选引用名列表；{@code defaultExclusiveJoinTask} 兜底分支。
 */
@Component(TaskType.TASK_TYPE_EXCLUSIVE_JOIN)
public class ExclusiveJoin extends WorkflowSystemTask {

    public ExclusiveJoin() {
        super(TaskType.TASK_TYPE_EXCLUSIVE_JOIN);
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean execute(WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        List<String> joinOn = (List<String>) task.getInputData().get("joinOn");
        List<String> defaultJoinOn = (List<String>) task.getInputData().get("defaultExclusiveJoinTask");

        TaskModel hit = pickFirstTerminal(workflow, joinOn);
        if (hit == null) {
            hit = pickFirstTerminal(workflow, defaultJoinOn);
        }
        if (hit == null) {
            return false;
        }

        if (hit.getStatus().isSuccessful()) {
            task.setOutputData(hit.getOutputData());
            task.setStatus(TaskModel.Status.COMPLETED);
        } else {
            task.setReasonForInCompletion(hit.getReasonForInCompletion());
            task.setStatus(TaskModel.Status.FAILED);
        }
        return true;
    }

    private TaskModel pickFirstTerminal(WorkflowModel workflow, List<String> refs) {
        if (refs == null) {
            return null;
        }
        for (String ref : refs) {
            TaskModel candidate = workflow.getTaskByRefName(ref);
            if (candidate != null && candidate.getStatus().isTerminal()) {
                return candidate;
            }
        }
        return null;
    }
}
