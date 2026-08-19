package com.helianhealth.agent.core.execution;

import com.helianhealth.agent.core.model.TaskModel;
import com.helianhealth.agent.core.model.WorkflowModel;

/**
 * 工作流执行引擎入口。
 */
public interface WorkflowExecutor {

    /**
     * 启动工作流并同步执行至终态。
     */
    WorkflowModel startWorkflow(StartWorkflowInput input);

    WorkflowModel decide(String workflowId);

    /** 唤醒已有同步执行；没有活动执行时，由当前调用线程同步推进至终态。 */
    WorkflowModel decideWithLock(WorkflowModel workflow);

    void terminateWorkflow(WorkflowModel workflow, String reason);

    void updateWorkflow(WorkflowModel workflow);

    /**
     * 为 DO_WHILE 调度下一轮循环体任务。
     *
     * @param task DO_WHILE 父任务
     * @param workflow 所属工作流实例
     */
    void scheduleNextIteration(TaskModel task, WorkflowModel workflow);
}
