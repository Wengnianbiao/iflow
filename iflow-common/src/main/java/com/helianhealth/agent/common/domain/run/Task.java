package com.helianhealth.agent.common.domain.run;

import com.helianhealth.agent.common.domain.metadata.workflow.WorkflowTask;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * 任务运行时视图。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Task {

    public enum Status {
        SCHEDULED(false, true, true),
        IN_PROGRESS(false, true, true),
        CANCELED(true, false, false),
        FAILED(true, false, true),
        FAILED_WITH_TERMINAL_ERROR(true, false, false),
        COMPLETED(true, true, true),
        COMPLETED_WITH_ERRORS(true, true, true),
        TIMED_OUT(true, false, true),
        SKIPPED(true, true, false);

        private final boolean terminal;
        private final boolean successful;
        private final boolean retriable;

        Status(boolean terminal, boolean successful, boolean retriable) {
            this.terminal = terminal;
            this.successful = successful;
            this.retriable = retriable;
        }

        public boolean isTerminal() {
            return terminal;
        }

        public boolean isSuccessful() {
            return successful;
        }

        public boolean isRetriable() {
            return retriable;
        }
    }

    private String taskType;

    private Status status;

    private Status previousStatus;

    @Builder.Default
    private Map<String, Object> inputData = new HashMap<>();

    @Builder.Default
    private Map<String, Object> outputData = new HashMap<>();

    private String referenceTaskName;

    private int retryCount;

    private int seq;

    private String correlationId;

    private String taskDefName;

    private long scheduledTime;

    private long firstScheduledTime;

    private long startTime;

    private long endTime;

    private long updateTime;

    private String reasonForIncompletion;

    private String workerId;

    private String taskId;

    private String workflowInstanceId;

    private String workflowType;

    private String retriedTaskId;

    private boolean retried;

    private boolean executed;

    private WorkflowTask workflowTask;

    private String parentTaskId;

    private String forkTaskId;

    private String keyword;

    private int iteration;

    public String getTaskDefName() {
        if (taskDefName == null || taskDefName.isEmpty()) {
            return taskType;
        }
        return taskDefName;
    }
}
