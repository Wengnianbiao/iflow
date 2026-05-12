package com.helianhealth.agent.console.service.extension.strategy;

import com.helianhealth.agent.console.controller.request.WorkflowExtensionBindingUpdateReq;
import com.helianhealth.agent.console.enums.ExtensionType;
import com.helianhealth.agent.console.model.dto.WorkflowExtensionBindingDTO;
import com.helianhealth.agent.repository.model.domain.NodeExtensionConfig;
import com.helianhealth.agent.repository.model.domain.Workflow;
import com.helianhealth.agent.repository.service.NodeExtensionConfigQueryService;
import com.helianhealth.agent.repository.service.WorkflowCommandService;
import com.helianhealth.agent.repository.service.WorkflowQueryService;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 工作流路由扩展策略抽象基类。
 * <p>直接持有 {@code interface_workflow.route_extension} 字段的读写能力（类比 {@code RemoteStrategy} 持有节点字段读写）。
 * OpenApiRouteHandler 与 JarvisRouteHandler 共享本类实现，子类仅声明 {@link #supportType()}。
 */
public abstract class AbstractWorkflowRouteStrategy implements ExtensionTypeStrategy {

    protected final WorkflowQueryService workflowQueryService;
    protected final WorkflowCommandService workflowCommandService;
    protected final NodeExtensionConfigQueryService nodeExtensionConfigQueryService;

    protected AbstractWorkflowRouteStrategy(WorkflowQueryService workflowQueryService,
                                            WorkflowCommandService workflowCommandService,
                                            NodeExtensionConfigQueryService nodeExtensionConfigQueryService) {
        this.workflowQueryService = workflowQueryService;
        this.workflowCommandService = workflowCommandService;
        this.nodeExtensionConfigQueryService = nodeExtensionConfigQueryService;
    }

    @Override
    public void onRegister(NodeExtensionConfig config) {
        if (!config.isEnabled()) {
            return;
        }
        Integer bindFlowId = firstBindingId(config.getBindingIds());
        if (bindFlowId == null) {
            return;
        }
        syncRouteExtensionBinding(bindFlowId, config.getExtensionName(), null);
    }

    @Override
    public void onUpdate(NodeExtensionConfig newConfig, NodeExtensionConfig oldConfig) {
        if (!newConfig.isEnabled()) {
            return;
        }
        Integer bindFlowId = firstBindingId(newConfig.getBindingIds());
        String previousName = oldConfig != null ? oldConfig.getExtensionName() : null;
        syncRouteExtensionBinding(bindFlowId, newConfig.getExtensionName(), previousName);
    }

    @Override
    public void onUnregister(NodeExtensionConfig config) {
        // 禁用/删除：清空所有占用此扩展名的 workflow.route_extension
        syncRouteExtensionBinding(null, config.getExtensionName(), null);
    }

    /**
     * 管理面板：列出所有工作流的路由扩展绑定快照。
     */
    public List<WorkflowExtensionBindingDTO> listBindings() {
        return workflowQueryService.selectAllWorkflows().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * 管理面板：手工调整某个工作流的 route_extension 绑定。
     * 清空传 {@code routeExtension=null/""}，绑定时需保证目标扩展已启用。
     */
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
            validateExtensionEnabled(routeExtension);
        }

        workflowCommandService.updateRouteExtension(request.getFlowId(), routeExtension);
    }

    /**
     * 注册扩展时联动同步：先解除目标扩展名（及更新前扩展名）在所有工作流上的占用，
     * 再将 {@code bindFlowId} 指向的工作流绑定到新扩展名。
     */
    private void syncRouteExtensionBinding(Integer bindFlowId, String extensionName, String previousExtensionName) {
        String target = normalize(extensionName);
        if (target == null) {
            return;
        }
        String prev = normalize(previousExtensionName);

        List<Workflow> workflows = workflowQueryService.selectAllWorkflows();
        for (Workflow w : workflows) {
            String re = normalize(w.getRouteExtension());
            if (re == null) {
                continue;
            }
            if (re.equals(target) || (re.equals(prev))) {
                workflowCommandService.updateRouteExtension(w.getFlowId(), null);
            }
        }
        if (bindFlowId != null) {
            workflowCommandService.updateRouteExtension(bindFlowId, target);
        }
    }

    private void validateExtensionEnabled(String extensionName) {
        Set<String> enabledNames = nodeExtensionConfigQueryService
                .selectBySpiInterface(ExtensionType.WORKFLOW.getSpiInterface()).stream()
                .filter(cfg -> cfg.getEnabled() != null && cfg.getEnabled() == 1)
                .map(NodeExtensionConfig::getExtensionName)
                .collect(Collectors.toSet());
        enabledNames.addAll(
                nodeExtensionConfigQueryService
                        .selectBySpiInterface(ExtensionType.JARVIS_WORKFLOW.getSpiInterface()).stream()
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

    private Integer firstBindingId(List<Integer> bindingIds) {
        return (bindingIds == null || bindingIds.isEmpty()) ? null : bindingIds.get(0);
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
}
