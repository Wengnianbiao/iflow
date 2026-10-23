package com.helianhealth.iflow.common.domain.statistics;

import java.util.List;
import java.util.Set;

/** 调用看板的查询条件及轻量响应，不携带业务报文。 */
public final class ExecutionStatistics {
    private ExecutionStatistics() {}

    public record Query(long startTime, long endTime, String workflowRoute, boolean asyncOnly,
                        String sort, boolean descending, int pageNum, int pageSize) {
        private static final Set<String> SORTS = Set.of("totalCount", "avgDurationMs",
                "minDurationMs", "maxDurationMs", "successRate");

        public Query {
            if (startTime < 0 || endTime < startTime) {
                throw new IllegalArgumentException("请选择有效的统计时间范围");
            }
            workflowRoute = workflowRoute == null ? "" : workflowRoute.trim();
            sort = sort == null ? "totalCount" : sort;
            if (!SORTS.contains(sort)) throw new IllegalArgumentException("不支持的统计排序字段");
            pageNum = Math.max(1, pageNum);
            pageSize = Math.max(1, Math.min(100, pageSize));
        }

        public long offset() { return ((long) pageNum - 1) * pageSize; }
    }

    public record Metrics(long totalCount, long runningCount, long successCount, long failureCount,
                          long durationCount, Double avgDurationMs, Long minDurationMs,
                          Long maxDurationMs, Double successRate) {}

    public record Row(String workflowRoute, String workflowName, String nodeRef, String taskName,
                      String taskType, Metrics metrics) {}

    public record Page(Metrics summary, List<Row> rows, long total, int pageNum, int pageSize) {}

    public record TaskInstance(String taskId, String workflowId, String referenceTaskName,
                               String status, int retryCount, long startTime, long endTime,
                               Long durationMs) {}

    public record Instances(List<TaskInstance> rows, long total, int pageNum, int pageSize) {}
}
