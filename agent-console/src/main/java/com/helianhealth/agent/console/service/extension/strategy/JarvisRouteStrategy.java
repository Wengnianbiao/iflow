package com.helianhealth.agent.console.service.extension.strategy;

import com.helianhealth.agent.console.enums.ExtensionType;
import com.helianhealth.agent.repository.service.WorkflowCommandService;
import com.helianhealth.agent.repository.service.WorkflowQueryService;
import org.springframework.stereotype.Component;

/**
 * Jarvis 工作流路由扩展策略。
 */
@Component
public class JarvisRouteStrategy extends AbstractWorkflowRouteStrategy {

    public JarvisRouteStrategy(WorkflowQueryService workflowQueryService,
                               WorkflowCommandService workflowCommandService) {
        super(workflowQueryService, workflowCommandService);
    }

    @Override
    public ExtensionType supportType() {
        return ExtensionType.JARVIS_WORKFLOW;
    }
}
