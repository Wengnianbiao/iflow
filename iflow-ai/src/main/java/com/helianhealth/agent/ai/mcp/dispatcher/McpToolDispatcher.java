package com.helianhealth.agent.ai.mcp.dispatcher;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * MCP Tool 分发器。
 *
 * <p>每个领域（workflow / workflow_node / mock_config 等）实现一个 Dispatcher Bean，
 * 由 {@link com.helianhealth.agent.ai.mcp.McpToolRegistry} 按 {@link #toolName()} 路由。</p>
 *
 * <h3>实现约束</h3>
 * <ul>
 *   <li>Dispatcher 直接调 Service 层，不绕回 HTTP 自调。</li>
 *   <li>正常业务结果：返回业务对象（最终会被序列化到
 *       {@link com.helianhealth.agent.common.base.ResultData#getData()}）。</li>
 *   <li>业务异常：抛出 {@link IllegalArgumentException} / {@code AgentException} 等，由
 *       {@link com.helianhealth.agent.ai.mcp.McpServerEndpoint} 统一包成
 *       {@code ResultData.error(...)} 返回。</li>
 * </ul>
 */
public interface McpToolDispatcher {

    /**
     * 该 Dispatcher 处理的 Tool 名称，与 {@code tool-schemas.json} 中 name 一致。
     */
    String toolName();

    /**
     * 执行 Tool 调用。
     *
     * @param arguments MCP 客户端传入的 {@code arguments} 对象（JSON 节点，包含 action 与对应字段）
     * @return 业务返回值，null 表示无返回
     */
    Object call(JsonNode arguments);
}
