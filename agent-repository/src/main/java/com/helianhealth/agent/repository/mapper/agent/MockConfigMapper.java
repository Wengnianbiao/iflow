package com.helianhealth.agent.repository.mapper.agent;

import com.helianhealth.agent.repository.model.po.MockConfigDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Mock配置Mapper接口
 */
@Mapper
public interface MockConfigMapper {

    List<MockConfigDO> selectEnabledByNodeId(@Param("nodeId") Integer nodeId);

    List<MockConfigDO> selectByNodeId(@Param("nodeId") Integer nodeId);

    MockConfigDO selectById(@Param("mockId") Integer mockId);

    List<MockConfigDO> selectByPage(@Param("nodeId") Integer nodeId);

    Integer countByCondition(@Param("nodeId") Integer nodeId);

    int insert(MockConfigDO mockConfig);

    int update(MockConfigDO mockConfig);

    int deleteById(@Param("mockId") Integer mockId);

    int deleteByNodeId(@Param("nodeId") Integer nodeId);
}
