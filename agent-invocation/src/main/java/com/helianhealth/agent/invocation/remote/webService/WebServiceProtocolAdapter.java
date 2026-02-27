package com.helianhealth.agent.invocation.remote.webService;

import com.helianhealth.agent.invocation.remote.InterfaceClientProxy;
import com.helianhealth.agent.repository.model.domain.WorkflowNode;
import com.helianhealth.agent.invocation.remote.model.ParamTreeNode;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

@Slf4j
public class WebServiceProtocolAdapter implements InterfaceClientProxy {

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
    public Map<String, Object> doInvoke(WorkflowNode flowNode, List<ParamTreeNode> params) {
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
