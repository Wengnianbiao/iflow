package com.helianhealth.agent.invocation.remote;

import com.helianhealth.agent.common.extension.AgentAdaptive;
import com.helianhealth.agent.common.extension.AgentSPI;
import com.helianhealth.agent.invocation.remote.model.InterfaceWorkflowNode;
import com.helianhealth.agent.invocation.remote.model.Invocation;

import java.util.Map;

/**
 * 远程调用器接口
 * 负责执行远程调用（HTTP、SOAP、Database 等）
 */
@AgentSPI("default")
public interface InterfaceRemoteInvoker {

    /**
     * 执行远程调用
     * @param flowNode 工作流节点（用于 SPI 适配和节点元数据）
     * @param invocation 调用上下文（包含参数配置和业务数据）
     * @return 调用结果
     */
    @AgentAdaptive("remoteExtension")
    Map<String, Object> remoteInvoke(InterfaceWorkflowNode flowNode, Invocation invocation);
}
