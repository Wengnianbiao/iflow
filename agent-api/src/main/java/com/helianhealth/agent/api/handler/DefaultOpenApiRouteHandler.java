package com.helianhealth.agent.api.handler;

import com.helianhealth.agent.api.OpenApiRouteHandler;
import com.helianhealth.agent.api.adapter.ContentTypeAdapter;
import com.helianhealth.agent.repository.model.domain.Workflow;
import com.helianhealth.agent.repository.model.domain.WorkflowNode;
import com.helianhealth.agent.schedule.WorkflowScheduler;
import com.helianhealth.agent.schedule.builder.WorkFlowNodeBuilder;

import java.util.List;
import java.util.Map;

/**
 * Open API 默认工作流编排实现。
 */
public class DefaultOpenApiRouteHandler implements OpenApiRouteHandler {

    private final WorkflowScheduler workflowScheduler;
    private final WorkFlowNodeBuilder workFlowNodeBuilder;
    private final ContentTypeAdapter contentTypeAdapter;

    public DefaultOpenApiRouteHandler(WorkflowScheduler workflowScheduler,
                                      WorkFlowNodeBuilder workFlowNodeBuilder,
                                      ContentTypeAdapter contentTypeAdapter) {
        this.workflowScheduler = workflowScheduler;
        this.workFlowNodeBuilder = workFlowNodeBuilder;
        this.contentTypeAdapter = contentTypeAdapter;
    }

    @Override
    public String handle(Workflow workflow, String requestBody) {
        Map<String, Object> businessData = contentTypeAdapter.adaptRequest(workflow, requestBody);
        List<WorkflowNode> workflowNodes = workFlowNodeBuilder.build(workflow.getFirstFlowNodes());
        Map<String, Object> scheduleRsp = workflowScheduler.schedule(workflowNodes, businessData);
        return contentTypeAdapter.adaptResponse(workflow, scheduleRsp);
    }
}
