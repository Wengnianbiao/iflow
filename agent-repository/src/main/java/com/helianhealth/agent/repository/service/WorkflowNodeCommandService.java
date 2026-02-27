package com.helianhealth.agent.repository.service;

import com.helianhealth.agent.repository.model.po.InterfaceWorkflowNodePO;

import java.util.List;

/**
 * 工作流节点写操作服务
 */
public interface WorkflowNodeCommandService {

    void createNode(InterfaceWorkflowNodePO node);

    /**
     * 更新节点基本信息（动态SQL，只更新非空字段）
     */
    int updateNodeSelective(InterfaceWorkflowNodePO node);

    /**
     * 更新节点的 remote_extension 字段
     * @param nodeId 节点ID
     * @param remoteExtension 远程扩展名称（可为null表示清空）
     */
    int updateRemoteExtension(Integer nodeId, String remoteExtension);

    /**
     * 更新节点的 extension_param 字段
     * @param nodeId 节点ID
     * @param extensionParam 扩展参数JSON字符串
     */
    int updateExtensionParam(Integer nodeId, String extensionParam);

    void deleteNode(Integer nodeId);

    List<InterfaceWorkflowNodePO> selectAllNodes(Integer flowId);

    InterfaceWorkflowNodePO selectNodeEntity(Integer nodeId);
}
