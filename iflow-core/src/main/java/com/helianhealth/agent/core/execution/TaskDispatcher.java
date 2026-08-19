package com.helianhealth.agent.core.execution;

import com.helianhealth.agent.core.model.TaskModel;

/**
 * 工作流任务派发边界。
 *
 * <p>Dispatcher 只根据任务运行时执行域选择调用线程或 FORK 分支池，不解释 DAG、FORK 或 JOIN
 * 的推进条件。
 */
public interface TaskDispatcher {

    enum DispatchMode {
        CALLER,
        FORK_BRANCH
    }

    DispatchMode getDispatchMode(TaskModel task);

    void dispatch(DispatchMode mode, Runnable command);
}
