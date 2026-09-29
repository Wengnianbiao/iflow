package com.helianhealth.iflow.core.execution;

import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;

import java.util.List;

/**
 * 工作流任务调度。
 *
 * <p>Scheduler 只执行 Decider 已经选出的本轮任务，不解释或推进 DAG。根执行域任务在调用主线程串行执行，
 * FORK 分支任务按批次提交到分支线程池；本方法返回时，本轮任务均已执行完毕。
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
}
