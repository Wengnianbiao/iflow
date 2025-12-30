package com.helianhealth.agent.invocation.remote;

import com.helianhealth.agent.invocation.remote.model.InterfaceWorkflowNode;
import com.helianhealth.agent.invocation.remote.model.ParamTreeNode;

import java.util.List;

/**
 * 参数解析器接口
 * 负责将 ParamTreeNode 结构解析为具体协议所需的格式（如 HTTP JSON、SQL、SOAP XML）
 */
public interface ParamResolver {

    /**
     * 解析参数节点为协议特定格式
     * @param flowNode 工作流节点
     * @param params 参数树节点列表
     * @return 解析后的字符串（JSON、SQL、XML等）
     */
    String resolveParamNodes(InterfaceWorkflowNode flowNode, List<ParamTreeNode> params);
}
