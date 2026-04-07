package com.helianhealth.agent.console.service;

import com.helianhealth.agent.common.base.ExtensionFilterConstants;
import com.helianhealth.agent.common.enums.ScheduleParamSourceType;
import com.helianhealth.agent.common.exception.WorkflowBusinessException;
import com.helianhealth.agent.common.extension.AgentExtensionLoader;
import com.helianhealth.agent.common.utils.SpelUtils;
import com.helianhealth.agent.console.model.dto.MockInvokeRequest;
import com.helianhealth.agent.invocation.remote.InterfaceRemoteInvoker;
import com.helianhealth.agent.invocation.remote.model.Invocation;
import com.helianhealth.agent.invocation.remote.wrapper.WrapperFactory;
import com.helianhealth.agent.repository.model.domain.MockConfig;
import com.helianhealth.agent.repository.model.domain.NodeParamConfig;
import com.helianhealth.agent.repository.model.domain.WorkflowNode;
import com.helianhealth.agent.repository.service.MockConfigQueryService;
import com.helianhealth.agent.repository.service.NodeParamConfigQueryService;
import com.helianhealth.agent.schedule.processor.BusinessDataSplitter;
import com.helianhealth.agent.schedule.processor.ParamFilterProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Mock调度服务
 * 独立实现Mock调度逻辑，不依赖schedule模块
 */
@Service
@Slf4j
public class MockScheduleService {

    private final InterfaceWorkflowNodeService flowNodeService;
    private final NodeParamConfigQueryService nodeParamConfigService;
    private final MockConfigQueryService mockConfigQueryService;
    private final ParamFilterProcessor businessDataPostProcessor;
    private final BusinessDataSplitter splitBusinessDataPostProcessor;
    private final ThreadPoolTaskExecutor taskExecutor;
    private InterfaceRemoteInvoker interfaceRemoteInvoker;

    public MockScheduleService(InterfaceWorkflowNodeService flowNodeService,
                               NodeParamConfigQueryService nodeParamConfigService,
                               MockConfigQueryService mockConfigQueryService,
                               ParamFilterProcessor businessDataPostProcessor,
                               BusinessDataSplitter splitBusinessDataPostProcessor,
                               @Qualifier("WorkFlowNodeExecutor") ThreadPoolTaskExecutor taskExecutor) {
        this.flowNodeService = flowNodeService;
        this.nodeParamConfigService = nodeParamConfigService;
        this.mockConfigQueryService = mockConfigQueryService;
        this.businessDataPostProcessor = businessDataPostProcessor;
        this.splitBusinessDataPostProcessor = splitBusinessDataPostProcessor;
        this.taskExecutor = taskExecutor;
    }

    /**
     * 延迟获取 InterfaceRemoteInvoker（避免构造时 Spring 容器未初始化）
     */
    private InterfaceRemoteInvoker getInvoker() {
        if (interfaceRemoteInvoker == null) {
            interfaceRemoteInvoker = WrapperFactory.createRemoteInvokerChain(
                    AgentExtensionLoader.getExtensionLoader(InterfaceRemoteInvoker.class).getAdaptiveExtension()
            );
        }
        return interfaceRemoteInvoker;
    }

    /**
     * 执行Mock调度
     *
     * @param request Mock调用请求
     * @return 最终执行结果
     */
    public Map<String, Object> mockSchedule(MockInvokeRequest request) {
        List<MockInvokeRequest.MockNodeConfig> mockConfigs = request.getMockConfigs();
        if (mockConfigs == null || mockConfigs.isEmpty()) {
            throw new IllegalArgumentException("Mock配置不能为空");
        }

        // 构建 nodeId -> mockId 映射
        Map<Integer, Integer> mockIdMap = mockConfigs.stream()
                .collect(Collectors.toMap(
                        MockInvokeRequest.MockNodeConfig::getNodeId,
                        MockInvokeRequest.MockNodeConfig::getMockId
                ));

        // 从第一个节点开始调度
        Integer startNodeId = mockConfigs.get(0).getNodeId();
        Integer startMockId = mockConfigs.get(0).getMockId();

        WorkflowNode startNode = buildMockNode(startNodeId, startMockId);
        return processMockNode(startNode, request.getBusinessData(), mockIdMap);
    }

    /**
     * 处理单个Mock节点
     */
    private Map<String, Object> processMockNode(WorkflowNode node,
                                                 Map<String, Object> businessData,
                                                 Map<Integer, Integer> mockIdMap) {
        Map<String, Object> filteredBusinessData = businessData;
        String paramFilterExpr = node.getParamFilterExpr();
        if (paramFilterExpr != null && !paramFilterExpr.trim().isEmpty()) {
            filteredBusinessData = businessDataPostProcessor.paramFilterPostProcess(paramFilterExpr, businessData);
            if (filteredBusinessData == null || filteredBusinessData.isEmpty()) {
                return new HashMap<>();
            }
        }

        Map<String, Object> nodeResponse;
        String splitField = node.getSplitField();
        if (splitField != null && !splitField.isEmpty()) {
            nodeResponse = parallelSplitNodeProcess(node, filteredBusinessData);
        } else {
            nodeResponse = getInvoker()
                    .remoteInvoke(node, buildInvocation(node, filteredBusinessData));
        }

        List<Integer> nextNodeIds = determineNextNode(node.getScheduleExpr(), nodeResponse);
        if (nextNodeIds == null || nextNodeIds.isEmpty()) {
            return nodeResponse;
        }

        for (Integer nextNodeId : nextNodeIds) {
            if (!mockIdMap.containsKey(nextNodeId)) {
                throw new IllegalArgumentException(
                        "调度的下游节点[" + nextNodeId + "]未配置Mock，请先配置该节点的Mock数据");
            }
        }

        Map<String, Object> nextNodeParam = determineNextNodeParam(
                node.getScheduleParamSourceType(), businessData, nodeResponse);

        if (nextNodeIds.size() == 1) {
            WorkflowNode nextNode = buildMockNode(nextNodeIds.get(0), mockIdMap.get(nextNodeIds.get(0)));
            return processMockNode(nextNode, nextNodeParam, mockIdMap);
        } else {
            Map<String, Object> mergedResult = new HashMap<>();
            for (Integer nextNodeId : nextNodeIds) {
                WorkflowNode nextNode = buildMockNode(nextNodeId, mockIdMap.get(nextNodeId));
                Map<String, Object> result = processMockNode(nextNode, nextNodeParam, mockIdMap);
                mergedResult.putAll(result);
            }
            return mergedResult;
        }
    }

    /**
     * 构建带Mock标记的节点
     */
    private WorkflowNode buildMockNode(Integer nodeId, Integer mockId) {
        // 验证Mock配置
        MockConfig mockConfig = mockConfigQueryService.findById(mockId);
        if (mockConfig == null) {
            throw new IllegalArgumentException("Mock配置不存在，mockId=" + mockId);
        }
        if (!mockConfig.getNodeId().equals(nodeId)) {
            throw new IllegalArgumentException(
                    "Mock配置不属于该节点，mockId=" + mockId + ", nodeId=" + nodeId);
        }

        // 获取节点信息
        WorkflowNode node = flowNodeService.selectByNodeId(nodeId);
        if (node == null) {
            throw new IllegalArgumentException("节点不存在，nodeId=" + nodeId);
        }

        // 设置Mock标记
        Map<String, String> extensionParam = new HashMap<>();
        if (node.getExtensionParam() != null) {
            extensionParam.putAll(node.getExtensionParam());
        }
        extensionParam.put(ExtensionFilterConstants.EXTENSION_PARAM_KEY_MOCK, mockId.toString());

        return WorkflowNode.builder()
                .nodeId(node.getNodeId())
                .nodeName(node.getNodeName())
                .flowId(node.getFlowId())
                .paramFilterExpr(node.getParamFilterExpr())
                .nodeType(node.getNodeType())
                .metaInfo(node.getMetaInfo())
                .splitField(node.getSplitField())
                .scheduleExpr(node.getScheduleExpr())
                .scheduleParamSourceType(node.getScheduleParamSourceType())
                .keywordField(node.getKeywordField())
                .remoteExtension(node.getRemoteExtension())
                .extensionParam(extensionParam)
                .build();
    }

    private Invocation buildInvocation(WorkflowNode node, Map<String, Object> businessData) {
        List<NodeParamConfig> preParamConfigs = nodeParamConfigService
                .selectPreProcessConfigByNodeId(node.getNodeId());
        List<NodeParamConfig> postParamConfigs = nodeParamConfigService
                .selectPostProcessConfigByNodeId(node.getNodeId());

        return Invocation.builder()
                .preParamConfigs(preParamConfigs)
                .postParamConfigs(postParamConfigs)
                .businessData(businessData)
                .build();
    }

    private List<Integer> determineNextNode(String scheduleExpr, Map<String, Object> nodeResponse) {
        if (scheduleExpr == null || scheduleExpr.trim().isEmpty()) {
            return null;
        }
        try {
            return SpelUtils.parseScheduleExpression(scheduleExpr, nodeResponse);
        } catch (Exception e) {
            log.error("调度表达式[{}]解析异常：", scheduleExpr, e);
            throw WorkflowBusinessException.EXPRESSION_PARSING_ERROR.toException();
        }
    }

    private Map<String, Object> determineNextNodeParam(ScheduleParamSourceType paramSourceType,
                                                        Map<String, Object> businessData,
                                                        Map<String, Object> nodeResponse) {
        if (paramSourceType == null || paramSourceType == ScheduleParamSourceType.ORIGINAL) {
            return businessData;
        }
        return nodeResponse;
    }

    private Map<String, Object> parallelSplitNodeProcess(WorkflowNode node, Map<String, Object> businessData) {
        List<Map<String, Object>> listBusinessData = splitBusinessDataPostProcessor.split(node.getSplitField(), businessData);
        if (listBusinessData.isEmpty()) {
            return mergeResults(Collections.emptyList());
        }

        List<CompletableFuture<Map<String, Object>>> futures = listBusinessData.stream()
                .map(map -> CompletableFuture.supplyAsync(
                        () -> getInvoker().remoteInvoke(node, buildInvocation(node, map)),
                        taskExecutor
                ).handle((result, ex) -> {
                    if (ex != null) {
                        log.error("节点[{}]并行执行失败，跳过该分片", node.getNodeName(), ex);
                        return Collections.<String, Object>emptyMap();
                    }
                    return result;
                }))
                .collect(Collectors.toList());

        List<Map<String, Object>> nodeResultList = futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());

        return mergeResults(nodeResultList);
    }

    private Map<String, Object> mergeResults(List<Map<String, Object>> resultList) {
        Map<String, Object> merged = new HashMap<>();
        for (Map<String, Object> nodeResult : resultList) {
            mergeMap(nodeResult, merged);
        }
        return merged;
    }

    @SuppressWarnings("unchecked")
    private void mergeMap(Map<String, Object> source, Map<String, Object> target) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (target.containsKey(key)) {
                Object existingValue = target.get(key);
                if (existingValue instanceof List) {
                    List<Object> mergedValue = mergeValues(existingValue, value);
                    target.put(key, mergedValue);
                } else if (existingValue instanceof Map && value instanceof Map) {
                    mergeMap((Map<String, Object>) value, (Map<String, Object>) existingValue);
                }
            } else {
                target.put(key, value);
            }
        }
    }

    private List<Object> mergeValues(Object existingValue, Object newValue) {
        List<Object> merged = new ArrayList<>();
        if (existingValue instanceof List) {
            merged.addAll((List<?>) existingValue);
        } else {
            merged.add(existingValue);
        }
        if (newValue instanceof List) {
            merged.addAll((List<?>) newValue);
        } else {
            merged.add(newValue);
        }
        return merged;
    }
}
