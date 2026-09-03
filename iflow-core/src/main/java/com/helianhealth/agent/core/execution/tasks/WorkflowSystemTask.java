package com.helianhealth.agent.core.execution.tasks;

import com.helianhealth.agent.core.execution.WorkflowExecutor;
import com.helianhealth.agent.core.model.TaskModel;
import com.helianhealth.agent.core.model.WorkflowModel;

/**
 * 系统任务（system task）抽象基类
 *
 * <p>任务由 Decider 产出、Scheduler 调度：根执行域使用工作流调用线程，FORK 分支使用专用线程池。
 * 任务本身不选择执行线程，也不直接触发下一轮 Decider。
 */
public abstract class WorkflowSystemTask {

    /** 系统任务执行失败时统一使用的诊断信息键。 */
    public static final String OUTPUT_ERROR = "error";

    private final String taskType;

    protected WorkflowSystemTask(String taskType) {
        this.taskType = taskType;
    }

    /**
     * 执行或评估一次任务。任务直接通过 {@link TaskModel} 表达执行结果。
     */
    public void execute(WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
    }

    /**
     * 工作流取消时回调（默认空实现）。
     */
    public void cancel(WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
    }

    /**
     * 将任务执行失败的诊断信息写入 {@code outputData.error}。
     */
    protected final void addError(TaskModel task, Object error) {
        task.addOutput(OUTPUT_ERROR, error);
    }

    public final String getTaskType() {
        return taskType;
    }

    @Override
    public String toString() {
        return taskType;
    }
}
