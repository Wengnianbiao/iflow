package com.helianhealth.agent.repository.service;

import com.helianhealth.agent.repository.model.domain.MockConfig;

/**
 * Mock配置写操作服务
 */
public interface MockConfigCommandService {

    int insert(MockConfig mockConfig);

    int update(MockConfig mockConfig);

    int deleteById(Integer mockId);

    int deleteByNodeId(Integer nodeId);
}
