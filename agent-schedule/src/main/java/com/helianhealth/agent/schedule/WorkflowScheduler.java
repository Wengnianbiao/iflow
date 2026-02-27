package com.helianhealth.agent.schedule;

import com.helianhealth.agent.common.enums.ScheduleParamSourceType;
import com.helianhealth.agent.common.exception.WorkflowBusinessException;
import com.helianhealth.agent.common.extension.AgentExtensionLoader;
import com.helianhealth.agent.repository.model.domain.NodeParamConfig;
import com.helianhealth.agent.repository.service.NodeParamConfigQueryService;
import com.helianhealth.agent.invocation.remote.InterfaceRemoteInvoker;
import com.helianhealth.agent.invocation.remote.model.InterfaceWorkflowNode;
import com.helianhealth.agent.invocation.remote.model.Invocation;
import com.helianhealth.agent.invocation.remote.wrapper.WrapperFactory;
import com.helianhealth.agent.common.utils.SpelUtils;
import com.helianhealth.agent.schedule.builder.WorkFlowNodeBuilder;
import com.helianhealth.agent.schedule.processor.BusinessDataSplitter;
import com.helianhealth.agent.schedule.processor.ParamFilterProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 工作流引擎调度器
 * 负责编排整个工作流
 * 对于工作流整个处理都是线性的，但节点之间是可以并行的
 */
@Component
@Slf4j
public class WorkflowScheduler {

    private final WorkFlowNodeBuilder workFlowNodeBuilder;
    private final ParamFilterProcessor businessDataPostProcessor;
    private final BusinessDataSplitter splitBusinessDataPostProcessor;
    private final NodeParamConfigQueryService nodeParamConfigService;
    private final ThreadPoolTaskExecutor taskExecutor;

    /**
     * 延迟初始化的InterfaceRemoteInvoker
     */
    private volatile InterfaceRemoteInvoker interfaceRemoteInvoker;

    public WorkflowScheduler(WorkFlowNodeBuilder workFlowNodeBuilder,
                           ParamFilterProcessor businessDataPostProcessor,
                           BusinessDataSplitter splitBusinessDataPostProcessor,
                           NodeParamConfigQueryService nodeParamConfigService,
                           @Qualifier("WorkFlowNodeExecutor") ThreadPoolTaskExecutor taskExecutor) {
        this.workFlowNodeBuilder = workFlowNodeBuilder;
        this.businessDataPostProcessor = businessDataPostProcessor;
        this.splitBusinessDataPostProcessor = splitBusinessDataPostProcessor;
        this.nodeParamConfigService = nodeParamConfigService;
        this.taskExecutor = taskExecutor;
    }

    /**
     * 获取InterfaceRemoteInvoker实例（延迟初始化）
     */
    private InterfaceRemoteInvoker getInterfaceRemoteInvoker() {
        if (interfaceRemoteInvoker == null) {
            synchronized (this) {
                if (interfaceRemoteInvoker == null) {
                    // 使用显式包装工厂创建包装链
                    InterfaceRemoteInvoker rawInvoker = AgentExtensionLoader
                        .getExtensionLoader(InterfaceRemoteInvoker.class)
                        .getAdaptiveExtension();
                    interfaceRemoteInvoker = WrapperFactory.createRemoteInvokerChain(rawInvoker);
                }
            }
        }
        return interfaceRemoteInvoker;
    }

    /**
     * 工作流调度
     * @param flowNodes 待调度节点
     * @param businessData 业务数据,业务数据一定是Map<String, Object>结构,而且这个Object得是JSON格式
     * @return 统一出参，出参怎么适配是工作流节点自己的事情，你只需要帮我处理好返回统一出参即可
     */
    public Map<String, Object> schedule(List<InterfaceWorkflowNode> flowNodes, Map<String, Object> businessData) {
        Map<String, Object> flowNodeResponse;
        if (flowNodes != null && flowNodes.size() == 1) {
            // 单节点处理
            flowNodeResponse = processSingleNode(flowNodes.get(0), businessData);
        } else if (flowNodes != null && flowNodes.size() > 1) {
            // 多个节点,则说明这是个并行业务,譬如申请单,节点工作完成后需要有个聚合操作
            flowNodeResponse = processParallelNode(flowNodes, businessData);
        } else {
            log.error(WorkflowBusinessException.FLOW_NOT_FOUND.getMessage());
            throw WorkflowBusinessException.FLOW_NOT_FOUND.toException();
        }
        return flowNodeResponse;
    }

    private Map<String, Object> processSingleNode(InterfaceWorkflowNode flowNode, Map<String, Object> businessData) {
        // 1、根据规则表达式对入参进行预处理
        Map<String, Object> businessDataAfterPost = businessDataPostProcessor.paramFilterPostProcess(
                flowNode.getParamFilterExpr(), businessData);
        // 有过滤配置&&过滤后的数据为空则直接返回空map
        if ((flowNode.getParamFilterExpr() != null && !flowNode.getParamFilterExpr().isEmpty())
                && (businessDataAfterPost == null || businessDataAfterPost.isEmpty())) {
            return new HashMap<>();
        }
        // 2、执行节点
        Map<String, Object> nodeResponse;
        String splitField = flowNode.getSplitField();
        if (splitField != null && !splitField.isEmpty()) {
            // 按照字段拆分并行处理节点
            nodeResponse = parallelSplitNodeProcess(flowNode, businessDataAfterPost);
        } else {
            // 单节点处理 - 直接调用远程调用器
            nodeResponse = getInterfaceRemoteInvoker().remoteInvoke(flowNode, buildInvocation(flowNode, businessDataAfterPost));
        }

        // 3、根据表达式调度下一个节点
        List<Integer> nextNodes = determineNextNode(flowNode.getScheduleExpr(), nodeResponse);
        if (nextNodes == null || nextNodes.isEmpty()) {
            return nodeResponse;
        }
        List<InterfaceWorkflowNode> workflowNodes = workFlowNodeBuilder.build(nextNodes);

        // 4、调度节点的入参来源
        Map<String, Object> nextNodeParam = determineNextNodeParam(flowNode.getScheduleParamSourceType(),
                businessData, nodeResponse);

        return schedule(workflowNodes, nextNodeParam);
    }

    private Invocation buildInvocation(InterfaceWorkflowNode flowNode, Map<String, Object> businessData) {
        List<NodeParamConfig> preParamConfigs = nodeParamConfigService.selectPreProcessConfigByNodeId(flowNode.getNodeId());
        List<NodeParamConfig> postParamConfigs = nodeParamConfigService.selectPostProcessConfigByNodeId(flowNode.getNodeId());

        return Invocation.builder()
                .preParamConfigs(preParamConfigs)
                .postParamConfigs(postParamConfigs)
                .businessData(businessData)
                .build();
    }



    private Map<String, Object> parallelSplitNodeProcess(InterfaceWorkflowNode flowNode, Map<String, Object> businessData) {
        // 1. 拆分业务数据
        List<Map<String, Object>> listBusinessData = splitBusinessDataPostProcessor.split(flowNode.getSplitField(), businessData);
        if (listBusinessData.isEmpty()) {
            return mergeResults(Collections.emptyList());
        }

        // 2. 并行执行任务并收集Future
        List<CompletableFuture<Map<String, Object>>> futures = listBusinessData.stream()
                .map(map -> CompletableFuture.supplyAsync(
                        () -> getInterfaceRemoteInvoker().remoteInvoke(flowNode, buildInvocation(flowNode, map)),
                        taskExecutor
                ))
                .collect(Collectors.toList());

        // 3. 等待所有任务完成，并收集结果
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        List<Map<String, Object>> nodeResultList = allFutures.thenApply(v ->
                futures.stream()
                        .map(CompletableFuture::join)
                        .collect(Collectors.toList())
        ).join();

        // 4. 合并结果并返回
        return mergeResults(nodeResultList);
    }

    private Map<String, Object> determineNextNodeParam(ScheduleParamSourceType paramSourceType,
                                                       Map<String, Object> businessData,
                                                       Map<String, Object> preFlowNodeResponse) {
        if (Objects.requireNonNull(paramSourceType) == ScheduleParamSourceType.ORIGINAL) {
            return businessData;
        }

        return preFlowNodeResponse;
    }

    private List<Integer> determineNextNode(String scheduleExpr, Map<String, Object> preFlowNodeResponse) {
        try {
            return SpelUtils.parseScheduleExpression(scheduleExpr, preFlowNodeResponse);
        } catch (IllegalArgumentException e) {
            log.error("调度表达式[{}]解析异常：", scheduleExpr, e);
            throw WorkflowBusinessException.EXPRESSION_PARSING_ERROR.toException();
        }
    }

    private Map<String, Object> processParallelNode(List<InterfaceWorkflowNode> nodes, Map<String, Object> businessData) {
        // 1. 并行执行任务并收集Future
        List<CompletableFuture<Map<String, Object>>> futures = nodes.stream()
                .map(map -> CompletableFuture.supplyAsync(
                        () -> processSingleNode(map, businessData),
                        taskExecutor
                ))
                .collect(Collectors.toList());

        // 2. 等待所有任务完成，并收集结果
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        List<Map<String, Object>> nodeResultList = allFutures.thenApply(v ->
                futures.stream()
                        .map(CompletableFuture::join)
                        .collect(Collectors.toList())
        ).join();

        // 3. 合并所有结果：相同key的值合并（支持List合并，非List类型转为List）
        return mergeResults(nodeResultList);
    }

    /**
     * 核心合并逻辑：递归处理嵌套结构，只有当key对应的值是List类型时才合并为List，否则只保留第一个节点的值
     * @param resultList 所有节点的结果列表
     * @return 合并后的Map
     */
    private Map<String, Object> mergeResults(List<Map<String, Object>> resultList) {
        Map<String, Object> merged = new HashMap<>();

        for (Map<String, Object> nodeResult : resultList) {
            mergeMap(nodeResult, merged);
        }

        return merged;
    }

    /**
     * 递归合并两个Map，处理嵌套结构
     * @param source 源Map
     * @param target 目标Map
     */
    private void mergeMap(Map<String, Object> source, Map<String, Object> target) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (target.containsKey(key)) {
                Object existingValue = target.get(key);
                // 如果现有值是List，则合并
                if (existingValue instanceof List) {
                    List<Object> mergedValue = mergeValues(existingValue, value);
                    target.put(key, mergedValue);
                } else if (existingValue instanceof Map && value instanceof Map) {
                    // 如果都是Map，则递归合并
                    mergeMap((Map<String, Object>) value, (Map<String, Object>) existingValue);
                }
                // 如果现有值不是List也不是Map，则保持不变（即不覆盖）
            } else {
                // 新key：直接放入，不需要特殊处理
                target.put(key, value);
            }
        }
    }

    /**
     * 合并两个值为List（处理原值和新值是否为List的情况）
     */
    private List<Object> mergeValues(Object existingValue, Object newValue) {
        List<Object> merged = new ArrayList<>();

        // 添加已存在的值
        if (existingValue instanceof List) {
            merged.addAll((List<?>) existingValue);
        } else {
            merged.add(existingValue);
        }

        // 添加新值
        if (newValue instanceof List) {
            merged.addAll((List<?>) newValue);
        } else {
            merged.add(newValue);
        }

        return merged;
    }

}
