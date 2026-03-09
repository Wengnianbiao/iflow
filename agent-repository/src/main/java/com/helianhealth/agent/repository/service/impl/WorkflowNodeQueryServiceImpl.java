package com.helianhealth.agent.repository.service.impl;

import com.helianhealth.agent.common.utils.JsonUtils;
import com.helianhealth.agent.repository.mapper.InterfaceFlowNodeMapper;
import com.helianhealth.agent.repository.model.domain.WorkflowNode;
import com.helianhealth.agent.repository.model.po.InterfaceWorkflowNodePO;
import com.helianhealth.agent.repository.service.WorkflowNodeQueryService;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 工作流节点查询服务基础实现
 */
@Service
public class WorkflowNodeQueryServiceImpl implements WorkflowNodeQueryService {

    protected final InterfaceFlowNodeMapper flowNodeMapper;

    public WorkflowNodeQueryServiceImpl(InterfaceFlowNodeMapper flowNodeMapper) {
        this.flowNodeMapper = flowNodeMapper;
    }

    @Override
    public WorkflowNode selectByNodeId(Integer nodeId) {
        InterfaceWorkflowNodePO po = flowNodeMapper.selectByNodeId(nodeId);
        return toWorkflowNode(po);
    }

    protected WorkflowNode toWorkflowNode(InterfaceWorkflowNodePO po) {
        if (po == null) {
            return null;
        }
        Map<String, String> extensionParam = null;
        if (po.getExtensionParam() != null && !po.getExtensionParam().isEmpty()) {
            extensionParam = JsonUtils.fromJsonStringToMap(po.getExtensionParam());
        }
        return WorkflowNode.builder()
                .nodeId(po.getNodeId())
                .nodeName(po.getNodeName())
                .flowId(po.getFlowId())
                .paramFilterExpr(po.getParamFilterExpr())
                .nodeType(po.getNodeType())
                .metaInfo(po.getMetaInfo())
                .splitField(po.getSplitField())
                .scheduleExpr(po.getScheduleExpr())
                .scheduleParamSourceType(po.getScheduleParamSourceType())
                .keywordField(po.getKeywordField())
                .remoteExtension(po.getRemoteExtension())
                .extensionParam(extensionParam)
                .build();
    }
}
