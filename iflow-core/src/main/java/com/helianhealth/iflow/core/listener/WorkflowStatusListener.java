package com.helianhealth.iflow.core.listener;

import com.fasterxml.jackson.annotation.JsonValue;
import com.helianhealth.iflow.core.model.WorkflowModel;

import java.util.Locale;

/**
 * 工作流状态持久化后的生命周期回调。
 *
 * <p>回调在保存状态的线程中执行，可能仍持有实例锁；实现必须保持轻量，不修改工作流，
 * 不执行 I/O、等待或调度。同一实例的终态可能被重复保存，实现应允许重复回调。
 */
public interface WorkflowStatusListener {

    enum WorkflowEventType {
        STARTED,
        COMPLETED,
        FAILED,
        TIMED_OUT,
        TERMINATED,
        INTERRUPTED;

        @JsonValue
        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** 实例创建成功后、首次决策前触发，对应 RUNNING 状态。 */
    default void onWorkflowStarted(WorkflowModel workflow) {}

    default void onWorkflowCompleted(WorkflowModel workflow) {}

    default void onWorkflowFailed(WorkflowModel workflow) {}

    default void onWorkflowTimedOut(WorkflowModel workflow) {}

    default void onWorkflowTerminated(WorkflowModel workflow) {}

    default void onWorkflowInterrupted(WorkflowModel workflow) {}
}
