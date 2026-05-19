package com.helianhealth.agent.ai.convert;

import com.helianhealth.agent.ai.model.MockConfigMcpView;
import com.helianhealth.agent.repository.model.domain.MockConfig;

import java.util.List;
import java.util.stream.Collectors;

/**
 * MCP 视图转换器 - Mock 配置
 */
public class MockConfigMcpConverter {

    private MockConfigMcpConverter() {
    }

    public static MockConfigMcpView toMcpView(MockConfig config) {
        if (config == null) {
            return null;
        }
        return MockConfigMcpView.builder()
                .mockId(config.getMockId())
                .nodeId(config.getNodeId())
                .caseName(config.getCaseName())
                .caseDesc(config.getCaseDesc())
                .mockResponse(config.getMockResponse())
                .build();
    }

    public static List<MockConfigMcpView> toMcpViewList(List<MockConfig> configs) {
        if (configs == null) {
            return null;
        }
        return configs.stream()
                .map(MockConfigMcpConverter::toMcpView)
                .collect(Collectors.toList());
    }

    public static MockConfig toDomain(MockConfigMcpView view) {
        if (view == null) {
            return null;
        }
        return MockConfig.builder()
                .mockId(view.getMockId())
                .nodeId(view.getNodeId())
                .caseName(view.getCaseName())
                .caseDesc(view.getCaseDesc())
                .mockResponse(view.getMockResponse())
                .build();
    }
}
