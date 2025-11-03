package com.helianhealth.agent.remote;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
public class InterfaceClientProxyDelegate {

//    private final HttpClientProxy httpClientProxy;
//
//    private final DatabaseClientProxy databaseApiClientProxy;
//
//    private final WebServiceClientProxy webServiceClientProxy;
//
//    private final MockApiClientProxy mockApiClientProxy;
//
//    private final DefaultApiClientProxy defaultApiClientProxy;
//
//    private InterfaceClientProxy getExecuteClientProxy(NodeType type) {
//        switch ( type) {
//            case DATABASE:
//                return databaseApiClientProxy;
//            case HTTP:
//                return httpClientProxy;
//            case WEBSERVICE:
//                return webServiceClientProxy;
//            case MOCK:
//                return mockApiClientProxy;
//            default:
//                return defaultApiClientProxy;
//        }
//    }
//
//
//    @Override
//    public Map<String, Object> remoteInvoke(InterfaceWorkflowNodeDO flowNode, Map<String, Object> businessData) {
//        // 直接委托给具体的客户端代理，参数处理已经在抽象类中完成
//        return getExecuteClientProxy(flowNode.getNodeType())
//                .remoteInvoke(flowNode, businessData);
//    }
}
