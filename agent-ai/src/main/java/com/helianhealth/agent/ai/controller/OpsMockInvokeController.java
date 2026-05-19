package com.helianhealth.agent.ai.controller;

import com.helianhealth.agent.common.base.ResultData;
import com.helianhealth.agent.common.exception.AgentException;
import com.helianhealth.agent.common.extension.AgentExtensionLoader;
import com.helianhealth.agent.invocation.remote.InterfaceRemoteInvoker;
import com.helianhealth.agent.repository.model.domain.WorkflowNode;
import com.helianhealth.agent.invocation.remote.model.Invocation;
import com.helianhealth.agent.invocation.remote.wrapper.WrapperFactory;
import com.helianhealth.agent.schedule.builder.WorkFlowNodeBuilder;
import com.helianhealth.agent.repository.model.domain.NodeParamConfig;
import com.helianhealth.agent.repository.service.InterfaceInvokeLogService;
import com.helianhealth.agent.ai.service.AiNodeParamConfigService;
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
 * Ops 接口 - Mock 调用测试（底层 REST 能力面）。
 */
@RestController
@RequestMapping("/v1/ai/ops/mock-invoke")
@Slf4j
@AllArgsConstructor
public class OpsMockInvokeController {

    private final WorkFlowNodeBuilder workFlowNodeBuilder;
    private final AiNodeParamConfigService nodeParamConfigService;
    private final InterfaceInvokeLogService interfaceInvokeLogService;

    @PostMapping("/invoke")
    public ResultData<Map<String, Object>> invokeMockNode(
            @RequestParam("nodeId") Integer nodeId,
            @RequestParam("mockId") Integer mockId,
            @RequestBody Map<String, Object> businessData) {
        try {
            log.info("Ops 执行 Mock 调用验证: nodeId={}, mockId={}", nodeId, mockId);

            WorkflowNode workflowNode = workFlowNodeBuilder.buildWithMock(nodeId, mockId);

            List<NodeParamConfig> preParamConfigs = nodeParamConfigService.findPreProcessDomainConfigs(nodeId);
            List<NodeParamConfig> postParamConfigs = nodeParamConfigService.findPostProcessDomainConfigs(nodeId);

            Invocation invocation = Invocation.builder()
                    .preParamConfigs(preParamConfigs)
                    .postParamConfigs(postParamConfigs)
                    .businessData(businessData)
                    .build();

            InterfaceRemoteInvoker invoker = AgentExtensionLoader.getExtensionLoader(InterfaceRemoteInvoker.class).getAdaptiveExtension();
            Map<String, Object> result = WrapperFactory.createRemoteInvokerChain(invoker, interfaceInvokeLogService).remoteInvoke(workflowNode, invocation);

            log.info("Ops Mock 调用成功: nodeId={}, mockId={}", nodeId, mockId);
            return ResultData.ok(result);
        } catch (AgentException e) {
            log.error("Ops Mock 调用异常, nodeId={}, mockId={}: {}", nodeId, mockId, e.getMessage(), e);
            return ResultData.error(e.getMessage());
        } catch (Exception e) {
            log.error("Ops Mock 调用未知异常, nodeId={}, mockId={}", nodeId, mockId, e);
            return ResultData.error("Mock调用失败: " + e.getMessage());
        }
    }

    @PostMapping("/batch-invoke")
    public ResultData<List<MockInvokeResult>> batchInvokeMock(
            @RequestParam("nodeId") Integer nodeId,
            @RequestParam("mockIds") List<Integer> mockIds,
            @RequestBody Map<String, Object> businessData) {
        try {
            log.info("Ops 批量执行 Mock 调用: nodeId={}, mockIds={}", nodeId, mockIds);

            List<MockInvokeResult> results = mockIds.stream().map(mockId -> {
                try {
                    ResultData<Map<String, Object>> result = invokeMockNode(nodeId, mockId, businessData);
                    return new MockInvokeResult(mockId, true, "验证成功", result.getData());
                } catch (Exception e) {
                    log.error("Mock 场景验证失败: mockId={}", mockId, e);
                    return new MockInvokeResult(mockId, false, e.getMessage(), null);
                }
            }).collect(Collectors.toList());

            return ResultData.ok(results);
        } catch (Exception e) {
            log.error("Ops 批量 Mock 调用失败", e);
            return ResultData.error("批量 Mock 调用失败: " + e.getMessage());
        }
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class MockInvokeResult {
        private Integer mockId;
        private Boolean success;
        private String message;
        private Map<String, Object> result;
    }
}
