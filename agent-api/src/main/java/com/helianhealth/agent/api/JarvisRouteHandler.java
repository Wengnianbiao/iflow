package com.helianhealth.agent.api;

import com.helianhealth.agent.api.model.request.JarvisRequest;
import com.helianhealth.agent.api.model.response.JarvisResultData;
import com.helianhealth.agent.common.extension.AgentAdaptive;
import com.helianhealth.agent.common.extension.AgentSPI;
import com.helianhealth.agent.repository.model.domain.Workflow;

/**
 * Jarvis 工作流接入扩展点。
 * 默认走 default 编排实现；当 workflow 绑定 routeExtension 时，
 * 再由 SPI 自适应扩展接管。
 */
@AgentSPI("default")
public interface JarvisRouteHandler {

    /**
     * 处理 Jarvis 请求。
     *
     * @param workflow 当前命中的工作流，用于 SPI 字段路由
     * @param request 当前 Jarvis 请求
     * @return Jarvis 固定响应格式
     */
    @AgentAdaptive("routeExtension")
    JarvisResultData<Object> handle(Workflow workflow, JarvisRequest request);
}
