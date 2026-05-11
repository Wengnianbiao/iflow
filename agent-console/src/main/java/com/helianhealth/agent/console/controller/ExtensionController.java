package com.helianhealth.agent.console.controller;

import com.helianhealth.agent.console.controller.request.NodeExtensionConfigCreateReq;
import com.helianhealth.agent.console.controller.request.WorkflowExtensionBindingUpdateReq;
import com.helianhealth.agent.console.enums.ExtensionType;
import com.helianhealth.agent.console.model.dto.NodeExtensionConfigDetailResponse;
import com.helianhealth.agent.console.model.dto.NodeExtensionConfigUpdateReq;
import com.helianhealth.agent.console.model.dto.WorkflowExtensionBindingDTO;
import com.helianhealth.agent.console.service.extension.DynamicSpiAdminService;
import com.helianhealth.agent.repository.model.domain.NodeExtensionConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/extension")
@Slf4j
public class ExtensionController {

    private final DynamicSpiAdminService dynamicSpiAdminService;

    public ExtensionController(DynamicSpiAdminService dynamicSpiAdminService) {
        this.dynamicSpiAdminService = dynamicSpiAdminService;
    }

    @PostMapping("/{extensionType}/register")
    public void registerExtension(@PathVariable String extensionType, @RequestBody NodeExtensionConfigCreateReq request) {
        ExtensionType type = ExtensionType.fromCode(extensionType);
        request.setSpiInterface(type.getSpiInterface());

        dynamicSpiAdminService.registerExtension(type, request);
    }

    @PostMapping("/{extensionType}/update")
    public void updateExtension(@PathVariable String extensionType, @RequestBody NodeExtensionConfigUpdateReq request) {
        ExtensionType type = ExtensionType.fromCode(extensionType);
        request.setSpiInterface(type.getSpiInterface());

        dynamicSpiAdminService.updateExtension(type, request);
    }

    @PostMapping("/disable/{configId}")
    public void disable(@PathVariable Integer configId) {
        dynamicSpiAdminService.disableAndUnregister(configId);
    }

    @PostMapping("/enable/{configId}")
    public void enable(@PathVariable Integer configId) {
        dynamicSpiAdminService.enableAndRegister(configId);
    }

    @PostMapping("/delete/{configId}")
    public void delete(@PathVariable Integer configId) {
        dynamicSpiAdminService.deleteExtension(configId);
    }

    @GetMapping("/{extensionType}/list")
    public List<NodeExtensionConfig> listByType(@PathVariable String extensionType) {
        ExtensionType type = ExtensionType.fromCode(extensionType);
        return dynamicSpiAdminService.listExtensionsBySpiInterface(type.getSpiInterface());
    }

    @GetMapping("/detail/{configId}")
    public NodeExtensionConfigDetailResponse getDetail(@PathVariable Integer configId) {
        return dynamicSpiAdminService.getExtensionDetailById(configId);
    }

    @GetMapping("/enabled-configs")
    public List<NodeExtensionConfig> listEnabledConfigs() {
        return dynamicSpiAdminService.listEnabledConfigs();
    }

    @GetMapping("/supported-spi-interfaces")
    public List<String> getSupportedSpiInterfaces() {
        return dynamicSpiAdminService.getSupportedSpiInterfaces();
    }

    @GetMapping("/types")
    public ExtensionType[] getSupportedTypes() {
        return ExtensionType.values();
    }

    // ==================== 工作流-路由扩展绑定（维护 interface_workflow.route_extension）====================

    @GetMapping("/workflow/binding/list")
    public List<WorkflowExtensionBindingDTO> listWorkflowBindings() {
        return dynamicSpiAdminService.listWorkflowBindings();
    }

    @PostMapping("/workflow/binding/update")
    public void updateWorkflowBinding(@RequestBody WorkflowExtensionBindingUpdateReq request) {
        dynamicSpiAdminService.updateWorkflowBinding(request);
    }

    private void validateNodeBindingForUpdate(ExtensionType type, NodeExtensionConfigUpdateReq request) {
        if (!isNodeScopedType(type)) {
            return;
        }
        if (type.isMultiNodeSupported()) {
            // 多节点类型：至少绑定一个节点
            if (request.getBindingIds() == null || request.getBindingIds().isEmpty()) {
                throw new IllegalArgumentException(type.getDescription() + "必须至少绑定一个节点");
            }
        }
        // 单节点类型不做强制校验，允许为空（通用扩展）
    }

    private boolean isNodeScopedType(ExtensionType type) {
        return type == ExtensionType.REMOTE || type == ExtensionType.FILTER;
    }
}
