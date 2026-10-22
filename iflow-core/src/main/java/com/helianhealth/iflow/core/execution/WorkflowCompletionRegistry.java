package com.helianhealth.iflow.core.execution;

import com.helianhealth.iflow.core.model.WorkflowModel;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;

/** 同步调用的完成通知；不参与工作流调度，也不持有执行锁。 */
@Component
public class WorkflowCompletionRegistry {

    private final ConcurrentMap<String, Registration> waiters = new ConcurrentHashMap<>();

    public Registration register(String workflowId) {
        Registration registration = new Registration(workflowId);
        if (waiters.putIfAbsent(workflowId, registration) != null) {
            throw new IllegalStateException("工作流已有同步等待者: " + workflowId);
        }
        return registration;
    }

    /** 仅在工作流终态成功持久化之后调用。 */
    public void signal(WorkflowModel workflow) {
        if (workflow.getStatus().isTerminal()) {
            Registration registration = waiters.get(workflow.getWorkflowId());
            if (registration != null) {
                registration.completed.release();
            }
        }
    }

    public final class Registration implements AutoCloseable {
        private final String workflowId;
        private final Semaphore completed = new Semaphore(0);

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
