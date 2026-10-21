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

    /** 只生成并登记 DO_WHILE 当前轮的入口任务，下一轮决策再调度。 */
    void addNextIterationTasks(TaskModel task, WorkflowModel workflow);

    void terminateWorkflow(WorkflowModel workflow, String reason);

    void updateWorkflow(WorkflowModel workflow);
}
