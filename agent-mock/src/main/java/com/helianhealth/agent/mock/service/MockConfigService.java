package com.helianhealth.agent.mock.service;

import com.helianhealth.agent.common.base.PageList;
import com.helianhealth.agent.mock.model.MockConfigDO;

import java.util.List;
import java.util.Map;

/**
 * Mock配置服务接口
 */
public interface MockConfigService {

    /**
     * 根据节点ID和业务数据匹配Mock配置
     *
     * @param nodeId 节点ID
     * @param businessData 业务数据
     * @return 匹配的Mock响应数据，如果没有匹配则返回null
     */
    Map<String, Object> matchMockData(Integer nodeId, Map<String, Object> businessData);

    /**
     * 根据mockId直接获取Mock响应数据
     *
     * @param mockId MockID
     * @return Mock响应数据，如果不存在则返回null
     */
    Map<String, Object> getMockResponseByMockId(Integer mockId);

    /**
     * 分页查询Mock配置
     */
    PageList<MockConfigDO> findByPage(Integer nodeId, int pageNum, int pageSize);

    /**
     * 根据节点ID查询所有Mock配置
     */
    List<MockConfigDO> findByNodeId(Integer nodeId);

    /**
     * 根据mockId查询Mock配置
     */
    MockConfigDO findById(Integer mockId);

    /**
     * 创建Mock配置
     */
    MockConfigDO save(MockConfigDO mockConfig);

    /**
     * 更新Mock配置
     */
    int update(MockConfigDO mockConfig);

    /**
     * 删除Mock配置
     */
    int deleteById(Integer mockId);

    /**
     * 根据节点ID删除所有Mock配置
     */
    int deleteByNodeId(Integer nodeId);
}
