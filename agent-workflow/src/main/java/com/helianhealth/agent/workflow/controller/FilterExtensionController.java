package com.helianhealth.agent.workflow.controller;

import com.helianhealth.agent.workflow.model.domain.NodeExtensionConfigDO;
import com.helianhealth.agent.workflow.service.extension.DynamicSpiAdminService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 过滤器扩展专用控制器（AgentFilter）
 * 
 * 功能：
 * - 多节点绑定
 * - 自动更新节点的 extension_param 字段（JSON格式，key: "filter.{extensionName}", value: "true"）
 */
@RestController
@RequestMapping("/api/filter-extension")
@Slf4j
public class FilterExtensionController {

    private static final String SPI_INTERFACE = "com.helianhealth.agent.invocation.remote.filter.AgentFilter";

    @Autowired
    private DynamicSpiAdminService dynamicSpiAdminService;

    /**
     * 新增过滤器扩展配置并注册
     */
    @PostMapping("/register")
    public void register(@RequestBody NodeExtensionConfigDO configDO) {
        try {
            // 强制设置为 AgentFilter 类型
            configDO.setSpiInterface(SPI_INTERFACE);
            
            // 验证至少绑定一个节点
            if (configDO.getNodeId() == null || configDO.getNodeId().isEmpty()) {
                throw new IllegalArgumentException("过滤器扩展必须至少绑定一个节点");
            }
            
            dynamicSpiAdminService.createFilterExtension(configDO);
            log.info("Successfully registered filter extension: {}, bound to {} nodes", 
                    configDO.getExtensionName(), configDO.getNodeId().size());
        } catch (Exception e) {
            log.error("Failed to register filter extension", e);
            throw e;
        }
    }

    /**
     * 更新过滤器扩展配置并热刷新
     */
    @PostMapping("/update")
    public void update(@RequestBody NodeExtensionConfigDO configDO) {
        try {
            // 强制设置为 AgentFilter 类型
            configDO.setSpiInterface(SPI_INTERFACE);
            
            // 验证至少绑定一个节点
            if (configDO.getNodeId() == null || configDO.getNodeId().isEmpty()) {
                throw new IllegalArgumentException("过滤器扩展必须至少绑定一个节点");
            }
            
            dynamicSpiAdminService.updateFilterExtension(configDO);
            log.info("Successfully updated filter extension: {}, bound to {} nodes", 
                    configDO.getExtensionName(), configDO.getNodeId().size());
        } catch (Exception e) {
            log.error("Failed to update filter extension", e);
            throw e;
        }
    }

    /**
     * 禁用并卸载过滤器扩展
     */
    @PostMapping("/disable/{codeId}")
    public void disable(@PathVariable Integer codeId) {
        dynamicSpiAdminService.disableAndUnregister(codeId);
        log.info("Successfully disabled filter extension: {}", codeId);
    }

    /**
     * 刷新指定过滤器扩展
     */
    @PostMapping("/refresh/{codeId}")
    public void refresh(@PathVariable Integer codeId) throws Exception {
        dynamicSpiAdminService.refresh(codeId);
        log.info("Successfully refreshed filter extension: {}", codeId);
    }

    /**
     * 获取所有过滤器扩展配置列表
     */
    @GetMapping("/list")
    public List<NodeExtensionConfigDO> listFilterExtensions() {
        return dynamicSpiAdminService.listExtensionsBySpiInterface(SPI_INTERFACE);
    }
}
