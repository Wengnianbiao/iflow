package com.helianhealth.iflow.core.execution;

import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;

import java.util.List;

/**
 * 工作流任务调度。
 *
 * <p>Scheduler 执行本轮任务，不解释 DAG。控制节点和根执行域任务在工作流调用线程执行，
 * FORK 分支业务任务提交到分支线程池，同一任务跨轮次复用正在进行的执行。
 * 本轮没有同步推进时，等待当前工作流任一分支完成，其余分支继续执行。
 */
public interface TaskScheduler {

    enum ScheduleMode {
        CALLER,
        FORK_BRANCH
    }

    ScheduleMode getScheduleMode(TaskModel task);

    /**
     * 执行一轮任务
     */
    void schedule(
            WorkflowModel workflow,
            List<TaskModel> tasks,
            WorkflowExecutor workflowExecutor);

    /** 结束调用线程的驱动过程；尚未返回的执行保留占用，实际完成后释放。 */
    default void release(WorkflowModel workflow) {}
}
