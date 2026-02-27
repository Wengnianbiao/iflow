package com.helianhealth.agent.repository.service;

import com.helianhealth.agent.repository.model.domain.NodeParamConfig;

import java.util.List;

/**
 * 节点参数配置查询服务（供运行时模块使用）
 */
public interface NodeParamConfigQueryService {

    List<NodeParamConfig> selectPreProcessConfigByNodeId(Integer nodeId);

    List<NodeParamConfig> selectPostProcessConfigByNodeId(Integer nodeId);
}
