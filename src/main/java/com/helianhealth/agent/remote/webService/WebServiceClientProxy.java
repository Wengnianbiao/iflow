package com.helianhealth.agent.remote.webService;

import com.helianhealth.agent.model.domain.InterfaceWorkflowNode;
import com.helianhealth.agent.model.dto.ParamTreeNode;
import com.helianhealth.agent.remote.InterfaceClientProxy;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

@Slf4j
public class WebServiceClientProxy implements InterfaceClientProxy {

    @Setter
    private SoapRequestHandler soapRequestHandler_findBySpring;

    @Override
    public Map<String, Object> doInvoke(InterfaceWorkflowNode flowNode, List<ParamTreeNode> params) {
        try {
            String soapMessage = soapRequestHandler_findBySpring.resolveParamNodes(flowNode, params);
            String soapResponse = soapRequestHandler_findBySpring.sendSoapRequest(flowNode, soapMessage);
            return soapRequestHandler_findBySpring.parseSoapResponse(flowNode, soapResponse);
        } catch (Exception e) {
            log.error("WebService调用失败", e);
            throw new RuntimeException("WebService调用失败: " + e.getMessage(), e);
        }
    }
}
