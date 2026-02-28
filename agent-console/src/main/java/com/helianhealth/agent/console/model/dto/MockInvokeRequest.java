package com.helianhealth.agent.console.model.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Mock调用请求参数
 */
@Data
public class MockInvokeRequest {

    /**
     * Mock配置列表，按调度顺序排列
     * 第一个元素为起始节点
     */
    private List<MockNodeConfig> mockConfigs;

    /**
     * 业务数据
     */
    private Map<String, Object> businessData;

    /**
     * 单个节点的Mock配置
     */
    @Data
    public static class MockNodeConfig {
        /**
         * 节点ID
         */
        private Integer nodeId;

        /**
         * Mock配置ID
         */
        private Integer mockId;
    }
}
