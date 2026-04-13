package com.helianhealth.agent.repository.mapper.agent;

import com.helianhealth.agent.repository.model.po.NodeParamConfigPO;
import org.apache.ibatis.annotations.Param;
import tk.mybatis.mapper.common.Mapper;

import java.util.List;

public interface NodeParamConfigMapper extends Mapper<NodeParamConfigPO>{

    List<NodeParamConfigPO> selectPreProcessConfigByNodeId(Integer nodeId);

    List<NodeParamConfigPO> selectPostProcessConfigByNodeId(Integer nodeId);

    List<NodeParamConfigPO> selectAllParamConfig(@Param("nodeId") Integer nodeId,
                                                 @Param("processType") String processType,
                                                 @Param("targetParam") String targetParam);

    /**
     * 插入节点参数配置（不包含自增主键）
     * @param config 节点参数配置对象
     * @return 插入记录数
     */
    int insertNodeParamConfig(NodeParamConfigPO config);

    /**
     * 更新节点参数配置
     * @param config 节点参数配置对象
     * @return 更新记录数
     */
    int updateNodeParamConfig(NodeParamConfigPO config);
}
