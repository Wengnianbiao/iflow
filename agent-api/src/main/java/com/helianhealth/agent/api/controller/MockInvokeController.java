package com.helianhealth.agent.api.controller;

import com.helianhealth.agent.common.ResultData;
import com.helianhealth.agent.utils.ResponseRenderUtils;
import com.helianhealth.agent.schedule.build.WorkFlowNodeBuilder;
import com.helianhealth.agent.workflow.model.domain.InterfaceWorkflowNode;
import com.helianhealth.agent.invocation.remote.InterfaceRemoteInvoker;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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
            // 使用buildWithMock方法构建带Mock配置的节点
            InterfaceWorkflowNode workflowNode = workFlowNodeBuilder.buildWithMock(nodeId, mockId);

            // 直接调用远程调用器执行节点
            InterfaceRemoteInvoker invoker = ExtensionLoader.getExtensionLoader(InterfaceRemoteInvoker.class).getAdaptiveExtension();
            Map<String, Object> result = invoker.remoteInvoke(workflowNode, businessData);

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
