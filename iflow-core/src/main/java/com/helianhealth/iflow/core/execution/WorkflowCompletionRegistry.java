package com.helianhealth.iflow.core.execution;

import com.helianhealth.iflow.core.listener.WorkflowStatusListener;
import com.helianhealth.iflow.core.model.WorkflowModel;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/** 同步调用的完成通知；不参与工作流调度，也不持有执行锁。 */
@Component
public class WorkflowCompletionRegistry implements WorkflowStatusListener {

    private final ConcurrentMap<String, Registration> waiters = new ConcurrentHashMap<>();

    public Registration register(String workflowId) {
        Registration registration = new Registration(workflowId);
        if (waiters.putIfAbsent(workflowId, registration) != null) {
            throw new IllegalStateException("工作流已有同步等待者: " + workflowId);
        }
        return registration;
    }

    @Override
    public void onWorkflowCompleted(WorkflowModel workflow) {
        signal(workflow.getWorkflowId());
    }

    @Override
    public void onWorkflowFailed(WorkflowModel workflow) {
        signal(workflow.getWorkflowId());
    }

    @Override
    public void onWorkflowTimedOut(WorkflowModel workflow) {
        signal(workflow.getWorkflowId());
    }

    @Override
    public void onWorkflowTerminated(WorkflowModel workflow) {
        signal(workflow.getWorkflowId());
    }

    @Override
    public void onWorkflowInterrupted(WorkflowModel workflow) {
        signal(workflow.getWorkflowId());
    }

    private void signal(String workflowId) {
        Registration registration = waiters.get(workflowId);
        if (registration != null && registration.notified.compareAndSet(false, true)) {
            registration.completed.release();
        }
    }

    public final class Registration implements AutoCloseable {
        private final String workflowId;
        private final Semaphore completed = new Semaphore(0);
        private final AtomicBoolean notified = new AtomicBoolean();

        private Registration(String workflowId) {
            this.workflowId = workflowId;
        }

        public void await() throws InterruptedException {
            completed.acquire();
        }

        @Override
        public void close() {
            waiters.remove(workflowId, this);
        }
    }
}
