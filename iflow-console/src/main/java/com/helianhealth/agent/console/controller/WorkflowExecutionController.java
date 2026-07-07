package com.helianhealth.agent.console.controller;

import com.helianhealth.agent.common.base.PageList;
import com.helianhealth.agent.common.base.ResultData;
import com.helianhealth.agent.common.domain.metadata.workflow.WorkflowDef;
import com.helianhealth.agent.common.domain.run.StartWorkflowRequest;
import com.helianhealth.agent.console.config.RequestMappingConstants;
import com.helianhealth.agent.console.model.response.WorkflowExecutionDetail;
import com.helianhealth.agent.console.model.response.WorkflowExecutionSummary;
import com.helianhealth.agent.core.dao.ExecutionDAO;
import com.helianhealth.agent.core.execution.WorkflowExecutor;
import com.helianhealth.agent.core.model.WorkflowModel;
import com.helianhealth.agent.core.service.MetadataService;
import com.helianhealth.agent.core.service.WorkflowExecutionService;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
    private final MetadataService metadataService;
    private final WorkflowExecutionService workflowExecutionService;
    private final WorkflowExecutor workflowExecutor;

    @GetMapping("/instances")
    public ResultData<List<WorkflowExecutionSummary>> listInstances(
            @RequestParam("name") String name,
            @RequestParam(value = "version", defaultValue = "1") int version,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        int safeLimit = clamp(limit, 1, MAX_INSTANCE_LIMIT);
        List<WorkflowExecutionSummary> summaries =
                executionDAO.listRecentInstances(name, version, safeLimit).stream()
                        .map(WorkflowExecutionSummary::from)
                        .collect(Collectors.toList());
        return ResultData.ok(summaries);
    }

    @GetMapping("/search")
    public ResultData<PageList<WorkflowExecutionSummary>> search(
            @RequestParam(value = "workflowName", required = false) String workflowName,
            @RequestParam(value = "name", required = false) String legacyWorkflowName,
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
        String name = firstText(workflowName, legacyWorkflowName);

        List<WorkflowModel> workflows =
                loadSearchCandidates(name, workflowId, correlationId, startTime, endTime);
        Set<String> statuses = parseStatuses(status);
        List<WorkflowExecutionSummary> filtered =
                workflows.stream()
                        .filter(workflow -> matchesWorkflowId(workflow, workflowId))
                        .filter(workflow -> matchesName(workflow, name))
                        .filter(workflow -> matchesVersion(workflow, version))
                        .filter(workflow -> matchesStatus(workflow, statuses))
                        .filter(workflow -> matchesCorrelationId(workflow, correlationId))
                        .filter(workflow -> matchesTimeRange(workflow, startTime, endTime))
                        .sorted(Comparator.comparingLong(WorkflowModel::getStartTime).reversed())
                        .map(WorkflowExecutionSummary::from)
                        .collect(Collectors.toList());

        int fromIndex = Math.min((safePageNum - 1) * safePageSize, filtered.size());
        int toIndex = Math.min(fromIndex + safePageSize, filtered.size());
        PageList<WorkflowExecutionSummary> page =
                PageList.<WorkflowExecutionSummary>builder()
                        .total(filtered.size())
                        .pageNum(safePageNum)
                        .pageSize(safePageSize)
                        .rows(filtered.subList(fromIndex, toIndex))
                        .build();
        return ResultData.ok(page);
    }

    @PostMapping("/start")
    public ResultData<String> start(@RequestBody StartWorkflowRequest request) {
        return ResultData.ok(workflowExecutionService.startWorkflow(request).getWorkflowId());
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

    @GetMapping("/running/{name}/{version}")
    public ResultData<List<String>> getRunningWorkflowIds(
            @PathVariable("name") String name, @PathVariable("version") int version) {
        return ResultData.ok(executionDAO.getRunningWorkflowIds(name, version));
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

    private List<WorkflowModel> loadSearchCandidates(
            String workflowName,
            String workflowId,
            String correlationId,
            Long startTime,
            Long endTime) {
        if (StringUtils.hasText(workflowId)) {
            return executionDAO.getWorkflow(workflowId, false).stream().toList();
        }

        List<String> names = resolveWorkflowNames(workflowName);
        if (StringUtils.hasText(correlationId)) {
            return names.stream()
                    .flatMap(name -> executionDAO.getWorkflowsByCorrelationId(name, correlationId, false).stream())
                    .collect(Collectors.toList());
        }

        long safeEndTime = endTime != null ? endTime : System.currentTimeMillis();
        long safeStartTime =
                startTime != null
                        ? startTime
                        : Math.max(0L, safeEndTime - DEFAULT_SEARCH_WINDOW_MS);
        return names.stream()
                .flatMap(name -> executionDAO.getWorkflowsByType(name, safeStartTime, safeEndTime).stream())
                .collect(Collectors.toMap(
                        WorkflowModel::getWorkflowId,
                        workflow -> workflow,
                        (left, right) -> left,
                        LinkedHashMap::new))
                .values()
                .stream()
                .collect(Collectors.toList());
    }

    private List<String> resolveWorkflowNames(String workflowName) {
        if (StringUtils.hasText(workflowName)) {
            return List.of(workflowName);
        }
        return metadataService.getAllLatest().stream()
                .map(WorkflowDef::getName)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .collect(Collectors.toList());
    }

    private static String firstText(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first;
        }
        return StringUtils.hasText(second) ? second : null;
    }

    private static boolean matchesWorkflowId(WorkflowModel workflow, String workflowId) {
        return !StringUtils.hasText(workflowId)
                || workflowId.equals(workflow.getWorkflowId());
    }

    private static boolean matchesName(WorkflowModel workflow, String workflowName) {
        return !StringUtils.hasText(workflowName)
                || workflowName.equals(workflow.getWorkflowName());
    }

    private static boolean matchesVersion(WorkflowModel workflow, Integer version) {
        return version == null || workflow.getWorkflowVersion() == version;
    }

    private static boolean matchesStatus(WorkflowModel workflow, Set<String> statuses) {
        if (statuses.isEmpty()) {
            return true;
        }
        return workflow.getStatus() != null && statuses.contains(workflow.getStatus().name());
    }

    private static boolean matchesCorrelationId(WorkflowModel workflow, String correlationId) {
        return !StringUtils.hasText(correlationId)
                || correlationId.equals(workflow.getCorrelationId());
    }

    private static boolean matchesTimeRange(WorkflowModel workflow, Long startTime, Long endTime) {
        long value = workflow.getStartTime();
        if (startTime != null && value < startTime) {
            return false;
        }
        return endTime == null || value <= endTime;
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
