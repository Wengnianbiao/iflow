package com.helianhealth.agent.repository.service.extension.strategy.impl;

import com.helianhealth.agent.common.enums.ExtensionType;
import com.helianhealth.agent.repository.service.WorkflowCommandService;
import com.helianhealth.agent.repository.service.WorkflowQueryService;
import org.springframework.stereotype.Component;

/**
 * OpenAPI 工作流路由扩展策略。
 */
@Component
public class OpenApiRouteStrategy extends AbstractWorkflowRouteStrategy {

    public OpenApiRouteStrategy(WorkflowQueryService workflowQueryService,
                                WorkflowCommandService workflowCommandService) {
        super(workflowQueryService, workflowCommandService);
    }

    @Override
    public ExtensionType supportType() {
        return ExtensionType.WORKFLOW;
    }
}
