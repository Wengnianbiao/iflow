package com.helianhealth.agent.ai.mcp.dispatcher;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.agent.ai.convert.NodeParamConfigMcpConverter;
import com.helianhealth.agent.ai.model.NodeParamConfigMcpResponse;
import com.helianhealth.agent.ai.model.NodeParamConfigMcpView;
import com.helianhealth.agent.ai.service.AiNodeParamConfigService;
import com.helianhealth.agent.common.enums.ProcessType;
import com.helianhealth.agent.repository.model.domain.NodeParamConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Tool: {@code agent_node_param_config} —— 节点参数映射域级 Tool 分发器。
 */
@Slf4j
@Component
public class NodeParamConfigToolDispatcher extends McpDispatcherSupport implements McpToolDispatcher {

    public static final String TOOL_NAME = "agent_node_param_config";

    private final AiNodeParamConfigService nodeParamConfigService;

    public NodeParamConfigToolDispatcher(ObjectMapper objectMapper,
                                         AiNodeParamConfigService nodeParamConfigService) {
        super(objectMapper);
        this.nodeParamConfigService = nodeParamConfigService;
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
                return listConfigs(arguments);
            case "export":
                return exportConfigs(arguments);
            case "import":
                return importConfigs(arguments);
            case "update":
                return updateConfig(arguments);
            case "delete":
                nodeParamConfigService.deleteNodeParamConfig(requireInt(arguments, "configId"));
                return "节点参数配置删除成功";
            case "delete_all":
                nodeParamConfigService.deleteAllNodeParamConfigs(requireInt(arguments, "nodeId"));
                return "全量删除节点参数配置成功";
            default:
                throw new IllegalArgumentException("不支持的 action: " + action);
        }
    }

    private List<NodeParamConfigMcpView> listConfigs(JsonNode args) {
        Integer nodeId = requireInt(args, "nodeId");
        ProcessType processType = parseProcessType(optString(args, "processType"));
        List<NodeParamConfig> configs = nodeParamConfigService.findAllNodeParamConfigsUnpaged(nodeId, processType);
        return NodeParamConfigMcpConverter.toMcpViewList(configs);
    }

    private NodeParamConfigMcpResponse exportConfigs(JsonNode args) {
        return nodeParamConfigService.exportNodeParamConfigs(requireInt(args, "nodeId"));
    }

    private String importConfigs(JsonNode args) {
        Integer nodeId = requireInt(args, "nodeId");
        if (!nodeParamConfigService.nodeExists(nodeId)) {
            throw new IllegalArgumentException("节点不存在，nodeId=" + nodeId);
        }
        List<NodeParamConfigMcpView> params = convertSubNodeList(args, "params", NodeParamConfigMcpView.class);
        if (params == null || params.isEmpty()) {
            throw new IllegalArgumentException("参数配置列表不能为空");
        }
        NodeParamConfigMcpConverter.validateViews(params);

        Boolean clearBeforeImport = optBool(args, "clearBeforeImport");
        if (clearBeforeImport == null || clearBeforeImport) {
            nodeParamConfigService.deleteAllNodeParamConfigs(nodeId);
        }
        nodeParamConfigService.importNodeParamConfigs(nodeId, params, false);
        return "导入节点参数配置成功，共" + params.size() + "条";
    }

    private NodeParamConfigMcpView updateConfig(JsonNode args) {
        NodeParamConfigMcpView view = convertSubNode(args, "config", NodeParamConfigMcpView.class);
        if (view == null || view.getConfigId() == null) {
            throw new IllegalArgumentException("参数不能为空，configId 必须存在");
        }
        NodeParamConfig updated = nodeParamConfigService.updateNodeParamConfig(view);
        if (updated == null) {
            throw new IllegalArgumentException("更新节点参数配置失败");
        }
        return NodeParamConfigMcpConverter.toMcpView(updated);
    }

    private ProcessType parseProcessType(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return ProcessType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("processType 无效: " + value + "，可选: PRE_PROCESS / POST_PROCESS");
        }
    }
}
