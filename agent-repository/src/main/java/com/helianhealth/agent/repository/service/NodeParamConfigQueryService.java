package com.helianhealth.agent.repository.service;

import com.helianhealth.agent.common.base.PageList;
import com.helianhealth.agent.repository.model.domain.NodeParamConfig;

import java.util.List;

/**
 * 节点参数配置查询服务
 */
public interface NodeParamConfigQueryService {

    List<NodeParamConfig> selectPreProcessConfigByNodeId(Integer nodeId);

    List<NodeParamConfig> selectPostProcessConfigByNodeId(Integer nodeId);

    /**
     * 根据配置ID查询
     */
    NodeParamConfig selectConfigById(Integer configId);

    /**
     * 查询所有参数配置
     */
    List<NodeParamConfig> selectAllConfigs(Integer nodeId, String processType, String targetParam);

    /**
     * 分页查询参数配置
     */
    PageList<NodeParamConfig> selectAllConfigsPaged(Integer nodeId, String processType, String targetParam, int pageNum, int pageSize);
}
