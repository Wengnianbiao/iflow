package com.helianhealth.agent.ai.mcp.dispatcher;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.agent.ai.mcp.McpServerProperties;
import com.helianhealth.agent.ai.model.HealthCheckMcpView;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * Tool: {@code agent_health} —— MCP 服务健康检查。
 */
@Slf4j
@Component
public class HealthCheckToolDispatcher extends McpDispatcherSupport implements McpToolDispatcher {

    public static final String TOOL_NAME = "agent_health";

    private final McpServerProperties mcpServerProperties;
    private final ObjectProvider<DataSource> dataSourceProvider;

    public HealthCheckToolDispatcher(ObjectMapper objectMapper,
                                     McpServerProperties mcpServerProperties,
                                     ObjectProvider<DataSource> dataSourceProvider) {
        super(objectMapper);
        this.mcpServerProperties = mcpServerProperties;
        this.dataSourceProvider = dataSourceProvider;
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

    private HealthCheckMcpView ping() {
        return HealthCheckMcpView.builder()
                .status("UP")
                .serverName(mcpServerProperties.getServerName())
                .serverVersion(mcpServerProperties.getServerVersion())
                .database("SKIPPED")
                .message("iFlow MCP 服务正常")
                .build();
    }

    private HealthCheckMcpView check() {
        HealthCheckMcpView.HealthCheckMcpViewBuilder builder = HealthCheckMcpView.builder()
                .serverName(mcpServerProperties.getServerName())
                .serverVersion(mcpServerProperties.getServerVersion());

        DataSource dataSource = dataSourceProvider.getIfAvailable();
        if (dataSource == null) {
            return builder
                    .status("UP")
                    .database("SKIPPED")
                    .message("MCP 正常；未配置 DataSource，业务 Tool 待新范式接入后再探测")
                    .build();
        }

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("SELECT 1");
            return builder
                    .status("UP")
                    .database("UP")
                    .message("iFlow MCP 与数据源均正常")
                    .build();
        } catch (Exception e) {
            log.warn("Health check database probe failed: {}", e.getMessage());
            return builder
                    .status("DOWN")
                    .database("DOWN")
                    .message("数据源不可用: " + e.getMessage())
                    .build();
        }
    }
}
