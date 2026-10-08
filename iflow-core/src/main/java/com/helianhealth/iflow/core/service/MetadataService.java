package com.helianhealth.iflow.core.service;

import com.helianhealth.iflow.common.domain.metadata.task.TaskDef;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowDef;
import com.helianhealth.iflow.core.dao.MetadataDAO;

import java.util.List;

/**
 * 元数据服务
 *
 * <p>对外暴露工作流/任务定义的注册、查询与删除能力；内部通过 {@link MetadataDAO}
 * 访问持久化层。定义管理（Console / Bootstrap）应依赖本接口，而非直接注入 DAO。引擎启动时按
 * route/version 查询定义（version 为 null 取最新）；Gateway 等调用方请走 {@link WorkflowExecutionService}。
 *
 * <p>查询方法在未找到时抛出异常，不返回 {@link java.util.Optional}。
 */
public interface MetadataService {

    // ===== TaskDef =====

    TaskDef registerTaskDef(TaskDef taskDef);

    TaskDef updateTaskDef(TaskDef taskDef);

    TaskDef getTaskDef(String name);

    List<TaskDef> getAllTaskDefs();

    void unregisterTaskDef(String name);

    // ===== WorkflowDef =====

    void registerWorkflowDef(WorkflowDef def);

    void updateWorkflowDef(WorkflowDef def);

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
