package com.helianhealth.agent.remote;

import com.helianhealth.agent.model.domain.InterfaceWorkflowNode;
import com.helianhealth.agent.model.domain.NodeParamConfigDO;
import com.helianhealth.agent.model.dto.ParamTreeNode;
import com.helianhealth.agent.service.NodeParamConfigService;
import lombok.Setter;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DefaultInterfaceRemoteInvoker implements InterfaceRemoteInvoker {

    @Setter
    private NodeParamConfigService nodeParamConfigService;

    private final ParamTree paramNode = ExtensionLoader.getExtensionLoader(ParamTree.class).getAdaptiveExtension();

    private final InterfaceClientProxy proxy = ExtensionLoader.getExtensionLoader(InterfaceClientProxy.class).getAdaptiveExtension();


    @Override
    public Map<String, Object> remoteInvoke(InterfaceWorkflowNode flowNode, Map<String, Object> businessData) {
        // 1. 参数前处理
        List<ParamTreeNode> paramBeforeInvoke = preProcess(flowNode, businessData);

        // 2. 执行协议的调用,由子类实现
        Map<String, Object> response = proxy.doInvoke(flowNode, paramBeforeInvoke);

        // 3. 参数后处理
        return postProcess(flowNode, response, businessData);
    }


    /**
     * 参数前置处理转化成参数树
     * 子类可以根据需要重写此方法
     * @param flowNode 工作流节点
     * @param businessData 业务数据
     * @return 处理后的参数
     */
    protected List<ParamTreeNode> preProcess(InterfaceWorkflowNode flowNode, Map<String, Object> businessData) {
        // 获取的是前置参数配置
        List<NodeParamConfigDO> nodeConfigs = nodeParamConfigService.selectPreProcessConfigByNodeId(flowNode.getNodeId());
        if (CollectionUtils.isEmpty(nodeConfigs)) {
            return new ArrayList<>();
        }
        return paramNode.buildParamTree(flowNode.getUrl(), nodeConfigs, null, businessData, businessData, businessData);
    }

    /**
     * 参数后置处理
     * @param flowNode 工作流节点
     * @param response 调用响应
     * @param businessData 原始业务数据
     * @return 处理后的结果
     */
    public Map<String, Object> postProcess(InterfaceWorkflowNode flowNode, Map<String, Object> response, Map<String, Object> businessData) {
        // 获取的是后置参数配置
        List<NodeParamConfigDO> nodeConfigs = nodeParamConfigService.selectPostProcessConfigByNodeId(flowNode.getNodeId());
        if (CollectionUtils.isEmpty(nodeConfigs)) {
            return new HashMap<>();
        }

        List<ParamTreeNode> rootNodes = paramNode.buildParamTree(flowNode.getUrl(), nodeConfigs, null, response, response, businessData);

        return ProxyConvertHelper.convertResponse(flowNode, rootNodes);
    }

}
