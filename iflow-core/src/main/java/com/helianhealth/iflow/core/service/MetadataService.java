package com.helianhealth.iflow.core.service;

import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowDef;
import com.helianhealth.iflow.core.dao.MetadataDAO;

import java.util.List;

public interface MetadataService {

    void registerWorkflowDef(WorkflowDef def);

    void updateWorkflowDef(WorkflowDef def);

    WorkflowDef updateWorkflowTags(String route, int version, List<String> tags);

    WorkflowDef saveWorkflowDef(WorkflowDef def);

    /**
     * @param route 工作流名称
     * @param version 版本；为 {@code null} 时取最新版本
     * @return 工作流定义
     */
    WorkflowDef getWorkflowDef(String route, Integer version);

    WorkflowDef getLatestWorkflowDef(String route);

    List<WorkflowDef> getAllLatest();

    List<WorkflowDef> getAllVersions(String route);

    void unregisterWorkflowDef(String route, int version);

    void unregisterAllWorkflowVersions(String route);
}
