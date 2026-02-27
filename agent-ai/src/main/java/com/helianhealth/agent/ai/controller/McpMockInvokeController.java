package com.helianhealth.agent.ai.controller;

import com.helianhealth.agent.common.base.ResultData;
import com.helianhealth.agent.common.extension.AgentExtensionLoader;
import com.helianhealth.agent.common.utils.ResponseRenderUtils;
import com.helianhealth.agent.invocation.remote.InterfaceRemoteInvoker;
import com.helianhealth.agent.invocation.remote.model.InterfaceWorkflowNode;
import com.helianhealth.agent.invocation.remote.model.Invocation;
import com.helianhealth.agent.invocation.remote.wrapper.WrapperFactory;
import com.helianhealth.agent.schedule.builder.WorkFlowNodeBuilder;
import com.helianhealth.agent.repository.model.domain.NodeParamConfig;
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
 * MCP接口 - Mock调用测试
 * 提供给AI使用的Mock验证接口，用于自动验证参数映射配置是否正确
 */
@RestController
@RequestMapping("/v1/ai/mcp/mock-invoke")
@Slf4j
@AllArgsConstructor
public class McpMockInvokeController {

    private final WorkFlowNodeBuilder workFlowNodeBuilder;
    private final NodeParamConfigService nodeParamConfigService;

    /**
     * 执行Mock调用验证
     * AI在配置完参数映射后，自动调用此接口验证映射是否正确
     *
     * @param nodeId 节点ID
     * @param mockId Mock配置ID
     * @param businessData 业务数据（模拟上游系统的请求数据）
     * @return 返回工作流执行结果，包含前置映射和后置映射的结果
     */
    @PostMapping("/invoke")
    public ResultData<Map<String, Object>> invokeMockNode(
            @RequestParam("nodeId") Integer nodeId,
            @RequestParam("mockId") Integer mockId,
            @RequestBody Map<String, Object> businessData) {
        try {
            log.info("MCP执行Mock调用验证: nodeId={}, mockId={}", nodeId, mockId);

            // 1. 构建包含Mock配置的工作流节点
            InterfaceWorkflowNode workflowNode = workFlowNodeBuilder.buildWithMock(nodeId, mockId);

            // 2. 获取前置和后置参数配置
            List<NodeParamConfig> preParamConfigs = nodeParamConfigService.selectPreProcessConfigByNodeId(nodeId);
            List<NodeParamConfig> postParamConfigs = nodeParamConfigService.selectPostProcessConfigByNodeId(nodeId);

            // 3. 构建调用上下文
            Invocation invocation = Invocation.builder()
                    .preParamConfigs(preParamConfigs)
                    .postParamConfigs(postParamConfigs)
                    .businessData(businessData)
                    .build();

            // 4. 执行Mock调用
            InterfaceRemoteInvoker invoker = AgentExtensionLoader.getExtensionLoader(InterfaceRemoteInvoker.class).getAdaptiveExtension();
            Map<String, Object> result = WrapperFactory.createRemoteInvokerChain(invoker).remoteInvoke(workflowNode, invocation);

            log.info("MCP Mock调用成功: nodeId={}, mockId={}", nodeId, mockId);
            return ResponseRenderUtils.render(result);
        } catch (IllegalArgumentException e) {
            log.error("MCP Mock调用参数错误, nodeId={}, mockId={}", nodeId, mockId, e);
            return ResponseRenderUtils.error(e.getMessage());
        } catch (Exception e) {
            log.error("MCP Mock调用失败, nodeId={}, mockId={}", nodeId, mockId, e);
            return ResponseRenderUtils.error("Mock调用失败: " + e.getMessage());
        }
    }

    /**
     * 批量执行Mock验证
     * AI可以使用此接口对同一个节点的多个Mock场景进行批量验证
     *
     * @param nodeId 节点ID
     * @param mockIds Mock配置ID列表
     * @param businessData 业务数据
     * @return 返回每个Mock场景的执行结果
     */
    @PostMapping("/batch-invoke")
    public ResultData<List<MockInvokeResult>> batchInvokeMock(
            @RequestParam("nodeId") Integer nodeId,
            @RequestParam("mockIds") List<Integer> mockIds,
            @RequestBody Map<String, Object> businessData) {
        try {
            log.info("MCP批量执行Mock调用: nodeId={}, mockIds={}", nodeId, mockIds);

            List<MockInvokeResult> results = mockIds.stream().map(mockId -> {
                try {
                    ResultData<Map<String, Object>> result = invokeMockNode(nodeId, mockId, businessData);
                    return new MockInvokeResult(mockId, true, "验证成功", result.getRsp());
                } catch (Exception e) {
                    log.error("Mock场景验证失败: mockId={}", mockId, e);
                    return new MockInvokeResult(mockId, false, e.getMessage(), null);
                }
            }).collect(Collectors.toList());

            return ResponseRenderUtils.render(results);
        } catch (Exception e) {
            log.error("MCP批量Mock调用失败", e);
            return ResponseRenderUtils.error("批量Mock调用失败: " + e.getMessage());
        }
    }

    /**
     * Mock调用结果
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class MockInvokeResult {
        private Integer mockId;
        private Boolean success;
        private String message;
        private Map<String, Object> result;
    }
}
