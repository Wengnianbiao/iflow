package com.helianhealth.agent.repository.service;

import com.helianhealth.agent.repository.model.domain.NodeParamConfig;

/**
 * 节点参数配置写操作服务
 */
public interface NodeParamConfigCommandService {

    int insertConfig(NodeParamConfig config);

    int updateConfig(NodeParamConfig config);

    void deleteConfig(Integer configId);
}
