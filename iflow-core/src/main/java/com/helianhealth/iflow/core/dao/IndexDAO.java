package com.helianhealth.iflow.core.dao;

import com.helianhealth.iflow.common.domain.statistics.ExecutionStatistics;
import com.helianhealth.iflow.common.base.PageList;
import com.helianhealth.iflow.common.domain.run.ExecutionSearch;
import com.helianhealth.iflow.core.model.TaskModel;
import com.helianhealth.iflow.core.model.WorkflowModel;

/** 执行索引端口：负责搜索、分页及聚合；引擎推进始终读取 ExecutionDAO。 */
public interface IndexDAO {
    void indexWorkflow(WorkflowModel workflow);
    void indexTask(TaskModel task);
    void removeWorkflow(String workflowId);
    void removeTask(String taskId);
    PageList<ExecutionSearch.WorkflowSummary> searchWorkflows(ExecutionSearch.WorkflowQuery query);
    PageList<ExecutionSearch.TaskSummary> searchTasks(ExecutionSearch.TaskQuery query);
    ExecutionStatistics.Page workflows(ExecutionStatistics.Query query);
    ExecutionStatistics.Page tasks(ExecutionStatistics.Query query);
    ExecutionStatistics.RuntimeOverview overview(ExecutionStatistics.OverviewQuery query);
    ExecutionStatistics.Instances taskInstances(ExecutionStatistics.Query query, String nodeRef, String taskType);
}
