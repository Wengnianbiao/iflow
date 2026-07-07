package com.helianhealth.agent.ai.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helianhealth.agent.ai.mcp.dispatcher.McpToolDispatcher;
import com.helianhealth.agent.ai.mcp.protocol.ToolDefinition;
import com.helianhealth.agent.common.exception.WorkFlowErrorCode;
import com.helianhealth.agent.common.exception.WorkFlowException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP Tool 注册中心。
 *
 * <p>启动时载入 {@code classpath:mcp/tool-schemas.json}，与所有
 * {@link McpToolDispatcher} Bean 按 {@code name} 配对，供 {@code tools/list} /
 * {@code tools/call} 使用。</p>
 *
 * <p>若 schema 文件中声明的 tool 在容器里找不到对应 Dispatcher，启动时直接报错，
 * 防止"schema 写了但代码没接"这类静默偏差。</p>
 */
@Slf4j
@Component
public class McpToolRegistry {

    private static final String DEFAULT_SCHEMA_LOCATION = "classpath:mcp/tool-schemas.json";

    private final ObjectMapper objectMapper;
    private final List<McpToolDispatcher> dispatchers;
    private final String schemaLocation;

    private final Map<String, ToolDefinition> tools = new LinkedHashMap<>();
    private final Map<String, McpToolDispatcher> dispatcherByTool = new LinkedHashMap<>();

    public McpToolRegistry(ObjectMapper objectMapper,
                           List<McpToolDispatcher> dispatchers,
                           @Value("${iflow.mcp.schema-location:" + DEFAULT_SCHEMA_LOCATION + "}") String schemaLocation) {
        this.objectMapper = objectMapper;
        this.dispatchers = dispatchers;
        this.schemaLocation = schemaLocation;
    }

    @PostConstruct
    public void init() {
        Map<String, McpToolDispatcher> byName = new LinkedHashMap<>();
        for (McpToolDispatcher dispatcher : dispatchers) {
            byName.put(dispatcher.toolName(), dispatcher);
        }

        try {
            JsonNode root = loadSchemaRoot();
            JsonNode toolsNode = root.get("tools");
            if (toolsNode == null || !toolsNode.isArray()) {
                throw new IllegalStateException("MCP schema 文件缺少 tools 数组: " + schemaLocation);
            }
            for (JsonNode toolNode : toolsNode) {
                ToolDefinition def = objectMapper.convertValue(toolNode, ToolDefinition.class);
                if (def.getName() == null || def.getName().isEmpty()) {
                    throw new IllegalStateException("MCP schema 中存在未命名 tool");
                }
                McpToolDispatcher dispatcher = byName.get(def.getName());
                if (dispatcher == null) {
                    throw new IllegalStateException(
                            "MCP schema 声明了 tool '" + def.getName() + "'，但容器中没有对应 Dispatcher Bean");
                }
                tools.put(def.getName(), def);
                dispatcherByTool.put(def.getName(), dispatcher);
            }
            log.info("MCP tool registry initialized, {} tools registered: {}", tools.size(), tools.keySet());
        } catch (Exception e) {
            throw new WorkFlowException(WorkFlowErrorCode.INTERNAL_ERROR,
                    "MCP Tool Registry 初始化失败: " + e.getMessage(), e);
        }
    }

    /**
     * 全部 tool 定义（按 schema 文件中声明的顺序）
     */
    public List<ToolDefinition> listTools() {
        return Collections.unmodifiableList(new java.util.ArrayList<>(tools.values()));
    }

    /**
     * 按名称获取 Dispatcher，找不到返回 null
     */
    public McpToolDispatcher findDispatcher(String toolName) {
        return dispatcherByTool.get(toolName);
    }

    private JsonNode loadSchemaRoot() throws Exception {
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(getClass().getClassLoader());
        Resource resource = resolver.getResource(schemaLocation);
        if (!resource.exists()) {
            throw new IllegalStateException("找不到 MCP schema 资源: " + schemaLocation);
        }
        try (InputStream in = resource.getInputStream()) {
            return objectMapper.readTree(in);
        }
    }
}
