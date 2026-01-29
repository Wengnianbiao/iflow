package com.helianhealth.agent.api.controller;

import com.helianhealth.agent.common.base.ResultData;
import com.helianhealth.agent.common.extension.AgentExtensionLoader;
import com.helianhealth.agent.common.utils.ResponseRenderUtils;
import com.helianhealth.agent.invocation.remote.InterfaceRemoteInvoker;
import com.helianhealth.agent.invocation.remote.model.InterfaceWorkflowNode;
import com.helianhealth.agent.invocation.remote.model.Invocation;
import com.helianhealth.agent.invocation.remote.wrapper.WrapperFactory;
import com.helianhealth.agent.schedule.builder.WorkFlowNodeBuilder;
import com.helianhealth.agent.schedule.convert.NodeParamConfigConvert;
import com.helianhealth.agent.console.model.domain.NodeParamConfigDO;
import com.helianhealth.agent.console.service.NodeParamConfigService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Mock测试接口控制器
 * 提供Mock工作流节点的调用接口
 */
@RestController
@RequestMapping("/v1/console/mock")
@Slf4j
@AllArgsConstructor
public class MockInvokeController {

    private final WorkFlowNodeBuilder workFlowNodeBuilder;
    private final NodeParamConfigService nodeParamConfigService;

    /**
     * 调用Mock工作流节点
     * 根据NodeId和MockId构建节点并执行
     */
    @PostMapping("/invoke")
    public ResultData<Map<String, Object>> invokeMockNode(
            @RequestParam("nodeId") Integer nodeId,
            @RequestParam("mockId") Integer mockId,
            @RequestBody Map<String, Object> businessData) {
        try {
            InterfaceWorkflowNode workflowNode = workFlowNodeBuilder.buildWithMock(nodeId, mockId);
            List<NodeParamConfigDO> preParamConfigs = nodeParamConfigService.selectPreProcessConfigByNodeId(nodeId);
            List<NodeParamConfigDO> postParamConfigs = nodeParamConfigService.selectPostProcessConfigByNodeId(nodeId);
            Invocation invocation = Invocation.builder()
                    .preParamConfigs(preParamConfigs.stream()
                            .map(NodeParamConfigConvert::convertToDomain)
                            .collect(Collectors.toList()))
                    .postParamConfigs(postParamConfigs.stream()
                            .map(NodeParamConfigConvert::convertToDomain)
                            .collect(Collectors.toList()))
                    .businessData(businessData)
                    .build();

            // 直接调用远程调用器执行节点
            InterfaceRemoteInvoker invoker = AgentExtensionLoader.getExtensionLoader(InterfaceRemoteInvoker.class).getAdaptiveExtension();
            Map<String, Object> result = WrapperFactory.createRemoteInvokerChain(invoker).remoteInvoke(workflowNode, invocation);

            return ResponseRenderUtils.render(result);
        } catch (IllegalArgumentException e) {
            log.error("调用Mock节点参数错误, nodeId={}, mockId={}", nodeId, mockId, e);
            return ResponseRenderUtils.error(e.getMessage());
        } catch (Exception e) {
            log.error("调用Mock节点失败, nodeId={}, mockId={}", nodeId, mockId, e);
            return ResponseRenderUtils.error("调用Mock节点失败: " + e.getMessage());
        }
    }
}
