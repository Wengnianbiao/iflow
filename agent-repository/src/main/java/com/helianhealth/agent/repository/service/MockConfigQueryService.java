package com.helianhealth.agent.repository.service;

import com.helianhealth.agent.common.base.PageList;
import com.helianhealth.agent.repository.model.domain.MockConfig;

import java.util.List;

/**
 * Mock配置查询服务
 */
public interface MockConfigQueryService {

    List<MockConfig> findByNodeId(Integer nodeId);

    MockConfig findById(Integer mockId);

    PageList<MockConfig> findByPage(Integer nodeId, int pageNum, int pageSize);
}
