package com.helianhealth.iflow.console.controller;

import com.helianhealth.iflow.common.base.ResultData;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowDef;
import com.helianhealth.iflow.console.config.RequestMappingConstants;
import com.helianhealth.iflow.core.service.MetadataService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 工作流定义元数据 API
 */
@RestController
@RequestMapping(RequestMappingConstants.WORKFLOW_METADATA)
public class WorkflowDefinitionController {

    private final MetadataService metadataService;

    public WorkflowDefinitionController(MetadataService metadataService) {
        this.metadataService = metadataService;
    }

    @PostMapping("/create")
    public ResultData<WorkflowDef> create(@RequestBody WorkflowDef def) {
        metadataService.registerWorkflowDef(def);
        return ResultData.ok(metadataService.getWorkflowDef(def.getRoute(), def.getVersion()));
    }

    @GetMapping("/all")
    public ResultData<List<WorkflowDef>> getAll() {
        return ResultData.ok(metadataService.getAllLatest());
    }

    @GetMapping("/latest")
    public ResultData<WorkflowDef> getLatestByQuery(@RequestParam("route") String route) {
        return ResultData.ok(metadataService.getLatestWorkflowDef(route));
    }

    @GetMapping("/detail")
    public ResultData<WorkflowDef> getByVersionByQuery(
            @RequestParam("route") String route, @RequestParam("version") int version) {
        return ResultData.ok(metadataService.getWorkflowDef(route, version));
    }

    @GetMapping("/versions")
    public ResultData<List<Integer>> listVersions(@RequestParam("route") String route) {
        List<Integer> versions =
                metadataService.getAllVersions(route).stream()
                        .map(WorkflowDef::getVersion)
                        .distinct()
                        .sorted(Comparator.naturalOrder())
                        .collect(Collectors.toList());
        return ResultData.ok(versions);
    }

    @PutMapping
    public ResultData<WorkflowDef> update(
            @RequestParam("route") String route,
            @RequestParam("version") int version,
            @RequestBody WorkflowDef def) {
        if (def == null || !Objects.equals(route, def.getRoute()) || version != def.getVersion()) {
            throw new IllegalArgumentException("更新目标 route/version 必须与工作流定义一致；新路由或版本请创建定义");
        }
        metadataService.getWorkflowDef(route, version);
        metadataService.updateWorkflowDef(def);
        return ResultData.ok(metadataService.getWorkflowDef(def.getRoute(), def.getVersion()));
    }

    @DeleteMapping
    public ResultData<Boolean> deleteByQuery(
            @RequestParam("route") String route,
            @RequestParam(value = "version", required = false) Integer version) {
        if (version == null) {
            metadataService.unregisterAllWorkflowVersions(route);
        } else {
            metadataService.unregisterWorkflowDef(route, version);
        }
        return ResultData.ok(true);
    }
}
