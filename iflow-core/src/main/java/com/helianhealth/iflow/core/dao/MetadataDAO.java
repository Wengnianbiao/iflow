package com.helianhealth.iflow.core.dao;

import com.helianhealth.iflow.common.domain.metadata.task.TaskDef;
import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowDef;

import java.util.List;
import java.util.Optional;

/**
 * 元数据 DAO
 *
 * <p>承载工作流定义（{@link WorkflowDef}）与任务定义（{@link TaskDef}）的持久化访问。
 * 接口定义在 {@code iflow-core}，具体实现由各数据库厂商 DAO 分别提供，引擎只依赖接口。
 *
 * <p>{@code latest_version} 维护是实现内部职责，不在接口上暴露。
 * Gateway 按 {@link #getLatestWorkflowDef(String) name} 取最新版本调度。
 */
public interface MetadataDAO {

    /**
     * 创建任务定义。
     *
     * @param taskDef 待创建的任务定义
     * @return 创建后的任务定义（含可能由实现回填的字段）
     */
    TaskDef createTaskDef(TaskDef taskDef);

    /**
     * 更新任务定义。
     *
     * @param taskDef 待更新的任务定义（以 {@link TaskDef#getName()} 为键）
     * @return 更新后的任务定义
     */
    TaskDef updateTaskDef(TaskDef taskDef);

    /**
     * 按名称取任务定义。
     */
    Optional<TaskDef> getTaskDef(String name);

    /**
     * 取全部任务定义。
     */
    List<TaskDef> getAllTaskDefs();

    /**
     * 按名称删除任务定义。
     */
    void removeTaskDef(String name);

    // ===== WorkflowDef =====

    /**
     * 创建工作流定义（首次创建，版本默认从 1 开始）。
     *
     * <p>实现负责：
     * <ul>
     *   <li>{@code (name, version)} 唯一性校验；</li>
     *   <li>同 name 下其他行的 {@code latest_version} 同步刷新。</li>
     * </ul>
     */
    void createWorkflowDef(WorkflowDef def);

    /**
     * 更新工作流定义（同 name、version 的整份配置替换）。
     */
    void updateWorkflowDef(WorkflowDef def);

    /**
     * 取指定版本的工作流定义。
     */
    Optional<WorkflowDef> getWorkflowDef(String name, int version);

    /**
     * 取指定 name 的最新版本。
     */
    Optional<WorkflowDef> getLatestWorkflowDef(String name);

    /**
     * 取指定 name 下的全部版本（按版本号升序）。
     */
    List<WorkflowDef> getAllVersions(String name);

    /**
     * 取全部工作流定义的最新版本（每个 name 一行）。
     */
    List<WorkflowDef> getAllLatest();

    /**
     * 删除指定版本的工作流定义。
     */
    void removeWorkflowDef(String name, int version);
}
