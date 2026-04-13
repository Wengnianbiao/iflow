package com.helianhealth.agent.repository.mapper.agent;

import com.helianhealth.agent.repository.model.po.MockConfigPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Mock配置Mapper接口
 */
@Mapper
public interface MockConfigMapper {

    List<MockConfigPO> selectEnabledByNodeId(@Param("nodeId") Integer nodeId);

    List<MockConfigPO> selectByNodeId(@Param("nodeId") Integer nodeId);

    MockConfigPO selectById(@Param("mockId") Integer mockId);

    List<MockConfigPO> selectByPage(@Param("nodeId") Integer nodeId);

    Integer countByCondition(@Param("nodeId") Integer nodeId);

    int insert(MockConfigPO mockConfig);

    int update(MockConfigPO mockConfig);

    int deleteById(@Param("mockId") Integer mockId);

    int deleteByNodeId(@Param("nodeId") Integer nodeId);
}
