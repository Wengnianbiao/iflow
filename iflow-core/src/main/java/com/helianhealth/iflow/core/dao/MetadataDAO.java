package com.helianhealth.iflow.core.dao;

import com.helianhealth.iflow.common.domain.metadata.workflow.WorkflowDef;

import java.util.List;
import java.util.Optional;

/**
 * 元数据 DAO
 *
 * <p>承载完整工作流定义（{@link WorkflowDef}，包含节点策略）的持久化访问。
 * 接口定义在 {@code iflow-core}，具体实现由各数据库厂商 DAO 分别提供，引擎只依赖接口。
 *
 * <p>{@code latest_version} 维护是实现内部职责，不在接口上暴露。
 * Gateway 按 {@link #getLatestWorkflowDef(String) route} 取最新版本调度。
 */
public interface MetadataDAO {

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
     * 更新工作流定义（同 route、version 的整份配置替换）。
     */
    void updateWorkflowDef(WorkflowDef def);

    /** 仅替换指定版本的标签，保留其余定义内容。 */
    void updateWorkflowTags(String route, int version, List<String> tags);

    /**
     * 取指定版本的工作流定义。
     */
    Optional<WorkflowDef> getWorkflowDef(String route, int version);

    /**
     * 取指定 route 的最新版本。
     */
    Optional<WorkflowDef> getLatestWorkflowDef(String route);

    /**
     * 取指定 route 下的全部版本（按版本号升序）。
     */
    List<WorkflowDef> getAllVersions(String route);

    /**
     * 取全部工作流定义的最新版本（每个 route 一行）。
     */
    List<WorkflowDef> getAllLatest();

    /**
     * 删除指定版本的工作流定义。
     */
    void removeWorkflowDef(String route, int version);
}
