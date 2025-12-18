package com.helianhealth.agent.mock.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Mock配置实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MockConfigDO {

    /**
     * 主键ID
     */
    private Integer mockId;

    /**
     * 节点ID
     */
    private Integer nodeId;

    /**
     * Mock场景名称
     */
    private String caseName;

    /**
     * Mock场景描述
     */
    private String caseDesc;


    /**
     * Mock响应数据（JSON格式）
     */
    private String mockResponse;

    /**
     * 是否启用（1-启用，0-禁用）
     */
    private Integer enabled;
}
