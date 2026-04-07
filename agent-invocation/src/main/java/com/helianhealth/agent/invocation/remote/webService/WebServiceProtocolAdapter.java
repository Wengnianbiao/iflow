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

    private final SoapRequestHandler soapRequestHandler;

    public WebServiceProtocolAdapter(SoapRequestHandler soapRequestHandler) {
        this.soapRequestHandler = soapRequestHandler;
    }

    @Override
    protected String resolveParam(WorkflowNode flowNode, List<ParamTreeNode> params) {
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
