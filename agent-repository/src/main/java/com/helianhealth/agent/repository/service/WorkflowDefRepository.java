package com.helianhealth.agent.repository.service;

import com.helianhealth.agent.repository.model.def.WorkflowDef;

import java.util.List;
import java.util.Optional;

/**
 * 工作流定义仓储（conductor {@code MetadataDAO} 中工作流相关能力的业务适配子集）。
 *
 * <p>统一以 {@link WorkflowDef}（即 {@code json_data} 的领域对象）为出入参，
 * 屏蔽 PO 与版本/latest_version 维护细节。
 */
public interface WorkflowDefRepository {

    /**
     * 新建一份定义（要求 name+version 不存在，否则冲突）。
     */
    void createWorkflowDef(WorkflowDef def);

    /**
     * 新建或覆盖更新一份定义（存在则覆盖该版本的 json_data，并维护 latest_version）。
     */
    void upsertWorkflowDef(WorkflowDef def);

    /**
     * 取指定版本。
     */
    Optional<WorkflowDef> getWorkflowDef(String name, int version);

    /**
     * 取最新版。
     */
    Optional<WorkflowDef> getLatestWorkflowDef(String name);

    /**
     * 按入口 URI 取最新且启用的版本（路由命中）。
     */
    Optional<WorkflowDef> getLatestEnabledByInterfaceUri(String interfaceUri);

    /**
     * 取某 name 的全部版本（升序）。
     */
    List<WorkflowDef> getAllVersions(String name);

    /**
     * 取所有定义的最新版。
     */
    List<WorkflowDef> getAllLatest();

    /**
     * 删除指定版本，并按剩余行重置 latest_version。
     */
    void removeWorkflowDef(String name, int version);
}
