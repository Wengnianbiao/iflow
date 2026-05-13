package com.helianhealth.agent.repository.mapper.agent;

import com.helianhealth.agent.repository.model.po.NodeParamConfigPO;
import org.apache.ibatis.annotations.Param;
import java.util.List;

public interface NodeParamConfigMapper {

    int insertNodeParamConfig(NodeParamConfigPO config);

    List<NodeParamConfigPO> selectPreProcessConfigByNodeId(Integer nodeId);

    List<NodeParamConfigPO> selectPostProcessConfigByNodeId(Integer nodeId);

    List<NodeParamConfigPO> selectAllParamConfig(@Param("nodeId") Integer nodeId, @Param("processType") String processType, @Param("targetParam") String targetParam);

    NodeParamConfigPO selectByPrimaryKey(Integer configId);

    int updateByPrimaryKeySelective(NodeParamConfigPO record);

    int deleteByPrimaryKey(Integer configId);
}
