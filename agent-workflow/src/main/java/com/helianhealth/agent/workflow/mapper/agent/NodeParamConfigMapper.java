package com.helianhealth.agent.workflow.mapper.agent;

import com.helianhealth.agent.workflow.enums.ProcessType;
import com.helianhealth.agent.workflow.model.domain.NodeParamConfigDO;
import org.apache.ibatis.annotations.Param;
import tk.mybatis.mapper.common.Mapper;

import java.util.List;

public interface NodeParamConfigMapper extends Mapper<NodeParamConfigDO>{

    List<NodeParamConfigDO> selectPreProcessConfigByNodeId(Integer nodeId);

    List<NodeParamConfigDO> selectPostProcessConfigByNodeId(Integer nodeId);

    List<NodeParamConfigDO> selectAllParamConfig(@Param("nodeId") Integer nodeId,
                                                 @Param("processType") String processType,
                                                 @Param("targetParam") String targetParam);

    /**
     * 插入节点参数配置（不包含自增主键）
     * @param config 节点参数配置对象
     * @return 插入记录数
     */
    int insertNodeParamConfig(NodeParamConfigDO config);

    /**
     * 更新节点参数配置
     * @param config 节点参数配置对象
     * @return 更新记录数
     */
    int updateNodeParamConfig(NodeParamConfigDO config);
}
