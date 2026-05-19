package com.helianhealth.agent.ai.mcp.dispatcher;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.agent.common.enums.WorkflowContentType;
import com.helianhealth.agent.repository.model.domain.Workflow;
import com.helianhealth.agent.repository.service.WorkflowCommandService;
import com.helianhealth.agent.repository.service.WorkflowQueryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Tool: {@code agent_workflow} —— 工作流（Workflow）域级 Tool 分发器。
 *
 * <p>支持 action：list / get / create / update / delete。</p>
 */
@Slf4j
@Component
public class WorkflowToolDispatcher extends McpDispatcherSupport implements McpToolDispatcher {

    public static final String TOOL_NAME = "agent_workflow";

    private final WorkflowCommandService workflowCommandService;
    private final WorkflowQueryService workflowQueryService;

    public WorkflowToolDispatcher(ObjectMapper objectMapper,
                                  WorkflowCommandService workflowCommandService,
                                  WorkflowQueryService workflowQueryService) {
        super(objectMapper);
        this.workflowCommandService = workflowCommandService;
        this.workflowQueryService = workflowQueryService;
    }

    @Override
    public String toolName() {
        return TOOL_NAME;
    }

    @Override
    public Object call(JsonNode arguments) {
        String action = requireAction(arguments);
        switch (action) {
            case "list":
                return workflowQueryService.selectAllWorkflows();
            case "get":
                return getWorkflow(requireInt(arguments, "flowId"));
            case "create":
                return createWorkflow(arguments);
            case "update":
                return updateWorkflow(arguments);
            case "delete":
                workflowCommandService.deleteWorkflow(requireInt(arguments, "flowId"));
                return "工作流删除成功";
            default:
                throw new IllegalArgumentException("不支持的 action: " + action);
        }
    }

    private Workflow getWorkflow(Integer flowId) {
        Workflow workflow = workflowQueryService.selectByFlowId(flowId);
        if (workflow == null) {
            throw new IllegalArgumentException("工作流不存在: flowId=" + flowId);
        }
        return workflow;
    }

    private Integer createWorkflow(JsonNode args) {
        Workflow workflow = Workflow.builder()
                .flowName(requireString(args, "flowName"))
                .interfaceUri(requireString(args, "interfaceUri"))
                .routeExtension(routeExtension(args))
                .contentType(parseContentType(optString(args, "contentType")))
                .contentMetaInfo(optString(args, "contentMetaInfo"))
                .firstFlowNodes(convertSubNodeList(args, "firstFlowNodes", Integer.class))
                .status(optInt(args, "status"))
                .build();
        workflowCommandService.createWorkflow(workflow);
        return workflow.getFlowId();
    }

    private Integer updateWorkflow(JsonNode args) {
        Workflow workflow = Workflow.builder()
                .flowId(requireInt(args, "flowId"))
                .flowName(optString(args, "flowName"))
                .interfaceUri(optString(args, "interfaceUri"))
                .routeExtension(routeExtension(args))
                .contentType(parseContentType(optString(args, "contentType")))
                .contentMetaInfo(optString(args, "contentMetaInfo"))
                .firstFlowNodes(convertSubNodeList(args, "firstFlowNodes", Integer.class))
                .status(optInt(args, "status"))
                .build();
        return workflowCommandService.updateWorkflow(workflow);
    }

    /** MCP 入参字段与 {@code WorkflowCreateMcpRequest} / {@code WorkflowUpdateMcpRequest} 的 routeExtension 一致。 */
    private String routeExtension(JsonNode args) {
        return optString(args, "routeExtension");
    }

    private WorkflowContentType parseContentType(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return WorkflowContentType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("contentType 无效: " + value + "，可选: JSON / XML");
        }
    }

    @SuppressWarnings("unused")
    private List<Integer> noop() {
        return null;
    }
}
