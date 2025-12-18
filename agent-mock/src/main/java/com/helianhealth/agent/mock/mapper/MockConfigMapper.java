package com.helianhealth.agent.mock.mapper;

import com.helianhealth.agent.mock.model.MockConfigDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Mock配置Mapper接口
 */
@Mapper
public interface MockConfigMapper {

    /**
     * 根据节点ID查询启用的Mock配置列表
     */
    List<MockConfigDO> selectEnabledByNodeId(@Param("nodeId") Integer nodeId);

    /**
     * 根据节点ID查询所有Mock配置列表
     */
    List<MockConfigDO> selectByNodeId(@Param("nodeId") Integer nodeId);

    /**
     * 根据mockId查询Mock配置
     */
    MockConfigDO selectById(@Param("mockId") Integer mockId);

    /**
     * 分页查询Mock配置
     */
    List<MockConfigDO> selectByPage(@Param("nodeId") Integer nodeId);

    /**
     * 查询总数
     */
    Integer countByCondition(@Param("nodeId") Integer nodeId);

    /**
     * 插入Mock配置
     */
    int insert(MockConfigDO mockConfig);

    /**
     * 更新Mock配置
     */
    int update(MockConfigDO mockConfig);

    /**
     * 删除Mock配置
     */
    int deleteById(@Param("mockId") Integer mockId);

    /**
     * 根据节点ID删除所有Mock配置
     */
    int deleteByNodeId(@Param("nodeId") Integer nodeId);
}
