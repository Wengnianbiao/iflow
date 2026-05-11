package com.helianhealth.agent.console.service.extension.strategy;

import com.helianhealth.agent.console.enums.ExtensionType;
import com.helianhealth.agent.repository.service.NodeExtensionConfigQueryService;
import com.helianhealth.agent.repository.service.WorkflowCommandService;
import com.helianhealth.agent.repository.service.WorkflowQueryService;
import org.springframework.stereotype.Component;

/**
 * OpenAPI 工作流路由扩展策略。
 */
@Component
public class OpenApiRouteStrategy extends AbstractWorkflowRouteStrategy {

    public OpenApiRouteStrategy(WorkflowQueryService workflowQueryService,
                                WorkflowCommandService workflowCommandService,
                                NodeExtensionConfigQueryService nodeExtensionConfigQueryService) {
        super(workflowQueryService, workflowCommandService, nodeExtensionConfigQueryService);
    }

    @Override
    public ExtensionType supportType() {
        return ExtensionType.WORKFLOW;
    }
}
