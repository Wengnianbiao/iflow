package com.helianhealth.agent.invocation.remote.filter.impl;

import com.helianhealth.agent.invocation.remote.model.InterfaceWorkflowNode;
import com.helianhealth.agent.invocation.remote.model.ParamTreeNode;
import com.helianhealth.agent.invocation.remote.InterfaceClientProxy;
import com.helianhealth.agent.invocation.remote.database.DatabaseSqlHandler;
import com.helianhealth.agent.invocation.remote.filter.AgentFilter;
import com.helianhealth.agent.invocation.remote.http.HttpRequestHandler;
import com.helianhealth.agent.invocation.remote.webService.SoapRequestHandler;
import com.helianhealth.agent.mock.service.MockConfigService;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.common.extension.Activate;

import java.util.List;
import java.util.Map;

@Activate(order = 100)
@Slf4j
public class MockAgentFilter implements AgentFilter {

    @Setter
    private MockConfigService mockConfigService;

    @Setter
    private HttpRequestHandler httpRequestHandler;

    @Setter
    private DatabaseSqlHandler databaseSqlHandler;

    @Setter
    private SoapRequestHandler soapRequestHandler;

    @Override
    public Map<String, Object> doInvoke(InterfaceClientProxy next, InterfaceWorkflowNode flowNode, List<ParamTreeNode> params) {

        // 检查URL参数中是否包含mockId,如果配置了mockId，则直接获取对应的Mock响应数据
        String mock = flowNode.getUrl().getParameter("mock");

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
        // 无Mock标记
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
