package com.helianhealth.agent.invocation.remote;

import com.helianhealth.agent.workflow.model.domain.InterfaceWorkflowNode;
import org.apache.dubbo.common.extension.Adaptive;
import org.apache.dubbo.common.extension.SPI;

import java.util.Map;

@SPI("default")
public interface InterfaceRemoteInvoker {

    @Adaptive("remote")
    Map<String, Object> remoteInvoke(InterfaceWorkflowNode flowNode, Map<String, Object> businessData);
}
