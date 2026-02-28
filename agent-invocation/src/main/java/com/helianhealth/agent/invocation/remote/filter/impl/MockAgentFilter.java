package com.helianhealth.agent.invocation.remote.filter.impl;

import com.helianhealth.agent.common.base.ExtensionConstants;
import com.helianhealth.agent.common.extension.AgentActivate;
import com.helianhealth.agent.common.utils.JsonUtils;
import com.helianhealth.agent.invocation.context.LogCollector;
import com.helianhealth.agent.invocation.remote.InterfaceClientProxy;
import com.helianhealth.agent.invocation.remote.database.DatabaseSqlHandler;
import com.helianhealth.agent.invocation.remote.filter.AgentFilter;
import com.helianhealth.agent.invocation.remote.http.HttpRequestHandler;
import com.helianhealth.agent.repository.model.domain.WorkflowNode;
import com.helianhealth.agent.invocation.remote.model.ParamTreeNode;
import com.helianhealth.agent.invocation.remote.webService.SoapRequestHandler;
import com.helianhealth.agent.repository.service.MockConfigService;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

@AgentActivate(order = 100)
@Slf4j
public class MockAgentFilter implements AgentFilter {

    private MockConfigService mockConfigService;

    private HttpRequestHandler httpRequestHandler;

    private DatabaseSqlHandler databaseSqlHandler;

    private SoapRequestHandler soapRequestHandler;

    /**
     * 无参构造函数，用于SPI机制实例化
     * 依赖将通过ExtensionFactory自动注入（Setter注入）
     */
    public MockAgentFilter() {
    }

    /**
     * 带参构造函数，支持手动注入依赖
     */
    public MockAgentFilter(MockConfigService mockConfigService,
                          HttpRequestHandler httpRequestHandler,
                          DatabaseSqlHandler databaseSqlHandler,
                          SoapRequestHandler soapRequestHandler) {
        this.mockConfigService = mockConfigService;
        this.httpRequestHandler = httpRequestHandler;
        this.databaseSqlHandler = databaseSqlHandler;
        this.soapRequestHandler = soapRequestHandler;
    }

    /**
     * Setter注入方法，用于ExtensionFactory自动注入
     */
    public void setMockConfigService(MockConfigService mockConfigService) {
        this.mockConfigService = mockConfigService;
    }

    public void setHttpRequestHandler(HttpRequestHandler httpRequestHandler) {
        this.httpRequestHandler = httpRequestHandler;
    }

    public void setDatabaseSqlHandler(DatabaseSqlHandler databaseSqlHandler) {
        this.databaseSqlHandler = databaseSqlHandler;
    }

    public void setSoapRequestHandler(SoapRequestHandler soapRequestHandler) {
        this.soapRequestHandler = soapRequestHandler;
    }

    @Override
    public Map<String, Object> doInvoke(InterfaceClientProxy next, WorkflowNode flowNode, List<ParamTreeNode> params) {

        // 检查节点配置中是否包含mockId，如果配置了mockId，则直接获取对应的Mock响应数据
        Map<String, String> extensionParam = flowNode.getExtensionParam();
        if (extensionParam == null || !extensionParam.containsKey(ExtensionConstants.EXTENSION_PARAM_KEY_MOCK)) {
            // 无Mock标记，继续调用链
            return next.doInvoke(flowNode, params);
        }

        String mock = extensionParam.get(ExtensionConstants.EXTENSION_PARAM_KEY_MOCK);
        if (mock != null) {
            try {
                Integer mockId = Integer.parseInt(mock);
                // 根据 NodeType 选择对应的 handler 来处理参数节点
                String resolvedParam = resolveParamNodesByType(flowNode, params);
                // 通过门面收集调用前参数
                LogCollector.collectParamBeforeInvoke(resolvedParam);
                
                Map<String, Object> mockResponse = mockConfigService.getMockResponseByMockId(mockId);

                if (mockResponse != null) {
                    // 通过门面收集Mock响应
                    LogCollector.collectRemoteInvokeResponse(JsonUtils.toJsonString(mockResponse));
                    return mockResponse;
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
                throw new UnsupportedOperationException("不支持的节点类型: " + flowNode.getNodeType());
        }
    }
}
