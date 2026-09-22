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
        return ResultData.ok(metadataService.getWorkflowDef(def.getName(), def.getVersion()));
    }

    @GetMapping("/all")
    public ResultData<List<WorkflowDef>> getAll() {
        return ResultData.ok(metadataService.getAllLatest());
    }

    @GetMapping("/latest")
    public ResultData<WorkflowDef> getLatestByQuery(@RequestParam("name") String name) {
        return ResultData.ok(metadataService.getLatestWorkflowDef(name));
    }

    @GetMapping("/detail")
    public ResultData<WorkflowDef> getByVersionByQuery(
            @RequestParam("name") String name, @RequestParam("version") int version) {
        return ResultData.ok(metadataService.getWorkflowDef(name, version));
    }

    @GetMapping("/versions")
    public ResultData<List<Integer>> listVersions(@RequestParam("name") String name) {
        List<Integer> versions =
                metadataService.getAllVersions(name).stream()
                        .map(WorkflowDef::getVersion)
                        .distinct()
                        .sorted(Comparator.naturalOrder())
                        .collect(Collectors.toList());
        return ResultData.ok(versions);
    }

    @PutMapping
    public ResultData<WorkflowDef> update(@RequestBody WorkflowDef def) {
        metadataService.updateWorkflowDef(def);
        return ResultData.ok(metadataService.getWorkflowDef(def.getName(), def.getVersion()));
    }

    @DeleteMapping
    public ResultData<Boolean> deleteByQuery(
            @RequestParam("name") String name,
            @RequestParam(value = "version", required = false) Integer version) {
        if (version == null) {
            metadataService.unregisterAllWorkflowVersions(name);
        } else {
            metadataService.unregisterWorkflowDef(name, version);
        }
        return ResultData.ok(true);
    }
}
