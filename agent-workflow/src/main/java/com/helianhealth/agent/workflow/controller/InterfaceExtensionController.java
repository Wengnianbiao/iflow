package com.helianhealth.agent.workflow.controller;

import com.helianhealth.agent.workflow.model.domain.NodeExtensionConfigDO;
import com.helianhealth.agent.workflow.service.extension.DynamicSpiAdminService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 接口扩展专用控制器（InterfaceRemoteInvoker）
 * 
 * 功能：
 * - 单节点绑定
 * - 自动更新节点的 remote_extension 字段
 */
@RestController
@RequestMapping("/api/interface-extension")
@Slf4j
public class InterfaceExtensionController {

    private static final String SPI_INTERFACE = "com.helianhealth.agent.invocation.remote.InterfaceRemoteInvoker";

    @Autowired
    private DynamicSpiAdminService dynamicSpiAdminService;

    /**
     * 新增接口扩展配置并注册
     */
    @PostMapping("/register")
    public void register(@RequestBody NodeExtensionConfigDO configDO) {
        try {
            // 强制设置为 InterfaceRemoteInvoker 类型
            configDO.setSpiInterface(SPI_INTERFACE);
            
            // 验证只能绑定一个节点
            if (configDO.getNodeId() == null || configDO.getNodeId().isEmpty()) {
                throw new IllegalArgumentException("接口扩展必须绑定一个节点");
            }
            if (configDO.getNodeId().size() > 1) {
                throw new IllegalArgumentException("接口扩展只能绑定一个节点，不能绑定多个");
            }
            
            dynamicSpiAdminService.createInterfaceExtension(configDO);
            log.info("Successfully registered interface extension: {}", configDO.getExtensionName());
        } catch (Exception e) {
            log.error("Failed to register interface extension", e);
            throw e;
        }
    }

    /**
     * 更新接口扩展配置并热刷新
     */
    @PostMapping("/update")
    public void update(@RequestBody NodeExtensionConfigDO configDO) {
        try {
            // 强制设置为 InterfaceRemoteInvoker 类型
            configDO.setSpiInterface(SPI_INTERFACE);
            
            // 验证只能绑定一个节点
            if (configDO.getNodeId() == null || configDO.getNodeId().isEmpty()) {
                throw new IllegalArgumentException("接口扩展必须绑定一个节点");
            }
            if (configDO.getNodeId().size() > 1) {
                throw new IllegalArgumentException("接口扩展只能绑定一个节点，不能绑定多个");
            }
            
            dynamicSpiAdminService.updateInterfaceExtension(configDO);
            log.info("Successfully updated interface extension: {}", configDO.getExtensionName());
        } catch (Exception e) {
            log.error("Failed to update interface extension", e);
            throw e;
        }
    }

    /**
     * 禁用并卸载接口扩展
     */
    @PostMapping("/disable/{codeId}")
    public void disable(@PathVariable Integer codeId) {
        dynamicSpiAdminService.disableAndUnregister(codeId);
        log.info("Successfully disabled interface extension: {}", codeId);
    }

    /**
     * 刷新指定接口扩展
     */
    @PostMapping("/refresh/{codeId}")
    public void refresh(@PathVariable Integer codeId) throws Exception {
        dynamicSpiAdminService.refresh(codeId);
        log.info("Successfully refreshed interface extension: {}", codeId);
    }

    /**
     * 获取所有接口扩展配置列表
     */
    @GetMapping("/list")
    public List<NodeExtensionConfigDO> listInterfaceExtensions() {
        return dynamicSpiAdminService.listExtensionsBySpiInterface(SPI_INTERFACE);
    }
}
