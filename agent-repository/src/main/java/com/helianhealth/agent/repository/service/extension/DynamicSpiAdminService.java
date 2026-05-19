package com.helianhealth.agent.repository.service.extension;

import com.helianhealth.agent.common.enums.ExtensionType;
import com.helianhealth.agent.repository.model.domain.NodeExtensionConfig;
import com.helianhealth.agent.repository.model.dto.NodeExtensionConfigCreateReq;
import com.helianhealth.agent.repository.model.dto.NodeExtensionConfigDetailResponse;
import com.helianhealth.agent.repository.model.dto.NodeExtensionConfigUpdateReq;

import java.util.List;

/**
 * 动态 SPI 管理服务 —— 仓储层对外暴露的扩展管理领域服务。
 * <p>统一封装：
 * <ul>
 *   <li>{@link NodeExtensionConfig} 本表 CRUD；</li>
 *   <li>SPI 运行时 register/unregister；</li>
 *   <li>各扩展类型的关联字段同步（节点 extension_param / 节点 remote_extension / 工作流 route_extension）。</li>
 * </ul>
 *
 * <p>对外（agent-ai / agent-console 等前端层）只应依赖本接口，避免依赖具体实现。
 */
public interface DynamicSpiAdminService {

    /**
     * 注册一个新的扩展配置。
     * <p>事务边界：策略关联字段写入 → 本表 insert →（启用时）SPI 注册。
     */
    void registerExtension(ExtensionType type, NodeExtensionConfigCreateReq request);

    /**
     * 更新已有扩展配置。
     * <p>事务边界：策略关联字段替换（清旧+写新）→ 本表 update → 旧 SPI 卸载 → 新 SPI 注册。
     */
    void updateExtension(ExtensionType type, NodeExtensionConfigUpdateReq request);

    /**
     * 禁用扩展：清理关联字段、置 enabled=0、卸载 SPI。
     */
    void disableAndUnregister(Integer configId);

    /**
     * 启用扩展：根据持久化的 bindingIds 恢复关联字段、置 enabled=1、注册 SPI。
     */
    void enableAndRegister(Integer configId);

    /**
     * 删除扩展：清理关联字段、（启用态则）卸载 SPI、删除本表记录。
     */
    void deleteExtension(Integer configId);

    /**
     * 按 configId 查询扩展（原始 domain，不做 enrich）。
     */
    NodeExtensionConfig getExtensionById(Integer configId);

    /**
     * 按 configId 查询扩展详情（含策略 enrich 后的 extensionParamValue 等回显字段）。
     */
    NodeExtensionConfigDetailResponse getExtensionDetailById(Integer configId);

    /**
     * 列出所有启用态的扩展。
     */
    List<NodeExtensionConfig> listEnabledConfigs();

    /**
     * 按 SPI 接口列出扩展。
     */
    List<NodeExtensionConfig> listExtensionsBySpiInterface(String spiInterface);

    /**
     * 获取当前支持的 SPI 接口全限定名清单。
     */
    List<String> getSupportedSpiInterfaces();
}
