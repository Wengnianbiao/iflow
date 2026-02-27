package com.helianhealth.agent.repository.service.impl;

import com.helianhealth.agent.common.utils.JsonUtils;
import com.helianhealth.agent.repository.model.domain.MockConfig;
import com.helianhealth.agent.repository.service.MockConfigQueryService;
import com.helianhealth.agent.repository.service.MockConfigService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Mock配置业务服务实现
 */
@Service
@Slf4j
@AllArgsConstructor
public class MockConfigServiceImpl implements MockConfigService {

    private final MockConfigQueryService mockConfigQueryService;

    @Override
    public Map<String, Object> matchMockData(Integer nodeId, Map<String, Object> businessData) {
        if (nodeId == null) {
            return null;
        }

        List<MockConfig> mockConfigs = mockConfigQueryService.findByNodeId(nodeId);
        if (mockConfigs == null || mockConfigs.isEmpty()) {
            log.debug("节点{}没有配置Mock数据", nodeId);
            return null;
        }

        MockConfig mockConfig = mockConfigs.get(0);
        log.info("节点{}使用Mock配置：caseName={}, mockId={}",
                nodeId, mockConfig.getCaseName(), mockConfig.getMockId());

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

        MockConfig mockConfig = mockConfigQueryService.findById(mockId);
        if (mockConfig == null) {
            log.debug("mockId={}的Mock配置不存在", mockId);
            return null;
        }

        try {
            return JsonUtils.toMap(mockConfig.getMockResponse());
        } catch (Exception e) {
            log.error("解析Mock响应数据失败，mockId={}", mockId, e);
            return null;
        }
    }
}
