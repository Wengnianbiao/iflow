package com.helianhealth.iflow.core.execution;

import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;

/**
 * 工作流执行引擎
 */
public interface WorkflowExecutor {

    /**
     * 启动工作流并同步执行至终态。
     */
    WorkflowModel startWorkflow(StartWorkflowInput input);

    WorkflowModel decide(String workflowId);

    /** 由当前调用线程同步推进工作流至终态。 */
    WorkflowModel decideWithLock(WorkflowModel workflow);

    /** 创建并登记 DO_WHILE 当前轮首任务，由调用线程的同步主循环派发执行。 */
    void scheduleNextIteration(TaskModel loopTask, WorkflowModel workflow);

    void terminateWorkflow(WorkflowModel workflow, String reason);

    void updateWorkflow(WorkflowModel workflow);
}
