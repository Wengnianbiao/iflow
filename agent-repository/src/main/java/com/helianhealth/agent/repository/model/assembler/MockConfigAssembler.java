package com.helianhealth.agent.repository.model.assembler;

import com.helianhealth.agent.repository.model.domain.MockConfig;
import com.helianhealth.agent.repository.model.po.MockConfigPO;

/**
 * Mock配置 PO/Domain 双向转换器
 */
public final class MockConfigAssembler {

    private MockConfigAssembler() {
    }

    public static MockConfig toDomain(MockConfigPO po) {
        if (po == null) {
            return null;
        }
        return MockConfig.builder()
                .mockId(po.getMockId())
                .nodeId(po.getNodeId())
                .caseName(po.getCaseName())
                .caseDesc(po.getCaseDesc())
                .mockResponse(po.getMockResponse())
                .build();
    }

    public static MockConfigPO toPO(MockConfig config) {
        if (config == null) {
            return null;
        }
        return MockConfigPO.builder()
                .mockId(config.getMockId())
                .nodeId(config.getNodeId())
                .caseName(config.getCaseName())
                .caseDesc(config.getCaseDesc())
                .mockResponse(config.getMockResponse())
                .build();
    }
}
