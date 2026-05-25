package com.helianhealth.agent.ai.mcp.dispatcher;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.agent.ai.mcp.McpServerProperties;
import com.helianhealth.agent.ai.model.HealthCheckMcpView;
import com.helianhealth.agent.repository.service.WorkflowQueryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Tool: {@code agent_health} —— 服务健康检查。
 * <p>AI 自动化配置前应先调用，确认 Interface Agent 进程与数据库可用。</p>
 */
@Slf4j
@Component
public class HealthCheckToolDispatcher extends McpDispatcherSupport implements McpToolDispatcher {

    public static final String TOOL_NAME = "agent_health";

    private final McpServerProperties mcpServerProperties;
    private final WorkflowQueryService workflowQueryService;

    public HealthCheckToolDispatcher(ObjectMapper objectMapper,
                                     McpServerProperties mcpServerProperties,
                                     WorkflowQueryService workflowQueryService) {
        super(objectMapper);
        this.mcpServerProperties = mcpServerProperties;
        this.workflowQueryService = workflowQueryService;
    }

    @Override
    public String toolName() {
        return TOOL_NAME;
    }

    @Override
    public Object call(JsonNode arguments) {
        String action = requireAction(arguments);
        switch (action) {
            case "ping":
                return ping();
            case "check":
                return check();
            default:
                throw new IllegalArgumentException("不支持的 action: " + action);
        }
    }

    /**
     * 存活探测：仅确认 MCP 进程可响应，不访问数据库。
     */
    private HealthCheckMcpView ping() {
        return HealthCheckMcpView.builder()
                .status("UP")
                .serverName(mcpServerProperties.getServerName())
                .serverVersion(mcpServerProperties.getServerVersion())
                .database("SKIPPED")
                .message("Interface Agent MCP 服务正常")
                .build();
    }

    /**
     * 就绪探测：确认 MCP 进程与 agent 数据源可用。
     */
    private HealthCheckMcpView check() {
        HealthCheckMcpView.HealthCheckMcpViewBuilder builder = HealthCheckMcpView.builder()
                .serverName(mcpServerProperties.getServerName())
                .serverVersion(mcpServerProperties.getServerVersion());

        try {
            workflowQueryService.selectAllWorkflows();
            return builder
                    .status("UP")
                    .database("UP")
                    .message("Interface Agent 服务与数据库均正常")
                    .build();
        } catch (Exception e) {
            log.warn("健康检查数据库探测失败: {}", e.getMessage());
            return builder
                    .status("DOWN")
                    .database("DOWN")
                    .message("数据库不可用: " + e.getMessage())
                    .build();
        }
    }
}
