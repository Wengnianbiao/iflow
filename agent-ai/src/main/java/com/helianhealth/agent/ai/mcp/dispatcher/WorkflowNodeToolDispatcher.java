package com.helianhealth.agent.ai.mcp.dispatcher;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.agent.ai.model.WorkflowNodeMcpView;
import com.helianhealth.agent.common.enums.NodeType;
import com.helianhealth.agent.common.enums.ScheduleParamSourceType;
import com.helianhealth.agent.common.utils.JsonUtils;
import com.helianhealth.agent.repository.model.domain.WorkflowNode;
import com.helianhealth.agent.repository.service.WorkflowNodeCommandService;
import com.helianhealth.agent.repository.service.WorkflowNodeQueryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Tool: {@code agent_workflow_node} —— 工作流节点域级 Tool 分发器。
 */
@Slf4j
@Component
public class WorkflowNodeToolDispatcher extends McpDispatcherSupport implements McpToolDispatcher {

    public static final String TOOL_NAME = "agent_workflow_node";

    private final WorkflowNodeCommandService workflowNodeCommandService;
    private final WorkflowNodeQueryService workflowNodeQueryService;

    public WorkflowNodeToolDispatcher(ObjectMapper objectMapper,
                                      WorkflowNodeCommandService workflowNodeCommandService,
                                      WorkflowNodeQueryService workflowNodeQueryService) {
        super(objectMapper);
        this.workflowNodeCommandService = workflowNodeCommandService;
        this.workflowNodeQueryService = workflowNodeQueryService;
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
                return listAll();
            case "get":
                return getNode(requireInt(arguments, "nodeId"));
            case "create":
                return createNode(arguments);
            case "update":
                return updateNode(arguments);
            case "delete":
                workflowNodeCommandService.deleteNode(requireInt(arguments, "nodeId"));
                return "工作流节点删除成功";
            default:
                throw new IllegalArgumentException("不支持的 action: " + action);
        }
    }

    private List<WorkflowNodeMcpView> listAll() {
        return workflowNodeQueryService.selectAllNodes(null).stream()
                .map(this::toNodeView)
                .collect(Collectors.toList());
    }

    private WorkflowNode getNode(Integer nodeId) {
        WorkflowNode node = workflowNodeQueryService.selectByNodeId(nodeId);
        if (node == null) {
            throw new IllegalArgumentException("工作流节点不存在: nodeId=" + nodeId);
        }
        return node;
    }

    private Integer createNode(JsonNode args) {
        WorkflowNode node = WorkflowNode.builder()
                .nodeName(requireString(args, "nodeName"))
                .flowId(requireInt(args, "flowId"))
                .nodeType(parseNodeType(requireString(args, "nodeType")))
                .metaInfo(optString(args, "metaInfo"))
                .paramFilterExpr(optString(args, "paramFilterExpr"))
                .splitField(optString(args, "splitField"))
                .keywordField(optString(args, "keywordField"))
                .scheduleExpr(optString(args, "scheduleExpr"))
                .scheduleParamSourceType(parseScheduleParamSourceType(optString(args, "scheduleParamSourceType")))
                .remoteExtension(optString(args, "remoteExtension"))
                .extensionParam(parseExtensionParam(optString(args, "extensionParam")))
                .build();
        workflowNodeCommandService.createNode(node);
        return node.getNodeId();
    }

    private Integer updateNode(JsonNode args) {
        WorkflowNode node = WorkflowNode.builder()
                .nodeId(requireInt(args, "nodeId"))
                .nodeName(optString(args, "nodeName"))
                .flowId(optInt(args, "flowId"))
                .nodeType(parseNodeType(optString(args, "nodeType")))
                .metaInfo(optString(args, "metaInfo"))
                .paramFilterExpr(optString(args, "paramFilterExpr"))
                .splitField(optString(args, "splitField"))
                .keywordField(optString(args, "keywordField"))
                .scheduleExpr(optString(args, "scheduleExpr"))
                .scheduleParamSourceType(parseScheduleParamSourceType(optString(args, "scheduleParamSourceType")))
                .remoteExtension(optString(args, "remoteExtension"))
                .extensionParam(parseExtensionParam(optString(args, "extensionParam")))
                .build();
        return workflowNodeCommandService.updateNodeSelective(node);
    }

    private NodeType parseNodeType(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return NodeType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("nodeType 无效: " + value + "，可选: HTTP / WEBSERVICE / DATABASE / NONE");
        }
    }

    private ScheduleParamSourceType parseScheduleParamSourceType(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return ScheduleParamSourceType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("scheduleParamSourceType 无效: " + value + "，可选: ORIGINAL / PRE_RESPONSE");
        }
    }

    private Map<String, String> parseExtensionParam(String extensionParam) {
        if (extensionParam == null || extensionParam.trim().isEmpty()) {
            return null;
        }
        return JSON.parseObject(extensionParam, new TypeReference<Map<String, String>>() {});
    }

    private WorkflowNodeMcpView toNodeView(WorkflowNode node) {
        WorkflowNodeMcpView view = new WorkflowNodeMcpView();
        view.setNodeId(node.getNodeId());
        view.setNodeName(node.getNodeName());
        view.setFlowId(node.getFlowId());
        view.setParamFilterExpr(node.getParamFilterExpr());
        view.setNodeType(node.getNodeType());
        view.setMetaInfo(node.getMetaInfo());
        view.setSplitField(node.getSplitField());
        view.setScheduleExpr(node.getScheduleExpr());
        view.setScheduleParamSourceType(node.getScheduleParamSourceType());
        view.setKeywordField(node.getKeywordField());
        view.setRemoteExtension(node.getRemoteExtension());
        view.setExtensionParam(node.getExtensionParam() == null ? null : JsonUtils.toJsonString(node.getExtensionParam()));
        return view;
    }
}
