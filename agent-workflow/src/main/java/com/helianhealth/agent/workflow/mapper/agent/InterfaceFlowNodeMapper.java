package com.helianhealth.agent.workflow.mapper.agent;

import com.helianhealth.agent.workflow.model.po.InterfaceWorkflowNodePO;
import org.apache.ibatis.annotations.Param;
import tk.mybatis.mapper.common.Mapper;

import java.util.List;

public interface InterfaceFlowNodeMapper extends Mapper<InterfaceWorkflowNodePO> {

    InterfaceWorkflowNodePO selectByNodeId(@Param("nodeId") Integer nodeId);
    List<InterfaceWorkflowNodePO> selectAllNodes(@Param("flowId") Integer flowId);

    /**
     * 创建工作流节点
     * @param node 工作流节点对象
     * @return 插入记录数
     */
    int createNode(InterfaceWorkflowNodePO node);

    /**
     * 更新工作流节点
     * @param node 工作流节点对象
     */
    int updateNode(InterfaceWorkflowNodePO node);
}
