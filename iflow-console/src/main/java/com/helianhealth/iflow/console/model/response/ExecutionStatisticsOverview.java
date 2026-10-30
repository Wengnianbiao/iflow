package com.helianhealth.iflow.console.model.response;

import com.helianhealth.iflow.common.domain.statistics.ExecutionStatistics;

/** 全局配置规模与按条件筛选的运行统计。 */
public class ExecutionStatisticsOverview {
    private final long workflowCount;
    private final long taskCount;
    private final ExecutionStatistics.RuntimeOverview runtime;

    public ExecutionStatisticsOverview(long workflowCount, long taskCount,
            ExecutionStatistics.RuntimeOverview runtime) {
        this.workflowCount = workflowCount;
        this.taskCount = taskCount;
        this.runtime = runtime;
    }

    public long getWorkflowCount() { return workflowCount; }
    public long getTaskCount() { return taskCount; }
    public ExecutionStatistics.RuntimeOverview getRuntime() { return runtime; }
}
