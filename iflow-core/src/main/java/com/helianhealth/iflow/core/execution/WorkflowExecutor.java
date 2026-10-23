package com.helianhealth.iflow.core.execution;

import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;

/**
 * 工作流执行引擎
 */
public interface WorkflowExecutor {

    /**
     * 创建工作流并在调用线程执行首次决策，不等待异步任务完成。
     * 返回工作流实例 ID，执行状态和结果通过实例查询获取。
     */
    String startWorkflow(StartWorkflowInput input);

    /** 创建工作流并等待进入终态后返回最终结果。 */
    WorkflowModel startWorkflowSync(StartWorkflowInput input);

    /** 生成并调度 DO_WHILE 当前轮入口任务，其余任务由 Decider 在入口完成后推进。 */
    void scheduleNextIteration(TaskModel task, WorkflowModel workflow);

    void terminateWorkflow(WorkflowModel workflow, String reason);

    /** 保存工作流，终态保存成功后在当前线程通知生命周期监听器；调用方负责实例锁。 */
    void updateWorkflow(WorkflowModel workflow);
}
