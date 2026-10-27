package com.helianhealth.iflow.core.execution;

import com.helianhealth.iflow.core.listener.WorkflowStatusListener;
import com.helianhealth.iflow.core.model.WorkflowModel;

import java.util.function.BiConsumer;

/** 将各终态回调交给测试断言，启动事件保持默认空实现。 */
class TestWorkflowStatusListener implements WorkflowStatusListener {

    private final BiConsumer<String, WorkflowModel.Status> terminalCallback;

    TestWorkflowStatusListener(BiConsumer<String, WorkflowModel.Status> terminalCallback) {
        this.terminalCallback = terminalCallback;
    }

    @Override
    public void onWorkflowCompleted(WorkflowModel workflow) {
        terminalCallback.accept(workflow.getWorkflowId(), WorkflowModel.Status.COMPLETED);
    }

    @Override
    public void onWorkflowFailed(WorkflowModel workflow) {
        terminalCallback.accept(workflow.getWorkflowId(), WorkflowModel.Status.FAILED);
    }

    @Override
    public void onWorkflowTimedOut(WorkflowModel workflow) {
        terminalCallback.accept(workflow.getWorkflowId(), WorkflowModel.Status.TIMED_OUT);
    }

    @Override
    public void onWorkflowTerminated(WorkflowModel workflow) {
        terminalCallback.accept(workflow.getWorkflowId(), WorkflowModel.Status.TERMINATED);
    }
    @Override
    public void onWorkflowInterrupted(WorkflowModel workflow) {
        terminalCallback.accept(workflow.getWorkflowId(), WorkflowModel.Status.INTERRUPTED);
    }

}
