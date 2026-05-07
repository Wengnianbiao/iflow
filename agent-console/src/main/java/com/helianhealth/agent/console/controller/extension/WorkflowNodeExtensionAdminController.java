package com.helianhealth.agent.console.controller.extension;

import com.helianhealth.agent.console.controller.request.NodeExtensionConfigCreateReq;
import com.helianhealth.agent.console.enums.ExtensionType;
import com.helianhealth.agent.console.model.dto.NodeExtensionConfigDetailResponse;
import com.helianhealth.agent.console.model.dto.NodeExtensionConfigUpdateReq;
import com.helianhealth.agent.console.service.extension.DynamicSpiAdminService;
import com.helianhealth.agent.repository.model.domain.NodeExtensionConfig;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/extension/workflow-node")
public class WorkflowNodeExtensionAdminController {

    private final DynamicSpiAdminService dynamicSpiAdminService;

    public WorkflowNodeExtensionAdminController(DynamicSpiAdminService dynamicSpiAdminService) {
        this.dynamicSpiAdminService = dynamicSpiAdminService;
    }

    @GetMapping("/list")
    public List<NodeExtensionConfig> listNodeExtensions() {
        return dynamicSpiAdminService.listExtensionsBySpiInterface(ExtensionType.REMOTE.getSpiInterface());
    }

    @PostMapping("/register")
    public void registerNodeExtension(@RequestBody NodeExtensionConfigCreateReq request) {
        request.setSpiInterface(ExtensionType.REMOTE.getSpiInterface());
        dynamicSpiAdminService.registerExtension(ExtensionType.REMOTE, request);
    }

    @PostMapping("/update")
    public void updateNodeExtension(@RequestBody NodeExtensionConfigUpdateReq request) {
        request.setSpiInterface(ExtensionType.REMOTE.getSpiInterface());
        dynamicSpiAdminService.updateExtension(ExtensionType.REMOTE, request);
    }

    @GetMapping("/detail/{configId}")
    public NodeExtensionConfigDetailResponse getDetail(@PathVariable Integer configId) {
        return dynamicSpiAdminService.getExtensionDetailById(configId);
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
}
