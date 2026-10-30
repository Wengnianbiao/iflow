package com.helianhealth.iflow.common.domain.statistics;

import java.time.DateTimeException;
import java.time.ZoneId;
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
            validateRange(startTime, endTime);
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

    public static final class OverviewQuery {
        private final long startTime;
        private final long endTime;
        private final String workflowRoute;
        private final ZoneId timeZone;

        public OverviewQuery(long startTime, long endTime, String workflowRoute, String timeZone) {
            validateRange(startTime, endTime);
            if (timeZone == null || timeZone.isBlank()) {
                throw new IllegalArgumentException("请选择有效的统计时区");
            }
            try {
                this.timeZone = ZoneId.of(timeZone);
            } catch (DateTimeException error) {
                throw new IllegalArgumentException("请选择有效的统计时区", error);
            }
            this.startTime = startTime;
            this.endTime = endTime;
            this.workflowRoute = workflowRoute == null ? "" : workflowRoute.trim();
        }

        public long getStartTime() { return startTime; }
        public long getEndTime() { return endTime; }
        public String getWorkflowRoute() { return workflowRoute; }
        public ZoneId getTimeZone() { return timeZone; }
    }

    public static final class TrendPoint {
        private final long startTime;
        private final long workflowCount;
        private final long taskCount;

        public TrendPoint(long startTime, long workflowCount, long taskCount) {
            this.startTime = startTime;
            this.workflowCount = workflowCount;
            this.taskCount = taskCount;
        }

        public long getStartTime() { return startTime; }
        public long getWorkflowCount() { return workflowCount; }
        public long getTaskCount() { return taskCount; }
    }

    public static final class RuntimeOverview {
        private final Metrics workflows;
        private final Metrics tasks;
        private final String granularity;
        private final List<TrendPoint> trend;

        public RuntimeOverview(Metrics workflows, Metrics tasks, String granularity, List<TrendPoint> trend) {
            this.workflows = workflows;
            this.tasks = tasks;
            this.granularity = granularity;
            this.trend = List.copyOf(trend);
        }

        public Metrics getWorkflows() { return workflows; }
        public Metrics getTasks() { return tasks; }
        public String getGranularity() { return granularity; }
        public List<TrendPoint> getTrend() { return trend; }
    }

    private static void validateRange(long startTime, long endTime) {
        if (startTime < 0 || endTime < startTime) {
            throw new IllegalArgumentException("请选择有效的统计时间范围");
        }
    }
}
