package com.helianhealth.iflow.core.service;

import com.helianhealth.iflow.common.domain.metadata.task.TaskDef;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowDef;
import com.helianhealth.iflow.common.exception.WorkFlowConfigException;
import com.helianhealth.iflow.common.exception.WorkFlowErrorCode;
import com.helianhealth.iflow.core.dao.MetadataDAO;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 元数据服务实现：封装 {@link MetadataDAO}，统一 NotFound 与 upsert 语义。
 */
@Service
public class MetadataServiceImpl implements MetadataService {

    private final MetadataDAO metadataDAO;

    public MetadataServiceImpl(MetadataDAO metadataDAO) {
        this.metadataDAO = metadataDAO;
    }

    @Override
    public TaskDef registerTaskDef(TaskDef taskDef) {
        return metadataDAO.createTaskDef(taskDef);
    }

    @Override
    public TaskDef updateTaskDef(TaskDef taskDef) {
        if (metadataDAO.getTaskDef(taskDef.getName()).isEmpty()) {
            throw new WorkFlowConfigException(
                    WorkFlowErrorCode.WORKFLOW_NOT_FOUND, "任务定义不存在: " + taskDef.getName());
        }
        return metadataDAO.updateTaskDef(taskDef);
    }

    @Override
    public TaskDef getTaskDef(String name) {
        return metadataDAO
                .getTaskDef(name)
                .orElseThrow(
                        () ->
                                new WorkFlowConfigException(
                                        WorkFlowErrorCode.WORKFLOW_NOT_FOUND, "任务定义不存在: " + name));
    }

    @Override
    public List<TaskDef> getAllTaskDefs() {
        return metadataDAO.getAllTaskDefs();
    }

    @Override
    public void unregisterTaskDef(String name) {
        metadataDAO.removeTaskDef(name);
    }

    @Override
    public void registerWorkflowDef(WorkflowDef def) {
        metadataDAO.createWorkflowDef(def);
    }

    @Override
    public void updateWorkflowDef(WorkflowDef def) {
        metadataDAO.updateWorkflowDef(def);
    }

    @Override
    public WorkflowDef updateWorkflowTags(String route, int version, List<String> tags) {
        metadataDAO.updateWorkflowTags(route, version, WorkflowDef.normalizeTags(tags));
        return getWorkflowDef(route, version);
    }

    @Override
    public WorkflowDef saveWorkflowDef(WorkflowDef def) {
        if (metadataDAO.getWorkflowDef(def.getRoute(), def.getVersion()).isPresent()) {
            metadataDAO.updateWorkflowDef(def);
        } else {
            metadataDAO.createWorkflowDef(def);
        }
        return metadataDAO.getWorkflowDef(def.getRoute(), def.getVersion()).orElse(def);
    }

    @Override
    public WorkflowDef getWorkflowDef(String route, Integer version) {
        Optional<WorkflowDef> workflowDef;
        if (version == null) {
            workflowDef = metadataDAO.getLatestWorkflowDef(route);
        } else {
            workflowDef = metadataDAO.getWorkflowDef(route, version);
        }

        return workflowDef.orElseThrow(
                () ->
                        new WorkFlowConfigException(
                                WorkFlowErrorCode.WORKFLOW_NOT_FOUND,
                                String.format(
                                        "No such workflow found by route: %s, version: %d",
                                        route, version)));
    }

    @Override
    public WorkflowDef getLatestWorkflowDef(String route) {
        return getWorkflowDef(route, null);
    }

    @Override
    public List<WorkflowDef> getAllLatest() {
        return metadataDAO.getAllLatest();
    }

    @Override
    public List<WorkflowDef> getAllVersions(String route) {
        return metadataDAO.getAllVersions(route);
    }

    @Override
    public void unregisterWorkflowDef(String route, int version) {
        metadataDAO.removeWorkflowDef(route, version);
    }

    @Override
    public void unregisterAllWorkflowVersions(String route) {
        for (WorkflowDef def : metadataDAO.getAllVersions(route)) {
            metadataDAO.removeWorkflowDef(route, def.getVersion());
        }
    }
}
