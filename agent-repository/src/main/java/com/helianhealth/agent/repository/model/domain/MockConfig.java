package com.helianhealth.agent.repository.model.domain;

import lombok.Builder;
import lombok.Data;

/**
 * Mock配置领域模型
 */
@Data
@Builder
public class MockConfig {

    private Integer mockId;

    private Integer nodeId;

    private String caseName;

    private String caseDesc;

    private String mockResponse;
}
