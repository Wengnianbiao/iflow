package com.helianhealth.agent.ai.mcp.dispatcher;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.agent.ai.convert.MockConfigMcpConverter;
import com.helianhealth.agent.ai.model.MockConfigMcpView;
import com.helianhealth.agent.repository.model.domain.MockConfig;
import com.helianhealth.agent.repository.service.MockConfigCommandService;
import com.helianhealth.agent.repository.service.MockConfigQueryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Tool: {@code agent_mock_config} —— Mock 配置域级 Tool 分发器。
 */
@Slf4j
@Component
public class MockConfigToolDispatcher extends McpDispatcherSupport implements McpToolDispatcher {

    public static final String TOOL_NAME = "agent_mock_config";

    private final MockConfigQueryService mockConfigQueryService;
    private final MockConfigCommandService mockConfigCommandService;

    public MockConfigToolDispatcher(ObjectMapper objectMapper,
                                    MockConfigQueryService mockConfigQueryService,
                                    MockConfigCommandService mockConfigCommandService) {
        super(objectMapper);
        this.mockConfigQueryService = mockConfigQueryService;
        this.mockConfigCommandService = mockConfigCommandService;
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
                return listByNode(requireInt(arguments, "nodeId"));
            case "get":
                return getMock(requireInt(arguments, "mockId"));
            case "create":
                return createMock(arguments);
            case "update":
                return updateMock(arguments);
            case "delete":
                return deleteMock(requireInt(arguments, "mockId"));
            case "delete_by_node":
                return deleteByNode(requireInt(arguments, "nodeId"));
            default:
                throw new IllegalArgumentException("不支持的 action: " + action);
        }
    }

    private List<MockConfigMcpView> listByNode(Integer nodeId) {
        return MockConfigMcpConverter.toMcpViewList(mockConfigQueryService.findByNodeId(nodeId));
    }

    private MockConfigMcpView getMock(Integer mockId) {
        MockConfig config = mockConfigQueryService.findById(mockId);
        if (config == null) {
            throw new IllegalArgumentException("Mock 配置不存在: mockId=" + mockId);
        }
        return MockConfigMcpConverter.toMcpView(config);
    }

    private Integer createMock(JsonNode args) {
        MockConfigMcpView view = MockConfigMcpView.builder()
                .nodeId(requireInt(args, "nodeId"))
                .caseName(requireString(args, "caseName"))
                .caseDesc(optString(args, "caseDesc"))
                .mockResponse(requireString(args, "mockResponse"))
                .build();
        MockConfig config = MockConfigMcpConverter.toDomain(view);
        config.setMockId(null);
        mockConfigCommandService.insert(config);
        return config.getMockId();
    }

    private Integer updateMock(JsonNode args) {
        MockConfigMcpView view = MockConfigMcpView.builder()
                .mockId(requireInt(args, "mockId"))
                .caseName(optString(args, "caseName"))
                .caseDesc(optString(args, "caseDesc"))
                .mockResponse(optString(args, "mockResponse"))
                .build();
        return mockConfigCommandService.update(MockConfigMcpConverter.toDomain(view));
    }

    private String deleteMock(Integer mockId) {
        int result = mockConfigCommandService.deleteById(mockId);
        if (result <= 0) {
            throw new IllegalArgumentException("Mock 配置不存在: mockId=" + mockId);
        }
        return "Mock 配置删除成功";
    }

    private String deleteByNode(Integer nodeId) {
        int result = mockConfigCommandService.deleteByNodeId(nodeId);
        return "已删除 " + result + " 个 Mock 配置";
    }
}
