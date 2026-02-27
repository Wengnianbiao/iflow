package com.helianhealth.agent.repository.service;

import com.helianhealth.agent.repository.model.po.MockConfigDO;

/**
 * Mock配置写操作服务
 */
public interface MockConfigCommandService {

    int insert(MockConfigDO mockConfig);

    int update(MockConfigDO mockConfig);

    int deleteById(Integer mockId);

    int deleteByNodeId(Integer nodeId);
}
