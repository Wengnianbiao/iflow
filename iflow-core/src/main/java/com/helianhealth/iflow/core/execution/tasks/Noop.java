package com.helianhealth.iflow.core.execution.tasks;

import com.helianhealth.iflow.common.domain.metadata.task.TaskType;
import com.helianhealth.iflow.core.execution.WorkflowExecutor;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;
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
    public void start(WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        task.setStatus(TaskModel.Status.COMPLETED);
    }
}
