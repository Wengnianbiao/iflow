package com.helianhealth.agent.workflow.controller;

import com.helianhealth.agent.common.extension.dynamic.DynamicSpiManager;
import com.helianhealth.agent.workflow.model.domain.NodeExtensionConfigDO;
import com.helianhealth.agent.workflow.service.extension.DynamicSpiAdminService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 节点扩展配置管理接口
 *
 * 说明：
 * - 配置数据由 workflow 模块管理（NodeExtensionConfigDO / NodeExtensionConfigMapper）
 * - extension 模块负责动态编译与运行时管理（DynamicSpiManager）
 * - 本控制器桥接两者，提供简单的运行时管理能力
 */
@RestController
@RequestMapping("/api/node-extension")
@Slf4j
public class NodeExtensionController {

    @Autowired
    private DynamicSpiManager dynamicSpiManager;

    @Autowired
    private DynamicSpiAdminService dynamicSpiAdminService;

    /**
     * 按配置 ID 刷新指定节点扩展
     */
    @PostMapping("/refresh/{codeId}")
    public void refresh(@PathVariable Integer codeId) throws Exception {
        dynamicSpiAdminService.refresh(codeId);
    }

    /**
     * 新增节点扩展配置并注册
     */
    @PostMapping("/register")
    public void register(@RequestBody NodeExtensionConfigDO configDO) {
        try {
            dynamicSpiAdminService.createAndRegister(configDO);
        } catch (Exception e) {
            log.error("Exception occurred while registering node extension", e);
            throw e;
        }
    }

    /**
     * 更新节点扩展配置并热刷新
     */
    @PostMapping("/update")
    public void update(@RequestBody NodeExtensionConfigDO configDO) throws Exception {
        dynamicSpiAdminService.updateAndRefresh(configDO);
    }

    /**
     * 禁用并卸载节点扩展
     */
    @PostMapping("/disable/{codeId}")
    public void disable(@PathVariable Integer codeId)  {
        dynamicSpiAdminService.disableAndUnregister(codeId);
    }

    /**
     * 查看当前已加载的节点扩展
     */
    @GetMapping("/loaded")
    public Map<String, Class<?>> listLoadedExtensions() {
        return dynamicSpiManager.getAllRegisteredExtensions();
    }

    /**
     * List all enabled node extension configurations from database
     */
    @GetMapping("/enabled-configs")
    public List<NodeExtensionConfigDO> listEnabledConfigs() {
        return dynamicSpiAdminService.listEnabledConfigs();
    }
}
