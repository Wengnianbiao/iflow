package com.helianhealth.agent.console.controller;

import com.helianhealth.agent.console.enums.ExtensionType;
import com.helianhealth.agent.console.model.domain.NodeExtensionConfigDO;
import com.helianhealth.agent.console.service.extension.DynamicSpiAdminService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 扩展配置统一管理控制器
 * 职责：
 * - 参数接收、校验
 * - 调用Service执行业务逻辑
 * - 返回响应
 */
@RestController
@RequestMapping("/api/extension")
@Slf4j
public class ExtensionController {

    @Autowired
    private DynamicSpiAdminService dynamicSpiAdminService;

    /**
     * 新增扩展配置并注册
     * @param extensionType 扩展类型（filter/invoker）
     * @param configDO 扩展配置
     */
    @PostMapping("/{extensionType}/register")
    public void register(@PathVariable String extensionType, @RequestBody NodeExtensionConfigDO configDO) {
        ExtensionType type = ExtensionType.fromCode(extensionType);
        configDO.setSpiInterface(type.getSpiInterface());
        validateNodeBinding(type, configDO);

        dynamicSpiAdminService.registerExtension(type, configDO);
    }

    /**
     * 更新扩展配置并热刷新
     * @param extensionType 扩展类型（filter/remote）
     * @param configDO 扩展配置
     */
    @PostMapping("/{extensionType}/update")
    public void update(@PathVariable String extensionType, @RequestBody NodeExtensionConfigDO configDO) {
        ExtensionType type = ExtensionType.fromCode(extensionType);
        configDO.setSpiInterface(type.getSpiInterface());
        validateNodeBinding(type, configDO);

        dynamicSpiAdminService.updateExtension(type, configDO);
    }

    /**
     * 禁用并卸载扩展
     * @param codeId 配置ID
     */
    @PostMapping("/disable/{codeId}")
    public void disable(@PathVariable Integer codeId) {
        dynamicSpiAdminService.disableAndUnregister(codeId);
        log.info("成功禁用扩展: {}", codeId);
    }

    /**
     * 刷新指定扩展
     * @param codeId 配置ID
     */
    @PostMapping("/refresh/{codeId}")
    public void refresh(@PathVariable Integer codeId) throws Exception {
        dynamicSpiAdminService.refresh(codeId);
        log.info("成功刷新扩展: {}", codeId);
    }

    /**
     * 获取指定类型的扩展配置列表
     * @param extensionType 扩展类型（filter/remote）
     */
    @GetMapping("/{extensionType}/list")
    public List<NodeExtensionConfigDO> listByType(@PathVariable String extensionType) {
        ExtensionType type = ExtensionType.fromCode(extensionType);
        return dynamicSpiAdminService.listExtensionsBySpiInterface(type.getSpiInterface());
    }

    /**
     * 获取所有启用的扩展配置
     */
    @GetMapping("/enabled-configs")
    public List<NodeExtensionConfigDO> listEnabledConfigs() {
        return dynamicSpiAdminService.listEnabledConfigs();
    }

    /**
     * 获取系统支持的所有 SPI 接口列表
     */
    @GetMapping("/supported-spi-interfaces")
    public List<String> getSupportedSpiInterfaces() {
        return dynamicSpiAdminService.getSupportedSpiInterfaces();
    }

    /**
     * 获取所有支持的扩展类型
     */
    @GetMapping("/types")
    public ExtensionType[] getSupportedTypes() {
        return ExtensionType.values();
    }

    /**
     * 校验节点绑定
     */
    private void validateNodeBinding(ExtensionType type, NodeExtensionConfigDO configDO) {
        if (type.isMultiNodeSupported()) {
            // 多节点类型：至少绑定一个节点
            if (configDO.getNodeId() == null || configDO.getNodeId().isEmpty()) {
                throw new IllegalArgumentException(type.getDescription() + "必须至少绑定一个节点");
            }
        }
        // 单节点类型不做强制校验，允许为空（通用扩展）
    }
}
