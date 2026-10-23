package com.helianhealth.iflow.common.domain.run;

import java.util.List;
import java.util.Locale;

/** 执行索引的结构化搜索条件和摘要，不包含业务输入输出。 */
public final class ExecutionSearch {
    private ExecutionSearch() {}

    public record WorkflowQuery(List<String> workflowRoutes, Integer version, List<String> statuses,
                                String workflowId, String correlationId, Long startTime, Long endTime,
                                int pageNum, int pageSize) {
        public WorkflowQuery {
            workflowRoutes = strings(workflowRoutes);
            statuses = strings(statuses).stream().map(s -> s.toUpperCase(Locale.ROOT)).toList();
            workflowId = text(workflowId);
            correlationId = text(correlationId);
            validateTime(startTime, endTime);
            pageNum = Math.max(1, pageNum);
            pageSize = Math.max(1, Math.min(100, pageSize));
        }
        public long offset() { return ((long) pageNum - 1) * pageSize; }
    }

    public record TaskQuery(String workflowRoute, String workflowId, String taskId, String nodeRef,
                            String taskType, List<String> statuses, boolean asyncOnly,
                            Long startTime, Long endTime, int pageNum, int pageSize) {
        public TaskQuery {
            workflowRoute = text(workflowRoute);
            workflowId = text(workflowId);
            taskId = text(taskId);
            nodeRef = text(nodeRef);
            taskType = text(taskType);
            statuses = strings(statuses).stream().map(s -> s.toUpperCase(Locale.ROOT)).toList();
            validateTime(startTime, endTime);
            pageNum = Math.max(1, pageNum);
            pageSize = Math.max(1, Math.min(100, pageSize));
        }
        public long offset() { return ((long) pageNum - 1) * pageSize; }
    }

    public record WorkflowSummary(String workflowId, String workflowRoute, String workflowName,
                                   int workflowVersion, String status, String correlationId,
                                   long startTime, long endTime, long updateTime,
                                   String reasonForIncompletion, String failedTaskId) {}

    public record TaskSummary(String taskId, String workflowId, String workflowRoute, String nodeRef,
                              String referenceTaskName, String taskName, String taskType,
                              Boolean async, String status, int retryCount, long scheduledTime,
                              long startTime, long endTime, long updateTime, Long durationMs) {}

    private static String text(String value) { return value == null ? "" : value.trim(); }
    private static List<String> strings(List<String> values) {
        return values == null ? List.of() : values.stream().filter(java.util.Objects::nonNull)
                .map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
    }
    private static void validateTime(Long start, Long end) {
        if ((start != null && start < 0) || (end != null && end < 0)
                || (start != null && end != null && end < start)) {
            throw new IllegalArgumentException("请选择有效的执行时间范围");
        }
    }
}
