package com.helianhealth.agent.repository.mapper.agent;

import com.helianhealth.agent.repository.model.po.InterfaceWorkflowDefPO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 工作流定义 Mapper（conductor {@code meta_workflow_def} 范式）。
 *
 * <p>读写均围绕 {@code (name, version)} 版本化进行，并维护 {@code latest_version} 以快速取最新版。
 */
public interface InterfaceWorkflowDefMapper {

    /**
     * 插入一份定义（不在此维护 latest_version，由服务层在同一事务内调用 {@link #updateLatestVersion}）。
     */
    int insertWorkflowDef(InterfaceWorkflowDefPO po);

    /**
     * 覆盖更新指定版本的 json_data 与冗余列。
     */
    int updateWorkflowDef(InterfaceWorkflowDefPO po);

    /**
     * 判断指定 name+version 是否已存在。
     */
    int countByNameAndVersion(@Param("name") String name, @Param("version") Integer version);

    /**
     * 取当前最大版本号；不存在返回 null。
     */
    Integer getMaxVersion(@Param("name") String name);

    /**
     * 把某 name 的所有行 latest_version 统一更新为给定值。
     */
    int updateLatestVersion(@Param("name") String name, @Param("version") Integer version);

    /**
     * 取指定版本。
     */
    InterfaceWorkflowDefPO selectByNameAndVersion(@Param("name") String name,
                                                  @Param("version") Integer version);

    /**
     * 取最新版（version = latest_version）。
     */
    InterfaceWorkflowDefPO selectLatestByName(@Param("name") String name);

    /**
     * 按入口 URI 取最新且启用的版本（路由命中）。
     */
    InterfaceWorkflowDefPO selectLatestEnabledByInterfaceUri(@Param("interfaceUri") String interfaceUri);

    /**
     * 取某 name 的全部版本（按 version 升序）。
     */
    List<InterfaceWorkflowDefPO> selectAllVersions(@Param("name") String name);

    /**
     * 取所有定义的最新版。
     */
    List<InterfaceWorkflowDefPO> selectAllLatest();

    /**
     * 删除指定版本。
     */
    int deleteByNameAndVersion(@Param("name") String name, @Param("version") Integer version);
}
