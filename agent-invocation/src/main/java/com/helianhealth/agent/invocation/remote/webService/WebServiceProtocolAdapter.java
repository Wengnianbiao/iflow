package com.helianhealth.agent.invocation.remote.webService;

import com.helianhealth.agent.invocation.remote.model.InterfaceWorkflowNode;
import com.helianhealth.agent.invocation.remote.model.ParamTreeNode;
import com.helianhealth.agent.invocation.remote.InterfaceClientProxy;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

@Slf4j
public class WebServiceProtocolAdapter implements InterfaceClientProxy {

    @Setter
    private SoapRequestHandler soapRequestHandler;

    @Override
    public Map<String, Object> doInvoke(InterfaceWorkflowNode flowNode, List<ParamTreeNode> params) {
        try {
            String soapMessage = soapRequestHandler.resolveParamNodes(flowNode, params);
            String soapResponse = soapRequestHandler.sendSoapRequest(flowNode, soapMessage);
            return soapRequestHandler.parseSoapResponse(flowNode, soapResponse);
        } catch (Exception e) {
            log.error("WebService调用失败", e);
            throw new RuntimeException("WebService调用失败: " + e.getMessage(), e);
        }
    }
}
