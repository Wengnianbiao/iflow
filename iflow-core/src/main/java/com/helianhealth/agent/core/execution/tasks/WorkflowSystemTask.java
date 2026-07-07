package com.helianhealth.agent.core.execution.tasks;

import com.helianhealth.agent.core.execution.WorkflowExecutor;
import com.helianhealth.agent.core.model.TaskModel;
import com.helianhealth.agent.core.model.WorkflowModel;

/**
 * 系统任务（system task）抽象基类
 *
 * <p>所有任务均由 Decider 直接驱动并提交到共享任务线程池。任务完成后回调 Decider 推进 DAG，
 * 不再区分 start/execute 两阶段。
 */
public abstract class WorkflowSystemTask {

    private final String taskType;

    protected WorkflowSystemTask(String taskType) {
        this.taskType = taskType;
    }

    /**
     * 执行或评估一次任务（默认返回 false，表示状态未改变）。
     *
     * @return true 表示本次调用已改变任务状态；false 表示本轮未推进（例如 JOIN 尚未汇聚完成）。
     */
    public boolean execute(WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        return false;
    }

    /**
     * 工作流取消时回调（默认空实现）。
     */
    public void cancel(WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
    }

    public final String getTaskType() {
        return taskType;
    }

    @Override
    public String toString() {
        return taskType;
    }
}
