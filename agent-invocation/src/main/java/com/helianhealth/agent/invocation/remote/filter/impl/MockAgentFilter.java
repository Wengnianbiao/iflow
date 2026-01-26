package com.helianhealth.agent.invocation.remote.filter.impl;

import com.helianhealth.agent.common.extension.AgentActivate;
import com.helianhealth.agent.invocation.remote.InterfaceClientProxy;
import com.helianhealth.agent.invocation.remote.database.DatabaseSqlHandler;
import com.helianhealth.agent.invocation.remote.filter.AgentFilter;
import com.helianhealth.agent.invocation.remote.http.HttpRequestHandler;
import com.helianhealth.agent.invocation.remote.model.InterfaceWorkflowNode;
import com.helianhealth.agent.invocation.remote.model.ParamTreeNode;
import com.helianhealth.agent.invocation.remote.webService.SoapRequestHandler;
import com.helianhealth.agent.mock.service.MockConfigService;
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
    public Map<String, Object> doInvoke(InterfaceClientProxy next, InterfaceWorkflowNode flowNode, List<ParamTreeNode> params) {

        // 检查节点配置中是否包含mockId，如果配置了mockId，则直接获取对应的Mock响应数据
        Map<String, String> filterParams = flowNode.getFilterParams();
        if (filterParams == null || !filterParams.containsKey("mock")) {
            // 无Mock标记，继续调用链
            return next.doInvoke(flowNode, params);
        }

        String mock = filterParams.get("mock");
        if (mock != null) {
            try {
                Integer mockId = Integer.parseInt(mock);
                // todo 耦合，待优化重构
                // 根据 NodeType 选择对应的 handler 来处理参数节点
                resolveParamNodesByType(flowNode, params);
                Map<String, Object> mockResponse = mockConfigService.getMockResponseByMockId(mockId);

                if (mockResponse != null) {
                    return mockResponse;
                }

            } catch (NumberFormatException e) {
                log.error("解析Mock标记失败，mock={}", mock, e);
            }
        }
        // 无Mock标记或解析失败
        return next.doInvoke(flowNode, params);
    }


    private void resolveParamNodesByType(InterfaceWorkflowNode flowNode, List<ParamTreeNode> params) {
        switch (flowNode.getNodeType()) {
            case HTTP:
                httpRequestHandler.resolveParamNodes(flowNode, params);
                break;
            case DATABASE:
                databaseSqlHandler.resolveParamNodes(flowNode, params);
                break;
            case WEBSERVICE:
                soapRequestHandler.resolveParamNodes(flowNode, params);
                break;
            default:
                throw new UnsupportedOperationException("不支持的节点类型: " + flowNode.getNodeType());
        }
    }
}
