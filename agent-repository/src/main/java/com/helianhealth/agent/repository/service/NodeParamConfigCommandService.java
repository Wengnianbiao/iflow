package com.helianhealth.agent.repository.service;

import com.helianhealth.agent.repository.model.po.NodeParamConfigDO;

import java.util.List;

/**
 * 节点参数配置写操作服务
 */
public interface NodeParamConfigCommandService {

    int insertConfig(NodeParamConfigDO config);

    int updateConfig(NodeParamConfigDO config);

    NodeParamConfigDO selectConfigById(Integer configId);

    void deleteConfig(Integer configId);

    List<NodeParamConfigDO> selectAllConfigs(Integer nodeId, String processType, String targetParam);
}
