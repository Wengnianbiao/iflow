package com.helianhealth.agent.repository.service.impl;

import com.helianhealth.agent.repository.mapper.MockConfigMapper;
import com.helianhealth.agent.repository.model.po.MockConfigDO;
import com.helianhealth.agent.repository.service.MockConfigCommandService;
import org.springframework.stereotype.Component;

/**
 * Mock配置写操作服务实现
 */
@Component
public class MockConfigCommandServiceImpl implements MockConfigCommandService {

    private final MockConfigMapper mockConfigMapper;

    public MockConfigCommandServiceImpl(MockConfigMapper mockConfigMapper) {
        this.mockConfigMapper = mockConfigMapper;
    }

    @Override
    public int insert(MockConfigDO mockConfig) {
        return mockConfigMapper.insert(mockConfig);
    }

    @Override
    public int update(MockConfigDO mockConfig) {
        return mockConfigMapper.update(mockConfig);
    }

    @Override
    public int deleteById(Integer mockId) {
        return mockConfigMapper.deleteById(mockId);
    }

    @Override
    public int deleteByNodeId(Integer nodeId) {
        return mockConfigMapper.deleteByNodeId(nodeId);
    }
}
