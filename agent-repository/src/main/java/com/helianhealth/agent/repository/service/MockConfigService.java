package com.helianhealth.agent.repository.service;

import java.util.Map;

/**
 * Mock配置业务服务接口
 * 提供Mock数据匹配等业务逻辑
 */
public interface MockConfigService {

    /**
     * 根据节点ID和业务数据匹配Mock配置
     */
    Map<String, Object> matchMockData(Integer nodeId, Map<String, Object> businessData);

    /**
     * 根据mockId直接获取Mock响应数据
     */
    Map<String, Object> getMockResponseByMockId(Integer mockId);
}
