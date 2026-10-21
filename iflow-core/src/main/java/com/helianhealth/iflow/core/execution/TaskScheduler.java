package com.helianhealth.iflow.core.execution;

import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;

import java.util.List;

/** 生命周期调度；上下文属于单次调用，调度器不保存运行实例。 */
public interface TaskScheduler {
    enum ScheduleMode { CALLER, FORK_BRANCH }

    ScheduleMode getScheduleMode(TaskModel task);

    /** 启动决策选出的 SCHEDULED 任务；在途分支不重复提交，不在此等待。 */
    boolean schedule(WorkflowModel workflow, List<TaskModel> tasks,
                     WorkflowExecutor executor, WorkflowDriveContext context);

    /** 主线程评估控制节点，等待条件不足时不算推进。 */
    boolean evaluate(WorkflowModel workflow, List<TaskModel> tasks,
                     WorkflowExecutor executor, WorkflowDriveContext context);
}
