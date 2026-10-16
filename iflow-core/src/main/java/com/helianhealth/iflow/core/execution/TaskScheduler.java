package com.helianhealth.iflow.core.execution;

import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;

import java.util.List;

/**
 * 工作流任务调度。
 *
 * <p>Scheduler 执行本轮任务，不解释 DAG。控制节点和根执行域任务在工作流调用线程执行，
 * FORK 分支业务任务按批次提交到分支线程池；每批任一任务执行结束后即可继续，
 * 其余分支任务在分支线程池中继续执行。
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
