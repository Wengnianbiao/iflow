package com.helianhealth.agent.console.service.extension.strategy;

import com.helianhealth.agent.repository.model.domain.NodeExtensionConfig;
import com.helianhealth.agent.repository.model.domain.Workflow;
import com.helianhealth.agent.repository.service.WorkflowCommandService;
import com.helianhealth.agent.repository.service.WorkflowQueryService;

import java.util.List;

/**
 * 工作流路由扩展策略抽象基类。
 * <p>直接持有 {@code interface_workflow.route_extension} 字段的写入能力（类比 {@code RemoteStrategy} 持有节点字段读写）。
 * OpenApiRouteHandler 与 JarvisRouteHandler 共享本类实现，子类仅声明 {@link #supportType()}。
 * <p>对外仅通过扩展配置的 register/update/enable/disable/delete 触发 {@link #onRegister}/{@link #onUpdate}/{@link #onUnregister} 同步路由列；
 * 不单独暴露工作流绑定 HTTP 语义。
 */
public abstract class AbstractWorkflowRouteStrategy implements ExtensionTypeStrategy {

    protected final WorkflowQueryService workflowQueryService;
    protected final WorkflowCommandService workflowCommandService;

    protected AbstractWorkflowRouteStrategy(WorkflowQueryService workflowQueryService,
                                            WorkflowCommandService workflowCommandService) {
        this.workflowQueryService = workflowQueryService;
        this.workflowCommandService = workflowCommandService;
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
}
