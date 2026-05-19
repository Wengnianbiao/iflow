package com.helianhealth.agent.ai.mcp.dispatcher;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.agent.ai.service.AiNodeParamConfigService;
import com.helianhealth.agent.common.exception.AgentException;
import com.helianhealth.agent.common.extension.AgentExtensionLoader;
import com.helianhealth.agent.invocation.remote.InterfaceRemoteInvoker;
import com.helianhealth.agent.invocation.remote.model.Invocation;
import com.helianhealth.agent.invocation.remote.wrapper.WrapperFactory;
import com.helianhealth.agent.repository.model.domain.NodeParamConfig;
import com.helianhealth.agent.repository.model.domain.WorkflowNode;
import com.helianhealth.agent.repository.service.InterfaceInvokeLogService;
import com.helianhealth.agent.schedule.builder.WorkFlowNodeBuilder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Tool: {@code agent_mock_invoke} —— Mock 链路验证域级 Tool 分发器。
 */
@Slf4j
@Component
public class MockInvokeToolDispatcher extends McpDispatcherSupport implements McpToolDispatcher {

    public static final String TOOL_NAME = "agent_mock_invoke";

    private final WorkFlowNodeBuilder workFlowNodeBuilder;
    private final AiNodeParamConfigService nodeParamConfigService;
    private final InterfaceInvokeLogService interfaceInvokeLogService;

    public MockInvokeToolDispatcher(ObjectMapper objectMapper,
                                    WorkFlowNodeBuilder workFlowNodeBuilder,
                                    AiNodeParamConfigService nodeParamConfigService,
                                    InterfaceInvokeLogService interfaceInvokeLogService) {
        super(objectMapper);
        this.workFlowNodeBuilder = workFlowNodeBuilder;
        this.nodeParamConfigService = nodeParamConfigService;
        this.interfaceInvokeLogService = interfaceInvokeLogService;
    }

    @Override
    public String toolName() {
        return TOOL_NAME;
    }

    @Override
    public Object call(JsonNode arguments) {
        String action = requireAction(arguments);
        switch (action) {
            case "invoke":
                return invokeOne(arguments);
            case "batch_invoke":
                return invokeBatch(arguments);
            default:
                throw new IllegalArgumentException("不支持的 action: " + action);
        }
    }

    private Map<String, Object> invokeOne(JsonNode args) {
        Integer nodeId = requireInt(args, "nodeId");
        Integer mockId = requireInt(args, "mockId");
        Map<String, Object> businessData = parseBusinessData(args);
        return doInvoke(nodeId, mockId, businessData);
    }

    private List<MockInvokeResult> invokeBatch(JsonNode args) {
        Integer nodeId = requireInt(args, "nodeId");
        List<Integer> mockIds = convertSubNodeList(args, "mockIds", Integer.class);
        if (mockIds == null || mockIds.isEmpty()) {
            throw new IllegalArgumentException("mockIds 不能为空");
        }
        Map<String, Object> businessData = parseBusinessData(args);

        List<MockInvokeResult> results = new ArrayList<>(mockIds.size());
        for (Integer mockId : mockIds) {
            try {
                Map<String, Object> result = doInvoke(nodeId, mockId, businessData);
                results.add(new MockInvokeResult(mockId, true, "验证成功", result));
            } catch (Exception e) {
                log.error("Mock 场景验证失败: mockId={}", mockId, e);
                results.add(new MockInvokeResult(mockId, false, e.getMessage(), null));
            }
        }
        return results;
    }

    private Map<String, Object> doInvoke(Integer nodeId, Integer mockId, Map<String, Object> businessData) {
        try {
            WorkflowNode workflowNode = workFlowNodeBuilder.buildWithMock(nodeId, mockId);

            List<NodeParamConfig> preParamConfigs = nodeParamConfigService.findPreProcessDomainConfigs(nodeId);
            List<NodeParamConfig> postParamConfigs = nodeParamConfigService.findPostProcessDomainConfigs(nodeId);

            Invocation invocation = Invocation.builder()
                    .preParamConfigs(preParamConfigs)
                    .postParamConfigs(postParamConfigs)
                    .businessData(businessData)
                    .build();

            InterfaceRemoteInvoker invoker = AgentExtensionLoader
                    .getExtensionLoader(InterfaceRemoteInvoker.class).getAdaptiveExtension();
            return WrapperFactory.createRemoteInvokerChain(invoker, interfaceInvokeLogService)
                    .remoteInvoke(workflowNode, invocation);
        } catch (AgentException e) {
            log.error("Mock 调用异常, nodeId={}, mockId={}: {}", nodeId, mockId, e.getMessage(), e);
            throw e;
        }
    }

    private Map<String, Object> parseBusinessData(JsonNode args) {
        JsonNode node = args == null ? null : args.get("businessData");
        if (node == null || node.isNull()) {
            return Collections.emptyMap();
        }
        return objectMapper.convertValue(node, new TypeReference<Map<String, Object>>() {});
    }

    @Data
    @AllArgsConstructor
    public static class MockInvokeResult {
        private Integer mockId;
        private Boolean success;
        private String message;
        private Map<String, Object> result;
    }
}
