package com.helianhealth.agent.common.domain.run;

import com.helianhealth.agent.common.domain.metadata.workflow.WorkflowDef;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工作流运行时视图
 *
 * <p>用于 API 响应、快照 {@code rawJSON}、持久化 JSON —— 与引擎内可变 {@code WorkflowModel} 分离。
 * 不含 Index / 队列 / 外部大载荷存储字段。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Workflow {

    public enum WorkflowStatus {
        RUNNING(false, false),
        COMPLETED(true, true),
        FAILED(true, false),
        TIMED_OUT(true, false),
        TERMINATED(true, false),
        PAUSED(false, true);

        private final boolean terminal;
        private final boolean successful;

        WorkflowStatus(boolean terminal, boolean successful) {
            this.terminal = terminal;
            this.successful = successful;
        }

        public boolean isTerminal() {
            return terminal;
        }

        public boolean isSuccessful() {
            return successful;
        }
    }

    @Builder.Default
    private WorkflowStatus status = WorkflowStatus.RUNNING;

    private WorkflowStatus previousStatus;

    private long endTime;

    private String workflowId;

    @Builder.Default
    private List<Task> tasks = new LinkedList<>();

    @Builder.Default
    private Map<String, Object> input = new HashMap<>();

    @Builder.Default
    private Map<String, Object> output = new HashMap<>();

    private String correlationId;

    private String reasonForIncompletion;

    @Builder.Default
    private Set<String> failedReferenceTaskNames = new HashSet<>();

    @Builder.Default
    private Set<String> failedTaskNames = new HashSet<>();

    private WorkflowDef workflowDefinition;

    @Builder.Default
    private int priority = 0;

    private long lastRetriedTime;

    private String failedTaskId;

    /** 实例创建时间 */
    private long createTime;

    private long updateTime;

    public long getStartTime() {
        return createTime;
    }

    public void setStartTime(long startTime) {
        this.createTime = startTime;
    }

    public String getWorkflowName() {
        return workflowDefinition == null ? null : workflowDefinition.getName();
    }

    public int getWorkflowVersion() {
        return workflowDefinition == null ? 0 : workflowDefinition.getVersion();
    }

    public Task getTaskByRefName(String refName) {
        if (refName == null) {
            throw new IllegalArgumentException("refName cannot be null");
        }
        Task last = null;
        for (Task task : tasks) {
            if (refName.equals(task.getReferenceTaskName())) {
                last = task;
            }
        }
        return last;
    }

    public String toShortString() {
        return getWorkflowName() + "." + getWorkflowVersion() + "/" + workflowId;
    }
}
