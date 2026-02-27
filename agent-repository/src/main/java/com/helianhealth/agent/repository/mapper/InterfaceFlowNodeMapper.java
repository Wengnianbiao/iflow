package com.helianhealth.agent.repository.mapper;

import com.helianhealth.agent.repository.model.po.InterfaceWorkflowNodePO;
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
     * 更新工作流节点基本信息（动态SQL，只更新非空字段）
     * @param node 工作流节点对象
     * @return 更新记录数
     */
    int updateNodeSelective(InterfaceWorkflowNodePO node);

    /**
     * 更新节点的 remote_extension 字段
     * @param nodeId 节点ID
     * @param remoteExtension 远程扩展名称（可为null表示清空）
     * @return 更新记录数
     */
    int updateRemoteExtension(@Param("nodeId") Integer nodeId, 
                              @Param("remoteExtension") String remoteExtension);

    /**
     * 更新节点的 extension_param 字段
     * @param nodeId 节点ID
     * @param extensionParam 扩展参数JSON字符串
     * @return 更新记录数
     */
    int updateExtensionParam(@Param("nodeId") Integer nodeId, 
                             @Param("extensionParam") String extensionParam);
}
