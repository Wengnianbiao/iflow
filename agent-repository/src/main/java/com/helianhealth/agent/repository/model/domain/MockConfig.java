package com.helianhealth.agent.repository.model.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mock配置领域模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MockConfig {

    private Integer mockId;

    private Integer nodeId;

    private String caseName;

    private String caseDesc;

    private String mockResponse;
}
