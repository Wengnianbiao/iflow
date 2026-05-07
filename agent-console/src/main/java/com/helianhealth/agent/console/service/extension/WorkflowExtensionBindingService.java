package com.helianhealth.agent.console.service.extension;

import com.helianhealth.agent.console.controller.request.WorkflowExtensionBindingUpdateReq;
import com.helianhealth.agent.console.enums.ExtensionType;
import com.helianhealth.agent.console.model.dto.WorkflowExtensionBindingDTO;
import com.helianhealth.agent.repository.model.domain.NodeExtensionConfig;
import com.helianhealth.agent.repository.model.domain.Workflow;
import com.helianhealth.agent.repository.service.WorkflowCommandService;
import com.helianhealth.agent.repository.service.WorkflowQueryService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class WorkflowExtensionBindingService {

    private final WorkflowQueryService workflowQueryService;
    private final WorkflowCommandService workflowCommandService;
    private final DynamicSpiAdminService dynamicSpiAdminService;

    public WorkflowExtensionBindingService(WorkflowQueryService workflowQueryService,
                                           WorkflowCommandService workflowCommandService,
                                           @Lazy DynamicSpiAdminService dynamicSpiAdminService) {
        this.workflowQueryService = workflowQueryService;
        this.workflowCommandService = workflowCommandService;
        this.dynamicSpiAdminService = dynamicSpiAdminService;
    }

    public List<WorkflowExtensionBindingDTO> listAllBindings() {
        List<Workflow> workflows = workflowQueryService.selectAllWorkflows();
        return workflows.stream().map(this::toDto).collect(Collectors.toList());
    }

    public void updateBinding(WorkflowExtensionBindingUpdateReq request) {
        if (request == null || request.getFlowId() == null) {
            throw new IllegalArgumentException("flowId不能为空");
        }
        Workflow workflow = workflowQueryService.selectByFlowId(request.getFlowId());
        if (workflow == null) {
            throw new IllegalArgumentException("工作流不存在: flowId=" + request.getFlowId());
        }

        String routeExtension = normalize(request.getRouteExtension());
        if (routeExtension != null) {
            validateExtensionExists(routeExtension);
        }

        Workflow update = new Workflow();
        update.setFlowId(request.getFlowId());
        update.setRouteExtension(routeExtension);
        workflowCommandService.updateWorkflow(update);
    }

    private void validateExtensionExists(String extensionName) {
        Set<String> enabledNames = dynamicSpiAdminService.listExtensionsBySpiInterface(ExtensionType.WORKFLOW.getSpiInterface())
                .stream()
                .filter(cfg -> cfg.getEnabled() != null && cfg.getEnabled() == 1)
                .map(NodeExtensionConfig::getExtensionName)
                .collect(Collectors.toSet());
        enabledNames.addAll(
                dynamicSpiAdminService.listExtensionsBySpiInterface(ExtensionType.JARVIS_WORKFLOW.getSpiInterface())
                        .stream()
                        .filter(cfg -> cfg.getEnabled() != null && cfg.getEnabled() == 1)
                        .map(NodeExtensionConfig::getExtensionName)
                        .collect(Collectors.toSet())
        );
        if (!enabledNames.contains(extensionName)) {
            throw new IllegalArgumentException("未找到已启用的工作流扩展: " + extensionName);
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private WorkflowExtensionBindingDTO toDto(Workflow workflow) {
        WorkflowExtensionBindingDTO dto = new WorkflowExtensionBindingDTO();
        dto.setFlowId(workflow.getFlowId());
        dto.setFlowName(workflow.getFlowName());
        dto.setInterfaceUri(workflow.getInterfaceUri());
        dto.setStatus(workflow.getStatus());
        dto.setRouteExtension(workflow.getRouteExtension());
        return dto;
    }

    /**
     * Open API / Jarvis 工作流路由扩展注册或更新成功后调用：持久化 {@code interface_workflow.route_extension}，
     * 与节点上写入 {@code remote_extension}、向 ExtensionLoader 注册实现类同属「关联」语义。
     * <p>
     * 先解除当前扩展名及更新前扩展名在所有工作流上的占用，再若 {@code bindFlowId != null} 则绑定到该流程。
     */
    public void syncRouteExtensionBinding(Integer bindFlowId, String extensionName, String previousExtensionName) {
        String target = normalize(extensionName);
        if (target == null) {
            return;
        }
        validateExtensionExists(target);
        String prev = normalize(previousExtensionName);

        List<Workflow> workflows = workflowQueryService.selectAllWorkflows();
        for (Workflow w : workflows) {
            String re = normalize(w.getRouteExtension());
            if (re == null) {
                continue;
            }
            if (re.equals(target) || (prev != null && re.equals(prev))) {
                Workflow clear = new Workflow();
                clear.setFlowId(w.getFlowId());
                clear.setRouteExtension(null);
                workflowCommandService.updateWorkflow(clear);
            }
        }
        if (bindFlowId != null) {
            Workflow bind = new Workflow();
            bind.setFlowId(bindFlowId);
            bind.setRouteExtension(target);
            workflowCommandService.updateWorkflow(bind);
        }
    }
}
