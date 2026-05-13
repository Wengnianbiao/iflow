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

    List<MockConfigPO> selectEnabledByNodeId(Integer nodeId);

    List<MockConfigPO> selectByNodeId(Integer nodeId);

    MockConfigPO selectById(Integer mockId);

    List<MockConfigPO> selectByPage(@Param("nodeId") Integer nodeId);

    int countByCondition(@Param("nodeId") Integer nodeId);

    int insert(MockConfigPO mockConfig);

    int update(MockConfigPO mockConfig);

    int deleteById(Integer mockId);

    int deleteByNodeId(Integer nodeId);
}
