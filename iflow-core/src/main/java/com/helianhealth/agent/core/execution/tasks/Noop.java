package com.helianhealth.agent.core.execution.tasks;

import com.helianhealth.agent.common.domain.metadata.task.TaskType;
import com.helianhealth.agent.core.execution.WorkflowExecutor;
import com.helianhealth.agent.core.model.TaskModel;
import com.helianhealth.agent.core.model.WorkflowModel;
import org.springframework.stereotype.Component;

/**
 * 空操作任务。
 *
 * <p>占位用：调度即完成，不做任何处理。常用于条件分支的 fallthrough 节点 / 流程编排测试。
 */
@Component(TaskType.TASK_TYPE_NOOP)
public class Noop extends WorkflowSystemTask {

    public Noop() {
        super(TaskType.TASK_TYPE_NOOP);
    }

    @Override
    public void execute(WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        task.setStatus(TaskModel.Status.COMPLETED);
    }
}
