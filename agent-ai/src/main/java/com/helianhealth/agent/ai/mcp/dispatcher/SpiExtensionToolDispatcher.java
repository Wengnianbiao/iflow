package com.helianhealth.agent.ai.mcp.dispatcher;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.agent.ai.convert.ExtensionMcpConverter;
import com.helianhealth.agent.ai.model.ExtensionMcpView;
import com.helianhealth.agent.ai.model.ExtensionRegisterMcpRequest;
import com.helianhealth.agent.ai.model.ExtensionUpdateMcpRequest;
import com.helianhealth.agent.common.enums.ExtensionType;
import com.helianhealth.agent.repository.model.domain.NodeExtensionConfig;
import com.helianhealth.agent.repository.model.dto.NodeExtensionConfigCreateReq;
import com.helianhealth.agent.repository.model.dto.NodeExtensionConfigUpdateReq;
import com.helianhealth.agent.repository.service.extension.DynamicSpiAdminService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Tool: {@code agent_spi_extension} —— SPI 扩展域级 Tool 分发器。
 */
@Slf4j
@Component
public class SpiExtensionToolDispatcher extends McpDispatcherSupport implements McpToolDispatcher {

    public static final String TOOL_NAME = "agent_spi_extension";

    private final DynamicSpiAdminService dynamicSpiAdminService;

    public SpiExtensionToolDispatcher(ObjectMapper objectMapper,
                                      DynamicSpiAdminService dynamicSpiAdminService) {
        super(objectMapper);
        this.dynamicSpiAdminService = dynamicSpiAdminService;
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
                return list(arguments);
            case "register":
                register(arguments);
                return "注册成功";
            case "update":
                update(arguments);
                return "更新成功";
            case "delete":
                dynamicSpiAdminService.deleteExtension(requireInt(arguments, "configId"));
                return "删除成功";
            case "enable":
                dynamicSpiAdminService.enableAndRegister(requireInt(arguments, "configId"));
                return "启用成功";
            case "disable":
                dynamicSpiAdminService.disableAndUnregister(requireInt(arguments, "configId"));
                return "禁用成功";
            default:
                throw new IllegalArgumentException("不支持的 action: " + action);
        }
    }

    private List<ExtensionMcpView> list(JsonNode args) {
        ExtensionType type = parseExtensionType(requireString(args, "type"));
        List<NodeExtensionConfig> configs = dynamicSpiAdminService.listExtensionsBySpiInterface(type.getSpiInterface());
        return ExtensionMcpConverter.toMcpViewList(configs);
    }

    private void register(JsonNode args) {
        ExtensionType type = parseExtensionType(requireString(args, "type"));
        ExtensionRegisterMcpRequest request = convertWithoutType(args, ExtensionRegisterMcpRequest.class);
        NodeExtensionConfigCreateReq createReq = ExtensionMcpConverter.toCreateReq(request);
        createReq.setSpiInterface(type.getSpiInterface());
        dynamicSpiAdminService.registerExtension(type, createReq);
    }

    private void update(JsonNode args) {
        ExtensionType type = parseExtensionType(requireString(args, "type"));
        ExtensionUpdateMcpRequest request = convertWithoutType(args, ExtensionUpdateMcpRequest.class);
        NodeExtensionConfigUpdateReq updateReq = ExtensionMcpConverter.toUpdateReq(request);
        updateReq.setSpiInterface(type.getSpiInterface());
        dynamicSpiAdminService.updateExtension(type, updateReq);
    }

    /**
     * 把 arguments 反序列化为目标 DTO，忽略 action / type 这两个仅 MCP 路由用的字段
     */
    private <T> T convertWithoutType(JsonNode args, Class<T> type) {
        if (args == null || !args.isObject()) {
            return null;
        }
        com.fasterxml.jackson.databind.node.ObjectNode copy = args.deepCopy();
        copy.remove("action");
        copy.remove("type");
        return objectMapper.convertValue(copy, type);
    }

    private ExtensionType parseExtensionType(String code) {
        try {
            return ExtensionType.fromCode(code);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("type 无效: " + code + "，可选: filter / remote / workflow / jarvis-workflow");
        }
    }
}
