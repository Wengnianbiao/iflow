package com.helianhealth.iflow.console.controller;

import com.helianhealth.iflow.common.base.PageList;
import com.helianhealth.iflow.common.base.ResultData;
import com.helianhealth.iflow.common.domain.run.StartWorkflowRequest;
import com.helianhealth.iflow.common.domain.run.ExecutionSearch;
import com.helianhealth.iflow.core.dao.IndexDAO;
import com.helianhealth.iflow.console.config.RequestMappingConstants;
import com.helianhealth.iflow.console.model.response.WorkflowExecutionDetail;
import com.helianhealth.iflow.console.model.response.WorkflowExecutionSummary;
import com.helianhealth.iflow.core.dao.ExecutionDAO;
import com.helianhealth.iflow.core.execution.WorkflowExecutor;
import com.helianhealth.iflow.core.model.WorkflowModel;
import com.helianhealth.iflow.core.service.WorkflowExecutionService;
import com.helianhealth.iflow.gateway.rest.OpenApiRequest;
import com.helianhealth.iflow.gateway.rest.OpenApiRequestReader;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping(RequestMappingConstants.WORKFLOW_EXECUTION)
@RequiredArgsConstructor
public class WorkflowExecutionController {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_INSTANCE_LIMIT = 500;
    private static final long DEFAULT_SEARCH_WINDOW_MS = Duration.ofDays(7).toMillis();

    private final ExecutionDAO executionDAO;
    private final WorkflowExecutionService workflowExecutionService;
    private final WorkflowExecutor workflowExecutor;
    private final IndexDAO indexDAO;
    private final OpenApiRequestReader requestReader;

    @GetMapping("/instances")
    public ResultData<List<WorkflowExecutionSummary>> listInstances(
            @RequestParam("route") String route,
            @RequestParam(value = "version", defaultValue = "1") int version,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        int safeLimit = clamp(limit, 1, MAX_INSTANCE_LIMIT);
        return ResultData.ok(indexDAO.searchWorkflows(new ExecutionSearch.WorkflowQuery(List.of(route),
                version > 0 ? version : null, List.of(), null, null, null, null, 1, safeLimit))
                .getRows().stream().map(WorkflowExecutionSummary::from).toList());
    }

    @GetMapping("/search")
    public ResultData<PageList<WorkflowExecutionSummary>> search(
            @RequestParam(value = "workflowRoute", required = false) String workflowRoute,
            @RequestParam(value = "workflowRoutes", required = false) List<String> workflowRoutes,
            @RequestParam(value = "version", required = false) Integer version,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "workflowId", required = false) String workflowId,
            @RequestParam(value = "correlationId", required = false) String correlationId,
            @RequestParam(value = "startTime", required = false) Long startTime,
            @RequestParam(value = "endTime", required = false) Long endTime,
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        int safePageNum = Math.max(1, pageNum);
        int safePageSize = clamp(pageSize, 1, MAX_PAGE_SIZE);
        List<String> routes = workflowRoutes == null ? List.of() : workflowRoutes.stream()
                .filter(StringUtils::hasText).map(String::trim).distinct().toList();
        if (routes.isEmpty() && StringUtils.hasText(workflowRoute)) {
            routes = List.of(workflowRoute.trim());
        }
        Long from = startTime;
        Long to = endTime;
        if (!StringUtils.hasText(workflowId) && !StringUtils.hasText(correlationId)) {
            to = to != null ? to : System.currentTimeMillis();
            from = from != null ? from : Math.max(0L, to - DEFAULT_SEARCH_WINDOW_MS);
        }
        var indexed = indexDAO.searchWorkflows(new ExecutionSearch.WorkflowQuery(routes, version,
                List.copyOf(parseStatuses(status)), workflowId, correlationId, from, to, safePageNum, safePageSize));
        return ResultData.ok(new PageList<>(indexed.getTotal(), indexed.getPageNum(), indexed.getPageSize(),
                indexed.getRows().stream().map(WorkflowExecutionSummary::from).toList()));
    }

    @PostMapping("/start")
    public ResultData<String> start(@RequestBody StartWorkflowRequest request) {
        return ResultData.ok(workflowExecutionService.startWorkflow(request));
    }

    @PostMapping("/start-http")
    public ResultData<String> startHttp(HttpServletRequest request) {
        OpenApiRequest parsed = requestReader.read(request);
        if (!(parsed.query().get("route") instanceof String route) || !StringUtils.hasText(route)) {
            throw new IllegalArgumentException("route 必须为非空字符串");
        }
        Object versionValue = parsed.query().get("version");
        if (versionValue != null && !(versionValue instanceof String)) {
            throw new IllegalArgumentException("version 必须为整数");
        }
        Integer version = versionValue == null ? null : Integer.valueOf((String) versionValue);
        Map<String, Object> input = parsed.toWorkflowInput();
        // route/version 是控制台启动参数；业务请求路径使用选中的工作流路由。
        input.put("query", Map.of());
        input.put("path", "/" + route);
        return ResultData.ok(workflowExecutionService.startWorkflow(
                StartWorkflowRequest.builder().route(route).version(version).input(input).build()));
    }

    @GetMapping("/tasks/search")
    public ResultData<PageList<ExecutionSearch.TaskSummary>> searchTasks(
            @RequestParam(value = "workflowRoute", required = false) String workflowRoute,
            @RequestParam(value = "workflowId", required = false) String workflowId,
            @RequestParam(value = "taskId", required = false) String taskId,
            @RequestParam(value = "nodeRef", required = false) String nodeRef,
            @RequestParam(value = "taskType", required = false) String taskType,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "asyncOnly", defaultValue = "false") boolean asyncOnly,
            @RequestParam(value = "startTime", required = false) Long startTime,
            @RequestParam(value = "endTime", required = false) Long endTime,
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        Long to = endTime;
        Long from = startTime;
        if (!StringUtils.hasText(taskId) && !StringUtils.hasText(workflowId)) {
            to = to != null ? to : System.currentTimeMillis();
            from = from != null ? from : Math.max(0L, to - DEFAULT_SEARCH_WINDOW_MS);
        }
        return ResultData.ok(indexDAO.searchTasks(new ExecutionSearch.TaskQuery(workflowRoute, workflowId,
                taskId, nodeRef, taskType, List.copyOf(parseStatuses(status)), asyncOnly, from, to, pageNum, pageSize)));
    }

    @GetMapping("/{workflowId}")
    public ResultData<WorkflowExecutionDetail> getWorkflow(
            @PathVariable("workflowId") String workflowId,
            @RequestParam(value = "includeTasks", defaultValue = "true") boolean includeTasks) {
        WorkflowModel workflow =
                executionDAO
                        .getWorkflow(workflowId, includeTasks)
                        .orElseThrow(() -> new IllegalArgumentException("工作流实例不存在: " + workflowId));
        return ResultData.ok(WorkflowExecutionDetail.from(workflow));
    }

    @GetMapping("/running/{route}/{version}")
    public ResultData<List<String>> getRunningWorkflowIds(
            @PathVariable("route") String route, @PathVariable("version") int version) {
        return ResultData.ok(executionDAO.getRunningWorkflowIds(route, version));
    }

    @PostMapping("/{workflowId}/terminate")
    public ResultData<Boolean> terminateWorkflow(
            @PathVariable("workflowId") String workflowId,
            @RequestParam(value = "reason", required = false) String reason) {
        WorkflowModel workflow =
                executionDAO
                        .getWorkflow(workflowId, true)
                        .orElseThrow(() -> new IllegalArgumentException("工作流实例不存在: " + workflowId));
        if (workflow.getStatus() != null && workflow.getStatus().isTerminal()) {
            return ResultData.ok(true);
        }
        workflowExecutor.terminateWorkflow(
                workflow,
                StringUtils.hasText(reason) ? reason : "terminated from console");
        return ResultData.ok(true);
    }

    private static Set<String> parseStatuses(String status) {
        if (!StringUtils.hasText(status)) {
            return Set.of();
        }
        return Arrays.stream(status.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(value -> value.toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
