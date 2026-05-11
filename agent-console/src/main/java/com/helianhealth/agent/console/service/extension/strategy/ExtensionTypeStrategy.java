package com.helianhealth.agent.console.service.extension.strategy;

import com.helianhealth.agent.console.enums.ExtensionType;
import com.helianhealth.agent.repository.model.domain.NodeExtensionConfig;

/**
 * 扩展类型策略
 * <p>
 * 职责边界：仅负责各扩展类型在「关联字段」上的差异处理（节点 extension_param /
 * 节点 remote_extension / 工作流 route_extension）。
 * <ul>
 *   <li>{@link com.helianhealth.agent.repository.model.domain.NodeExtensionConfig} 本表 CRUD
 *       由 {@code DynamicSpiAdminService} 主干统一负责。</li>
 *   <li>SPI 运行时 {@code registerDynamicExtension}/{@code removeDynamicExtension}
 *       由主干统一负责（所有类型完全一致）。</li>
 * </ul>
 */
public interface ExtensionTypeStrategy {

    /** 策略适配的扩展类型 */
    ExtensionType supportType();

    /**
     * 创建或启用场景：写入关联字段。
     *
     * @param config 当前生效的扩展配置；绑定目标从 {@code config.bindingIds} 读取
     */
    void onRegister(NodeExtensionConfig config);

    /**
     * 更新场景：替换关联字段（清理旧名 + 写入新名）。
     *
     * @param newConfig 新扩展配置
     * @param oldConfig 旧扩展配置（可能为 {@code null}，表示旧记录不存在）
     */
    void onUpdate(NodeExtensionConfig newConfig, NodeExtensionConfig oldConfig);

    /**
     * 禁用或删除场景：清理关联字段。
     */
    void onUnregister(NodeExtensionConfig config);

    /**
     * 详情查询回显：把 domain 补齐（例如从节点 extension_param 回捞参数值）。
     * 默认不做任何事；仅有需要的策略覆写。
     */
    default void enrichDetail(NodeExtensionConfig config) {
    }
}
