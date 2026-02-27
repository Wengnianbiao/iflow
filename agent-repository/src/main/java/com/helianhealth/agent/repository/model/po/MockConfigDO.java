package com.helianhealth.agent.repository.model.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mock配置持久化对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MockConfigDO {

    private Integer mockId;

    private Integer nodeId;

    private String caseName;

    private String caseDesc;

    private String mockResponse;
}
