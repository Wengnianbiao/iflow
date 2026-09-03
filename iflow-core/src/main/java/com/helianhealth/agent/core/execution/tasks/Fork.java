package com.helianhealth.agent.core.execution.tasks;

import com.helianhealth.agent.common.domain.metadata.task.TaskType;
import com.helianhealth.agent.core.execution.WorkflowExecutor;
import com.helianhealth.agent.core.model.TaskModel;
import com.helianhealth.agent.core.model.WorkflowModel;
import org.springframework.stereotype.Component;

/**
 * 逻辑分支任务。
 *
 * <p>Decider 在调度阶段生成 {@code forkTasks} 各支首任务；FORK 自身的 execute 只负责完成逻辑标记，
 * 各分支任务携带 fork execution scope，由 Scheduler 在专用分支线程池中按批次并行执行，最终由 JOIN 汇聚。
 */
@Component(TaskType.TASK_TYPE_FORK)
public class Fork extends WorkflowSystemTask {

    public Fork() {
        super(TaskType.TASK_TYPE_FORK);
    }

    @Override
    public void execute(WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        task.setStatus(TaskModel.Status.COMPLETED);
    }
}
