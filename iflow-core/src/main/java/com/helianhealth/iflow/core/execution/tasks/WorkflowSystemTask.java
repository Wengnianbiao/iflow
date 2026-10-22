package com.helianhealth.iflow.core.execution.tasks;

import com.helianhealth.iflow.core.execution.WorkflowExecutor;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;

/**
 * 系统任务基类
 *
 * <p>任务由 Decider 产出：同步任务在工作流推进线程执行，异步任务交给内存队列中的工作线程。
 * 任务本身不创建线程，也不直接触发下一轮 Decider。
 */
public abstract class WorkflowSystemTask {

    private final String taskType;

    protected WorkflowSystemTask(String taskType) {
        this.taskType = taskType;
    }

    /** 是否通过内存队列异步执行；控制任务和纯计算任务默认同步执行。 */
    public boolean isAsync() {
        return false;
    }

    /** 首次启动任务。一次性业务任务返回时必须进入终态，控制任务可等待后续评估。 */
    public void start(WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
    }

    /** 工作流推进线程评估控制任务；状态、迭代或任务图有实际推进时返回 true，等待时返回 false。 */
    public boolean execute(WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        return false;
    }

    /**
     * 工作流取消时回调（默认空实现）。
     */
    public void cancel(WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
    }

    /**
     * 补充任务执行失败诊断，不修改业务输出，也不覆盖任务已提供的更详细原因。
     */
    protected final void addError(TaskModel task, Object error) {
        if (task.getReasonForInCompletion() == null || task.getReasonForInCompletion().isBlank()) {
            task.setReasonForInCompletion(error == null ? null : error.toString());
        }
    }

    public final String getTaskType() {
        return taskType;
    }

    @Override
    public String toString() {
        return taskType;
    }
}
