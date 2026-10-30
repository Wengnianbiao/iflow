package com.helianhealth.iflow.console.controller;

import com.helianhealth.iflow.common.base.ResultData;
import com.helianhealth.iflow.common.domain.statistics.ExecutionStatistics;
import com.helianhealth.iflow.core.dao.IndexDAO;
import com.helianhealth.iflow.core.service.MetadataService;
import com.helianhealth.iflow.console.model.response.ExecutionStatisticsOverview;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workflow/statistics")
@RequiredArgsConstructor
public class ExecutionStatisticsController {
    private final IndexDAO statisticsDAO;
    private final MetadataService metadataService;

    @GetMapping("/overview")
    public ResultData<ExecutionStatisticsOverview> overview(
            @RequestParam("startTime") long startTime, @RequestParam("endTime") long endTime,
            @RequestParam("timeZone") String timeZone,
            @RequestParam(value = "workflowRoute", defaultValue = "") String workflowRoute) {
        var query = new ExecutionStatistics.OverviewQuery(startTime, endTime, workflowRoute, timeZone);
        var definitions = metadataService.getAllLatest();
        long taskCount = 0;
        for (var definition : definitions) {
            taskCount += definition.collectTasks().size();
        }
        var runtime = statisticsDAO.overview(query);
        return ResultData.ok(new ExecutionStatisticsOverview(definitions.size(), taskCount, runtime));
    }

    @GetMapping({"/workflows", "/tasks"})
    public ResultData<ExecutionStatistics.Page> statistics(
            jakarta.servlet.http.HttpServletRequest request,
            @RequestParam("startTime") long startTime, @RequestParam("endTime") long endTime,
            @RequestParam(value = "workflowRoute", defaultValue = "") String workflowRoute,
            @RequestParam(value = "asyncOnly", defaultValue = "true") boolean asyncOnly,
            @RequestParam(value = "sort", defaultValue = "totalCount") String sort,
            @RequestParam(value = "descending", defaultValue = "true") boolean descending,
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        var query = new ExecutionStatistics.Query(startTime, endTime, workflowRoute, asyncOnly,
                sort, descending, pageNum, pageSize);
        return ResultData.ok(request.getServletPath().endsWith("/tasks")
                ? statisticsDAO.tasks(query) : statisticsDAO.workflows(query));
    }

    @GetMapping("/task-instances")
    public ResultData<ExecutionStatistics.Instances> taskInstances(
            @RequestParam("startTime") long startTime, @RequestParam("endTime") long endTime,
            @RequestParam("workflowRoute") String workflowRoute, @RequestParam("nodeRef") String nodeRef,
            @RequestParam("taskType") String taskType,
            @RequestParam(value = "asyncOnly", defaultValue = "false") boolean asyncOnly,
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        if (workflowRoute.isBlank() || nodeRef.isBlank() || taskType.isBlank()) {
            throw new IllegalArgumentException("请选择工作流和任务节点");
        }
        var query = new ExecutionStatistics.Query(startTime, endTime, workflowRoute, asyncOnly,
                "totalCount", true, pageNum, pageSize);
        return ResultData.ok(statisticsDAO.taskInstances(query, nodeRef, taskType));
    }
}
