package com.helianhealth.agent.repository.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.helianhealth.agent.common.base.PageList;
import com.helianhealth.agent.repository.mapper.agent.NodeParamConfigMapper;
import com.helianhealth.agent.repository.model.assembler.NodeParamConfigAssembler;
import com.helianhealth.agent.repository.model.domain.NodeParamConfig;
import com.helianhealth.agent.repository.model.po.NodeParamConfigPO;
import com.helianhealth.agent.repository.service.NodeParamConfigQueryService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 节点参数配置查询服务基础实现
 */
@Service
public class NodeParamConfigQueryServiceImpl implements NodeParamConfigQueryService {

    protected final NodeParamConfigMapper nodeParamConfigMapper;

    public NodeParamConfigQueryServiceImpl(NodeParamConfigMapper nodeParamConfigMapper) {
        this.nodeParamConfigMapper = nodeParamConfigMapper;
    }

    @Override
    public List<NodeParamConfig> selectPreProcessConfigByNodeId(Integer nodeId) {
        return nodeParamConfigMapper.selectPreProcessConfigByNodeId(nodeId).stream()
                .map(NodeParamConfigAssembler::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<NodeParamConfig> selectPostProcessConfigByNodeId(Integer nodeId) {
        return nodeParamConfigMapper.selectPostProcessConfigByNodeId(nodeId).stream()
                .map(NodeParamConfigAssembler::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public NodeParamConfig selectConfigById(Integer configId) {
        if (configId == null) {
            return null;
        }
        NodeParamConfigPO po = nodeParamConfigMapper.selectByPrimaryKey(configId);
        return po != null ? NodeParamConfigAssembler.toDomain(po) : null;
    }

    @Override
    public List<NodeParamConfig> selectAllConfigs(Integer nodeId, String nodeName, String processType, String targetParam) {
        return nodeParamConfigMapper.selectAllParamConfig(nodeId, nodeName, processType, targetParam).stream()
                .map(NodeParamConfigAssembler::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public PageList<NodeParamConfig> selectAllConfigsPaged(Integer nodeId, String nodeName, String processType, String targetParam, int pageNum, int pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        List<NodeParamConfigPO> poList = nodeParamConfigMapper.selectAllParamConfig(nodeId, nodeName, processType, targetParam);
        PageInfo<NodeParamConfigPO> pageInfo = new PageInfo<>(poList);
        List<NodeParamConfig> configs = poList.stream().map(NodeParamConfigAssembler::toDomain).collect(Collectors.toList());
        PageList<NodeParamConfig> pageList = new PageList<>();
        pageList.setRows(configs);
        pageList.setTotal(pageInfo.getTotal());
        pageList.setPageNum(pageInfo.getPageNum());
        pageList.setPageSize(pageInfo.getPageSize());
        return pageList;
    }

}
