package com.helianhealth.agent.invocation.remote;

import com.helianhealth.agent.common.utils.JsonUtils;
import com.helianhealth.agent.invocation.context.LogCollector;
import com.helianhealth.agent.invocation.remote.model.ParamTreeNode;
import com.helianhealth.agent.repository.model.domain.WorkflowNode;

import java.util.List;
import java.util.Map;

/**
 * 协议适配器抽象基类
 * 使用模板方法模式统一调用流程，日志收集在抽象类中完成
 * 子类只需实现具体的参数解析、调用执行、响应解析逻辑
 */
public abstract class AbstractProtocolAdapter implements InterfaceClientProxy {

    /**
     * 模板方法：定义统一的调用流程
     * 1. 参数解析 -> 2. 执行调用 -> 3. 响应解析
     * 日志收集穿插在各步骤之间
     */
    @Override
    public final Map<String, Object> doInvoke(WorkflowNode flowNode, List<ParamTreeNode> params) {
        long startTime = System.currentTimeMillis();
        try {
            // 1. 调用前参数解析
            String resolvedParam = resolveParam(flowNode, params);
            LogCollector.collectInvokeRequest(resolvedParam);

            // 2. 执行远程调用
            String rawResponse = executeInvoke(flowNode, resolvedParam);
            LogCollector.collectInvokeResponse(rawResponse);

            // 3. 调用后响应解析
            Map<String, Object> parseResponse = parseResponse(flowNode, rawResponse);
            LogCollector.collectInvokeParsedResponse(JsonUtils.toJsonString(parseResponse));
            return parseResponse;
        } finally {
            LogCollector.collectInvokeTime(System.currentTimeMillis() - startTime);
        }
    }

    /**
     * 参数解析：将参数树转换为协议所需的格式
     * @param flowNode 工作流节点
     * @param params 参数树
     * @return 解析后的参数字符串（JSON/SQL/XML等）
     */
    protected abstract String resolveParam(WorkflowNode flowNode, List<ParamTreeNode> params);

    /**
     * 执行调用：发起实际的远程调用
     * @param flowNode 工作流节点
     * @param resolvedParam 解析后的参数
     * @return 原始响应字符串
     */
    protected abstract String executeInvoke(WorkflowNode flowNode, String resolvedParam);

    /**
     * 响应解析：将原始响应转换为标准Map格式
     * @param flowNode 工作流节点
     * @param rawResponse 原始响应字符串
     * @return 解析后的响应Map
     */
    protected abstract Map<String, Object> parseResponse(WorkflowNode flowNode, String rawResponse);
}
