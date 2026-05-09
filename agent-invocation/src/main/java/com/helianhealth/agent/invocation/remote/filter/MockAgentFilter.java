package com.helianhealth.agent.invocation.remote.filter;

import com.helianhealth.agent.common.base.ExtensionFilterConstants;
import com.helianhealth.agent.common.exception.AgentConfigException;
import com.helianhealth.agent.common.exception.AgentErrorCode;
import com.helianhealth.agent.common.extension.AgentActivate;
import com.helianhealth.agent.common.utils.JsonUtils;
import com.helianhealth.agent.invocation.support.LogCollector;
import com.helianhealth.agent.invocation.remote.InterfaceClientProxy;
import com.helianhealth.agent.invocation.remote.database.DatabaseSqlHandler;
import com.helianhealth.agent.invocation.remote.http.HttpRequestHandler;
import com.helianhealth.agent.invocation.remote.model.ParamTreeNode;
import com.helianhealth.agent.invocation.remote.webService.SoapRequestHandler;
import com.helianhealth.agent.repository.model.domain.WorkflowNode;
import com.helianhealth.agent.repository.service.MockConfigQueryService;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

@AgentActivate(order = 100)
@Slf4j
public class MockAgentFilter implements AgentFilter {

    private final MockConfigQueryService mockConfigQueryService;

    private final HttpRequestHandler httpRequestHandler;

    private final DatabaseSqlHandler databaseSqlHandler;

    private final SoapRequestHandler soapRequestHandler;

    public MockAgentFilter(MockConfigQueryService mockConfigQueryService,
                          HttpRequestHandler httpRequestHandler,
                          DatabaseSqlHandler databaseSqlHandler,
                          SoapRequestHandler soapRequestHandler) {
        this.mockConfigQueryService = mockConfigQueryService;
        this.httpRequestHandler = httpRequestHandler;
        this.databaseSqlHandler = databaseSqlHandler;
        this.soapRequestHandler = soapRequestHandler;
    }

    @Override
    public Map<String, Object> doInvoke(InterfaceClientProxy next, WorkflowNode flowNode, List<ParamTreeNode> params) {

        // 检查节点配置中是否包含mockId，如果配置了mockId，则直接获取对应的Mock响应数据
        Map<String, String> extensionParam = flowNode.getExtensionParam();
        if (extensionParam == null || !extensionParam.containsKey(ExtensionFilterConstants.EXTENSION_PARAM_KEY_MOCK)) {
            // 无Mock标记，继续调用链
            return next.doInvoke(flowNode, params);
        }

        String mock = extensionParam.get(ExtensionFilterConstants.EXTENSION_PARAM_KEY_MOCK);
        if (mock != null) {
            try {
                Integer mockId = Integer.parseInt(mock);
                // 根据 NodeType 选择对应的 handler 来处理参数节点
                String resolvedParam = resolveParamNodesByType(flowNode, params);
                // 通过门面收集调用前参数
                LogCollector.collectInvokeRequest(resolvedParam);

                // 获取原始响应字符串用于日志收集
                String rawResponse = mockConfigQueryService.getRawMockResponseByMockId(mockId);
                if (rawResponse != null) {
                    // 通过门面收集Mock原始响应
                    LogCollector.collectInvokeResponse(rawResponse);
                    // 根据 NodeType 选择对应的解析方式
                    Map<String, Object> mockResponse = parseResponseByType(flowNode, rawResponse);
                    LogCollector.collectInvokeParsedResponse(JsonUtils.toJsonString(mockResponse));
                    if (mockResponse != null) {
                        return mockResponse;
                    }
                }

            } catch (NumberFormatException e) {
                log.error("解析Mock标记失败，mock={}", mock, e);
            }
        }
        // 无Mock标记或解析失败
        return next.doInvoke(flowNode, params);
    }


    private String resolveParamNodesByType(WorkflowNode flowNode, List<ParamTreeNode> params) {
        switch (flowNode.getNodeType()) {
            case HTTP:
                return httpRequestHandler.resolveParamNodes(flowNode, params);
            case DATABASE:
                return databaseSqlHandler.resolveParamNodes(flowNode, params);
            case WEBSERVICE:
                return soapRequestHandler.resolveParamNodes(flowNode, params);
            default:
                throw new AgentConfigException(AgentErrorCode.NODE_TYPE_UNSUPPORTED,
                        String.valueOf(flowNode.getNodeType()));
        }
    }

    /**
     * 根据 NodeType 选择对应的响应解析方式
     */
    private Map<String, Object> parseResponseByType(WorkflowNode flowNode, String rawResponse) {
        switch (flowNode.getNodeType()) {
            case HTTP:
            case DATABASE:
                return JsonUtils.parseObjectMap(rawResponse);
            case WEBSERVICE:
                return soapRequestHandler.parseSoapResponse(flowNode, rawResponse);
            default:
                throw new AgentConfigException(AgentErrorCode.NODE_TYPE_UNSUPPORTED,
                        String.valueOf(flowNode.getNodeType()));
        }
    }
}
