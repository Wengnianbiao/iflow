package com.helianhealth.agent.repository.service.impl;

import com.helianhealth.agent.repository.mapper.agent.MockConfigMapper;
import com.helianhealth.agent.repository.model.assembler.MockConfigAssembler;
import com.helianhealth.agent.repository.model.domain.MockConfig;
import com.helianhealth.agent.repository.model.po.MockConfigPO;
import com.helianhealth.agent.repository.service.MockConfigCommandService;
import org.springframework.stereotype.Service;

/**
 * Mock配置写操作服务实现
 */
@Service
public class MockConfigCommandServiceImpl implements MockConfigCommandService {

    private final MockConfigMapper mockConfigMapper;

    public MockConfigCommandServiceImpl(MockConfigMapper mockConfigMapper) {
        this.mockConfigMapper = mockConfigMapper;
    }

    @Override
    public int insert(MockConfig mockConfig) {
        MockConfigPO po = MockConfigAssembler.toPO(mockConfig);
        int result = mockConfigMapper.insert(po);
        mockConfig.setMockId(po.getMockId());
        return result;
    }

    @Override
    public int update(MockConfig mockConfig) {
        MockConfigPO po = MockConfigAssembler.toPO(mockConfig);
        return mockConfigMapper.update(po);
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
