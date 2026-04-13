package com.helianhealth.agent.repository.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.helianhealth.agent.common.base.PageList;
import com.helianhealth.agent.repository.mapper.agent.MockConfigMapper;
import com.helianhealth.agent.repository.model.assembler.MockConfigAssembler;
import com.helianhealth.agent.repository.model.domain.MockConfig;
import com.helianhealth.agent.repository.model.po.MockConfigPO;
import com.helianhealth.agent.repository.service.MockConfigQueryService;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Mock配置服务实现
 */
@Service
public class MockConfigQueryServiceImpl implements MockConfigQueryService {

    private final MockConfigMapper mockConfigMapper;

    public MockConfigQueryServiceImpl(MockConfigMapper mockConfigMapper) {
        this.mockConfigMapper = mockConfigMapper;
    }

    @Override
    public List<MockConfig> findByNodeId(Integer nodeId) {
        List<MockConfigPO> doList = mockConfigMapper.selectByNodeId(nodeId);
        if (doList == null) {
            return Collections.emptyList();
        }
        return doList.stream().map(MockConfigAssembler::toDomain).collect(Collectors.toList());
    }

    @Override
    public MockConfig findById(Integer mockId) {
        return MockConfigAssembler.toDomain(mockConfigMapper.selectById(mockId));
    }

    @Override
    public PageList<MockConfig> findByPage(Integer nodeId, int pageNum, int pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        List<MockConfigPO> doList = mockConfigMapper.selectByPage(nodeId);
        PageInfo<MockConfigPO> pageInfo = new PageInfo<>(doList);

        List<MockConfig> rows = doList.stream()
                .map(MockConfigAssembler::toDomain)
                .collect(Collectors.toList());

        PageList<MockConfig> pageList = new PageList<>();
        pageList.setRows(rows);
        pageList.setTotal(pageInfo.getTotal());
        pageList.setPageNum(pageInfo.getPageNum());
        pageList.setPageSize(pageInfo.getPageSize());
        return pageList;
    }

    @Override
    public String getRawMockResponseByMockId(Integer mockId) {
        MockConfig mockConfig = findById(mockId);
        return mockConfig != null ? mockConfig.getMockResponse() : null;
    }

}
