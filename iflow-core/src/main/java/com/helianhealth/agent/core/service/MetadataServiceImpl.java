package com.helianhealth.agent.core.service;

import com.helianhealth.agent.common.domain.metadata.task.TaskDef;
import com.helianhealth.agent.common.domain.metadata.workflow.WorkflowDef;
import com.helianhealth.agent.common.exception.WorkFlowConfigException;
import com.helianhealth.agent.common.exception.WorkFlowErrorCode;
import com.helianhealth.agent.core.dao.MetadataDAO;
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
    public WorkflowDef saveWorkflowDef(WorkflowDef def) {
        if (metadataDAO.getWorkflowDef(def.getName(), def.getVersion()).isPresent()) {
            metadataDAO.updateWorkflowDef(def);
        } else {
            metadataDAO.createWorkflowDef(def);
        }
        return metadataDAO.getWorkflowDef(def.getName(), def.getVersion()).orElse(def);
    }

    @Override
    public WorkflowDef getWorkflowDef(String name, Integer version) {
        Optional<WorkflowDef> workflowDef;
        if (version == null) {
            workflowDef = metadataDAO.getLatestWorkflowDef(name);
        } else {
            workflowDef = metadataDAO.getWorkflowDef(name, version);
        }

        return workflowDef.orElseThrow(
                () ->
                        new WorkFlowConfigException(
                                WorkFlowErrorCode.WORKFLOW_NOT_FOUND,
                                String.format(
                                        "No such workflow found by name: %s, version: %d",
                                        name, version)));
    }

    @Override
    public WorkflowDef getLatestWorkflowDef(String name) {
        return getWorkflowDef(name, null);
    }

    @Override
    public List<WorkflowDef> getAllLatest() {
        return metadataDAO.getAllLatest();
    }

    @Override
    public List<WorkflowDef> getAllVersions(String name) {
        return metadataDAO.getAllVersions(name);
    }

    @Override
    public void unregisterWorkflowDef(String name, int version) {
        metadataDAO.removeWorkflowDef(name, version);
    }

    @Override
    public void unregisterAllWorkflowVersions(String name) {
        for (WorkflowDef def : metadataDAO.getAllVersions(name)) {
            metadataDAO.removeWorkflowDef(name, def.getVersion());
        }
    }
}
