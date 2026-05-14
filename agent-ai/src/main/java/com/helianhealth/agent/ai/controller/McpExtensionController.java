package com.helianhealth.agent.ai.controller;

import com.helianhealth.agent.ai.convert.ExtensionMcpConverter;
import com.helianhealth.agent.ai.model.ExtensionMcpView;
import com.helianhealth.agent.ai.model.ExtensionRegisterMcpRequest;
import com.helianhealth.agent.ai.model.ExtensionUpdateMcpRequest;
import com.helianhealth.agent.common.base.ResultData;
import com.helianhealth.agent.console.enums.ExtensionType;
import com.helianhealth.agent.console.service.extension.DynamicSpiAdminService;
import com.helianhealth.agent.repository.model.domain.NodeExtensionConfig;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * MCP接口 - 扩展管理
 * 提供给AI使用的过滤器、Invoker、Handler等扩展配置接口
 */
@RestController
@RequestMapping("/v1/ai/mcp/extension")
@Slf4j
@AllArgsConstructor
public class McpExtensionController {

    private final DynamicSpiAdminService dynamicSpiAdminService;

    /**
     * 根据类型查询扩展列表
     * @param type 扩展类型 (filter, remote, workflow, jarvis-workflow)
     */
    @GetMapping("/{type}/list")
    public ResultData<List<ExtensionMcpView>> listExtensions(@PathVariable String type) {
        try {
            log.info("MCP查询扩展列表: type={}", type);
            ExtensionType extensionType = ExtensionType.fromCode(type);
            List<NodeExtensionConfig> configs = dynamicSpiAdminService.listExtensionsBySpiInterface(extensionType.getSpiInterface());
            return ResultData.ok(ExtensionMcpConverter.toMcpViewList(configs));
        } catch (Exception e) {
            log.error("MCP查询扩展列表失败", e);
            return ResultData.error("查询扩展列表失败: " + e.getMessage());
        }
    }

    /**
     * 注册新扩展
     */
    @PostMapping("/{type}/register")
    public ResultData<String> registerExtension(@PathVariable String type, @RequestBody ExtensionRegisterMcpRequest request) {
        try {
            log.info("MCP注册扩展: type={}, name={}", type, request.getExtensionName());
            ExtensionType extensionType = ExtensionType.fromCode(type);
            dynamicSpiAdminService.registerExtension(extensionType, ExtensionMcpConverter.toCreateReq(request));
            return ResultData.ok("注册成功");
        } catch (Exception e) {
            log.error("MCP注册扩展失败", e);
            return ResultData.error("注册失败: " + e.getMessage());
        }
    }

    /**
     * 更新扩展
     */
    @PostMapping("/{type}/update")
    public ResultData<String> updateExtension(@PathVariable String type, @RequestBody ExtensionUpdateMcpRequest request) {
        try {
            log.info("MCP更新扩展: type={}, configId={}", type, request.getConfigId());
            ExtensionType extensionType = ExtensionType.fromCode(type);
            dynamicSpiAdminService.updateExtension(extensionType, ExtensionMcpConverter.toUpdateReq(request));
            return ResultData.ok("更新成功");
        } catch (Exception e) {
            log.error("MCP更新扩展失败", e);
            return ResultData.error("更新失败: " + e.getMessage());
        }
    }

    /**
     * 删除扩展
     */
    @DeleteMapping("/delete/{configId}")
    public ResultData<String> deleteExtension(@PathVariable Integer configId) {
        try {
            log.info("MCP删除扩展: configId={}", configId);
            dynamicSpiAdminService.deleteExtension(configId);
            return ResultData.ok("删除成功");
        } catch (Exception e) {
            log.error("MCP删除扩展失败", e);
            return ResultData.error("删除失败: " + e.getMessage());
        }
    }

    /**
     * 启用扩展
     */
    @PostMapping("/enable/{configId}")
    public ResultData<String> enableExtension(@PathVariable Integer configId) {
        try {
            log.info("MCP启用扩展: configId={}", configId);
            dynamicSpiAdminService.enableAndRegister(configId);
            return ResultData.ok("启用成功");
        } catch (Exception e) {
            log.error("MCP启用扩展失败", e);
            return ResultData.error("启用失败: " + e.getMessage());
        }
    }

    /**
     * 禁用扩展
     */
    @PostMapping("/disable/{configId}")
    public ResultData<String> disableExtension(@PathVariable Integer configId) {
        try {
            log.info("MCP禁用扩展: configId={}", configId);
            dynamicSpiAdminService.disableAndUnregister(configId);
            return ResultData.ok("禁用成功");
        } catch (Exception e) {
            log.error("MCP禁用扩展失败", e);
            return ResultData.error("禁用失败: " + e.getMessage());
        }
    }
}
