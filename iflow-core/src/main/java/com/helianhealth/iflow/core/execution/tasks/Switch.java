package com.helianhealth.iflow.core.execution.tasks;

import com.helianhealth.iflow.common.domain.metadata.task.TaskType;
import com.helianhealth.iflow.core.execution.WorkflowExecutor;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
import org.springframework.stereotype.Component;

/**
 * 条件分支任务
 *
 * <p>由 Mapper 在 Decider 阶段按 evaluator 求值后挑选分支调度子任务；Task 的 start 只负责
 * 把自身置 COMPLETED，分支决策结果写在 outputData 中由调用方使用。
 */
@Component(TaskType.TASK_TYPE_SWITCH)
public class Switch extends WorkflowSystemTask {

    public Switch() {
        super(TaskType.TASK_TYPE_SWITCH);
    }

    @Override
    public void start(WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        task.setStatus(TaskModel.Status.COMPLETED);
    }
}
