package com.helianhealth.agent.remote;

import com.helianhealth.agent.model.domain.InterfaceWorkflowNode;
import com.helianhealth.agent.model.dto.ParamTreeNode;
import org.apache.dubbo.common.extension.Adaptive;
import org.apache.dubbo.common.extension.SPI;

import java.util.List;
import java.util.Map;

/**
 * 接口调用客户端代理
 */
@SPI("default")
public interface InterfaceClientProxy {

    @Adaptive("proxy")
    Map<String, Object> doInvoke(InterfaceWorkflowNode flowNode,
                                 List<ParamTreeNode> params);
}
