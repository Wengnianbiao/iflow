package com.helianhealth.agent.tasks.spi;

import com.helianhealth.agent.common.extension.AgentSPI;

import java.util.Map;

/**
 * SPI 任务远程调用扩展点。
 *
 * <p>{@code inputParameters.remoteExtension} 为空时路由到 {@code default} 实现（返回空 Map）；
 * 非空时路由到对应扩展类。扩展只接收 {@code inputParameters.input} 解析后的
 * {@code Map<String, Object>}，返回 {@code Map<String, Object>}。
 */
@AgentSPI("default")
public interface InterfaceRemoteInvoker {

    Map<String, Object> remoteInvoke(Map<String, Object> input);
}
