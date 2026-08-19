package com.helianhealth.agent.core.execution;

import com.helianhealth.agent.core.config.AgentCoreConfiguration;
import com.helianhealth.agent.core.model.TaskModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * 同步工作流默认派发器：根执行域使用当前调用线程，FORK 分支使用专用线程池。
 */
@Component
public class CallerAndForkTaskDispatcher implements TaskDispatcher {

    private final Executor forkBranchExecutor;

    public CallerAndForkTaskDispatcher(
            @Qualifier(AgentCoreConfiguration.FORK_BRANCH_TASK_EXECUTOR)
                    Executor forkBranchExecutor) {
        this.forkBranchExecutor = Objects.requireNonNull(forkBranchExecutor);
    }

    @Override
    public DispatchMode getDispatchMode(TaskModel task) {
        return task.isForkBranchTask() ? DispatchMode.FORK_BRANCH : DispatchMode.CALLER;
    }

    @Override
    public void dispatch(DispatchMode mode, Runnable command) {
        if (mode == DispatchMode.FORK_BRANCH) {
            forkBranchExecutor.execute(command);
            return;
        }
        command.run();
    }
}
