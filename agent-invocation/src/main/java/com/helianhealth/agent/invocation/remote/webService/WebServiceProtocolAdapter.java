package com.helianhealth.agent.invocation.remote.webService;

import com.helianhealth.agent.invocation.remote.AbstractProtocolAdapter;
import com.helianhealth.agent.invocation.remote.model.ParamTreeNode;
import com.helianhealth.agent.repository.model.domain.WorkflowNode;

import java.util.List;
import java.util.Map;

/**
 * WebService协议适配器
 */
public class WebServiceProtocolAdapter extends AbstractProtocolAdapter {

    private SoapRequestHandler soapRequestHandler;

    /**
     * 无参构造函数，用于SPI机制实例化
     * SoapRequestHandler将通过ExtensionFactory自动注入（Setter注入）
     */
    public WebServiceProtocolAdapter() {
    }

    /**
     * 带参构造函数，支持手动注入依赖
     */
    public WebServiceProtocolAdapter(SoapRequestHandler soapRequestHandler) {
        this.soapRequestHandler = soapRequestHandler;
    }

    /**
     * Setter注入方法，用于ExtensionFactory自动注入
     */
    public void setSoapRequestHandler(SoapRequestHandler soapRequestHandler) {
        this.soapRequestHandler = soapRequestHandler;
    }

    @Override
    protected String resolveParams(WorkflowNode flowNode, List<ParamTreeNode> params) {
        return soapRequestHandler.resolveParamNodes(flowNode, params);
    }

    @Override
    protected String executeInvoke(WorkflowNode flowNode, String resolvedParam) {
        return soapRequestHandler.sendSoapRequest(flowNode, resolvedParam);
    }

    @Override
    protected Map<String, Object> parseResponse(WorkflowNode flowNode, String rawResponse) {
        return soapRequestHandler.parseSoapResponse(flowNode, rawResponse);
    }
}
