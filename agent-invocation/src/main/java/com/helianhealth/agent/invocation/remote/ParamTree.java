package com.helianhealth.agent.invocation.remote;

import com.helianhealth.agent.common.extension.AgentAdaptive;
import com.helianhealth.agent.common.extension.AgentSPI;
import com.helianhealth.agent.repository.model.domain.WorkflowNode;
import com.helianhealth.agent.repository.model.domain.NodeParamConfig;
import com.helianhealth.agent.invocation.remote.model.ParamTreeNode;
import com.helianhealth.agent.invocation.remote.model.PostBuildContext;
import com.helianhealth.agent.invocation.remote.model.PreBuildContext;

import java.util.List;

/**
 * 参数树构建器
 * 负责将配置化的参数映射规则转换为实际的参数树结构
 */
@AgentSPI("default")
public interface ParamTree {

    /**
     * 构建前置参数树（调用前的参数转换）
     * @param flowNode 流程节点
     * @param configs 参数配置列表
     * @param parentId 父节点 ID（用于递归构建）
     * @param buildContext 前置构建上下文，封装了所有需要的数据源
     * @return 构建好的参数树节点列表
     */
    @AgentAdaptive("node")
    List<ParamTreeNode> buildPreParamTree(WorkflowNode flowNode,
                                          List<NodeParamConfig> configs,
                                          Integer parentId,
                                          PreBuildContext buildContext);

    /**
     * 构建后置参数树（调用后的响应转换）
     * @param flowNode 流程节点
     * @param configs 参数配置列表
     * @param parentId 父节点 ID（用于递归构建）
     * @param buildContext 后置构建上下文，封装了输入和响应两份数据源
     * @return 构建好的参数树节点列表
     */
    @AgentAdaptive("node")
    List<ParamTreeNode> buildPostParamTree(WorkflowNode flowNode,
                                           List<NodeParamConfig> configs,
                                           Integer parentId,
                                           PostBuildContext buildContext);
}
