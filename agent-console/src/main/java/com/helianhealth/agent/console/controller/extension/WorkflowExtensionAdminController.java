package com.helianhealth.agent.console.controller.extension;

import com.helianhealth.agent.console.controller.request.NodeExtensionConfigCreateReq;
import com.helianhealth.agent.console.controller.request.WorkflowExtensionBindingUpdateReq;
import com.helianhealth.agent.console.enums.ExtensionType;
import com.helianhealth.agent.console.model.dto.NodeExtensionConfigDetailResponse;
import com.helianhealth.agent.console.model.dto.NodeExtensionConfigUpdateReq;
import com.helianhealth.agent.console.model.dto.WorkflowExtensionBindingDTO;
import com.helianhealth.agent.console.service.extension.DynamicSpiAdminService;
import com.helianhealth.agent.console.service.extension.WorkflowExtensionBindingService;
import com.helianhealth.agent.repository.model.domain.NodeExtensionConfig;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/extension/workflow")
public class WorkflowExtensionAdminController {

    private final DynamicSpiAdminService dynamicSpiAdminService;
    private final WorkflowExtensionBindingService workflowExtensionBindingService;

    public WorkflowExtensionAdminController(DynamicSpiAdminService dynamicSpiAdminService,
                                            WorkflowExtensionBindingService workflowExtensionBindingService) {
        this.dynamicSpiAdminService = dynamicSpiAdminService;
        this.workflowExtensionBindingService = workflowExtensionBindingService;
    }

    @GetMapping("/open-api/list")
    public List<NodeExtensionConfig> listOpenApiExtensions() {
        return dynamicSpiAdminService.listExtensionsBySpiInterface(ExtensionType.WORKFLOW.getSpiInterface());
    }

    @PostMapping("/open-api/register")
    public void registerOpenApiExtension(@RequestBody NodeExtensionConfigCreateReq request) {
        request.setSpiInterface(ExtensionType.WORKFLOW.getSpiInterface());
        dynamicSpiAdminService.registerExtension(ExtensionType.WORKFLOW, request);
    }

    @PostMapping("/open-api/update")
    public void updateOpenApiExtension(@RequestBody NodeExtensionConfigUpdateReq request) {
        request.setSpiInterface(ExtensionType.WORKFLOW.getSpiInterface());
        dynamicSpiAdminService.updateExtension(ExtensionType.WORKFLOW, request);
    }

    @GetMapping("/jarvis/list")
    public List<NodeExtensionConfig> listJarvisExtensions() {
        return dynamicSpiAdminService.listExtensionsBySpiInterface(ExtensionType.JARVIS_WORKFLOW.getSpiInterface());
    }

    @PostMapping("/jarvis/register")
    public void registerJarvisExtension(@RequestBody NodeExtensionConfigCreateReq request) {
        request.setSpiInterface(ExtensionType.JARVIS_WORKFLOW.getSpiInterface());
        dynamicSpiAdminService.registerExtension(ExtensionType.JARVIS_WORKFLOW, request);
    }

    @PostMapping("/jarvis/update")
    public void updateJarvisExtension(@RequestBody NodeExtensionConfigUpdateReq request) {
        request.setSpiInterface(ExtensionType.JARVIS_WORKFLOW.getSpiInterface());
        dynamicSpiAdminService.updateExtension(ExtensionType.JARVIS_WORKFLOW, request);
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

    @GetMapping("/binding/list")
    public List<WorkflowExtensionBindingDTO> listWorkflowBindings() {
        return workflowExtensionBindingService.listAllBindings();
    }

    @PostMapping("/binding/update")
    public void updateWorkflowBinding(@RequestBody WorkflowExtensionBindingUpdateReq request) {
        workflowExtensionBindingService.updateBinding(request);
    }

}
