package com.helianhealth.agent.remote;

import com.helianhealth.agent.model.domain.InterfaceWorkflowNodeDO;
import org.apache.dubbo.common.extension.Adaptive;
import org.apache.dubbo.common.extension.SPI;

import java.util.Map;

@SPI("default")
public interface InterfaceRemoteInvoker {


    @Adaptive("remote")
    public Map<String, Object> remoteInvoke(InterfaceWorkflowNodeDO flowNode, Map<String, Object> businessData);
}
