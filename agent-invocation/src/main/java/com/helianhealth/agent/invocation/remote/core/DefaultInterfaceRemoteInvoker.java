package com.helianhealth.agent.invocation.remote.core;

import com.helianhealth.agent.common.extension.AgentExtensionLoader;
import com.helianhealth.agent.invocation.remote.InterfaceClientProxy;
import com.helianhealth.agent.invocation.remote.InterfaceRemoteInvoker;
import com.helianhealth.agent.invocation.remote.ParamTree;
import com.helianhealth.agent.repository.model.domain.WorkflowNode;
import com.helianhealth.agent.invocation.remote.param.model.Invocation;
import com.helianhealth.agent.repository.model.domain.NodeParamConfig;
import com.helianhealth.agent.invocation.remote.param.model.ParamTreeNode;
import com.helianhealth.agent.invocation.remote.param.model.PostBuildContext;
import com.helianhealth.agent.invocation.remote.param.model.PreBuildContext;
import com.helianhealth.agent.invocation.remote.chain.WrapperFactory;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DefaultInterfaceRemoteInvoker implements InterfaceRemoteInvoker {

    private final ParamTree paramNode;

    private final InterfaceClientProxy proxy;


    public DefaultInterfaceRemoteInvoker() {
        InterfaceClientProxy rawProxy = AgentExtensionLoader.getExtensionLoader(InterfaceClientProxy.class).getAdaptiveExtension();
        this.proxy = WrapperFactory.createClientProxyChain(rawProxy);
        this.paramNode = AgentExtensionLoader.getExtensionLoader(ParamTree.class).getAdaptiveExtension();
    }


    @Override
    public Map<String, Object> remoteInvoke(WorkflowNode flowNode, Invocation invocation) {
        // 1. 参数前处理
        List<ParamTreeNode> paramBeforeInvoke = preProcess(flowNode, invocation);

        // 2. 执行协议的调用
        Map<String, Object> response = proxy.doInvoke(flowNode, paramBeforeInvoke);

        // 3. 参数后处理
        return postProcess(flowNode, response, invocation);
    }


    /**
     * 参数前置处理转化成参数树
     * 子类可以根据需要重写此方法
     * @param flowNode 工作流节点
     * @param invocation 调用上下文
     * @return 处理后的参数
     */
    protected List<ParamTreeNode> preProcess(WorkflowNode flowNode, Invocation invocation) {
        List<NodeParamConfig> nodeConfigs = invocation.getPreParamConfigs();
        if (CollectionUtils.isEmpty(nodeConfigs)) {
            return new ArrayList<>();
        }

        Map<String, Object> businessData = invocation.getBusinessData();
        PreBuildContext context = PreBuildContext.builder()
                .currentData(businessData)
                .parentData(businessData)
                .rootData(businessData)
                .build();
        return paramNode.buildPreParamTree(flowNode, nodeConfigs, null, context);
    }

    /**
     * 参数后置处理
     * @param flowNode 工作流节点
     * @param response 调用响应
     * @param invocation 调用上下文
     * @return 处理后的结果
     */
    public Map<String, Object> postProcess(WorkflowNode flowNode, Map<String, Object> response, Invocation invocation) {
        // 从 invocation 获取后置参数配置
        List<NodeParamConfig> nodeConfigs = invocation.getPostParamConfigs();
        if (CollectionUtils.isEmpty(nodeConfigs)) {
            return response;  // 无配置时返回原始响应
        }

        Map<String, Object> businessData = invocation.getBusinessData();
        PostBuildContext context = PostBuildContext.builder()
                .currentInputData(businessData)
                .currentResponseData(response)
                .rootInputData(businessData)
                .rootResponseData(response)
                .build();
        List<ParamTreeNode> rootNodes = paramNode.buildPostParamTree(flowNode,
                nodeConfigs, null, context);

        return ProxyConvertHelper.convertResponse(flowNode, rootNodes);
    }

}
