package com.helianhealth.agent.repository.service;

import com.helianhealth.agent.common.base.PageList;
import com.helianhealth.agent.repository.model.domain.MockConfig;

import java.util.List;

/**
 * Mock配置服务
 */
public interface MockConfigQueryService {

    List<MockConfig> findByNodeId(Integer nodeId);

    MockConfig findById(Integer mockId);

    PageList<MockConfig> findByPage(Integer nodeId, int pageNum, int pageSize);

    /**
     * 根据mockId获取Mock原始响应字符串（JSON/XML等原始格式）
     */
    String getRawMockResponseByMockId(Integer mockId);
}
