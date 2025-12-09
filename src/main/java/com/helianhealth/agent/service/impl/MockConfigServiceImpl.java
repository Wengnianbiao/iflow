package com.helianhealth.agent.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.helianhealth.agent.common.PageList;
import com.helianhealth.agent.mapper.agent.MockConfigMapper;
import com.helianhealth.agent.model.domain.MockConfigDO;
import com.helianhealth.agent.service.MockConfigService;
import com.helianhealth.agent.utils.JsonUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Mock配置服务实现类
 */
@Service
@Slf4j
@AllArgsConstructor
public class MockConfigServiceImpl implements MockConfigService {

    private final MockConfigMapper mockConfigMapper;

    @Override
    public Map<String, Object> matchMockData(Integer nodeId, Map<String, Object> businessData) {
        if (nodeId == null) {
            return null;
        }

        // 查询该节点启用的所有Mock配置
        List<MockConfigDO> mockConfigs = mockConfigMapper.selectEnabledByNodeId(nodeId);
        if (mockConfigs == null || mockConfigs.isEmpty()) {
            log.debug("节点{}没有配置Mock数据", nodeId);
            return null;
        }

        // 直接使用第一个启用的Mock配置
        MockConfigDO mockConfig = mockConfigs.get(0);
        log.info("节点{}使用Mock配置：caseName={}, mockId={}",
                nodeId, mockConfig.getCaseName(), mockConfig.getMockId());

        // 解析并返回Mock响应数据
        try {
            return JsonUtils.toMap(mockConfig.getMockResponse());
        } catch (Exception e) {
            log.error("解析Mock响应数据失败，mockId={}", mockConfig.getMockId(), e);
            return null;
        }
    }

    @Override
    public Map<String, Object> getMockResponseByMockId(Integer mockId) {
        if (mockId == null) {
            return null;
        }

        MockConfigDO mockConfig = mockConfigMapper.selectById(mockId);
        if (mockConfig == null || mockConfig.getEnabled() == 0) {
            log.debug("mockId={}的Mock配置不存在或未启用", mockId);
            return null;
        }

        // 解析并返回Mock响应数据
        try {
            return JsonUtils.toMap(mockConfig.getMockResponse());
        } catch (Exception e) {
            log.error("解析Mock响应数据失败，mockId={}", mockId, e);
            return null;
        }
    }

    @Override
    public PageList<MockConfigDO> findByPage(Integer nodeId, int pageNum, int pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        List<MockConfigDO> list = mockConfigMapper.selectByPage(nodeId);
        PageInfo<MockConfigDO> pageInfo = new PageInfo<>(list);

        return PageList.<MockConfigDO>builder()
                .rows(list)
                .total(pageInfo.getTotal())
                .pageNum(pageInfo.getPageNum())
                .pageSize(pageInfo.getPageSize())
                .build();
    }

    @Override
    public List<MockConfigDO> findByNodeId(Integer nodeId) {
        return mockConfigMapper.selectByNodeId(nodeId);
    }

    @Override
    public MockConfigDO findById(Integer mockId) {
        return mockConfigMapper.selectById(mockId);
    }

    @Override
    public MockConfigDO save(MockConfigDO mockConfig) {
        // 设置默认值
        if (mockConfig.getEnabled() == null) {
            mockConfig.setEnabled(1);
        }

        mockConfigMapper.insert(mockConfig);
        return mockConfig;
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
