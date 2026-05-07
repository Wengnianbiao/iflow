package com.helianhealth.agent.api;

import com.helianhealth.agent.common.extension.AgentAdaptive;
import com.helianhealth.agent.common.extension.AgentSPI;
import com.helianhealth.agent.repository.model.domain.Workflow;

/**
 * Open API 工作流接入扩展点。
 * 默认走 default 编排实现；当 workflow 绑定 routeExtension 时，
 * 再由 SPI 自适应扩展接管。
 */
@AgentSPI("default")
public interface OpenApiRouteHandler {

    /**
     * 处理 Open API 请求。
     *
     * @param workflow 当前命中的工作流，用于 SPI 字段路由
     * @param requestBody 原始请求体；GET 场景下可为框架封装后的 JSON 字符串
     * @return 原始响应字符串
     */
    @AgentAdaptive("routeExtension")
    String handle(Workflow workflow, String requestBody);
}
