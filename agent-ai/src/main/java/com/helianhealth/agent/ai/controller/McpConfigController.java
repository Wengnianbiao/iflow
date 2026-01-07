package com.helianhealth.agent.ai.controller;

import com.helianhealth.agent.ai.service.McpConfigService;
import com.helianhealth.agent.common.base.ResultData;
import com.helianhealth.agent.common.utils.ResponseRenderUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * MCP配置接口 - 从配置文件创建完整工作流
 */
@RestController
@RequestMapping("/v1/ai/mcp/config")
@Slf4j
@AllArgsConstructor
public class McpConfigController {

    private final McpConfigService mcpConfigService;

    /**
     * 从配置文件创建完整的工作流（工作流+节点+参数配置）
     * 
     * @param configFileName 配置文件名（相对于resources目录）
     * @return 创建结果
     */
    @PostMapping("/create-from-file")
    public ResultData<McpConfigService.WorkflowCreationResult> createFromFile(
            @RequestParam("configFileName") String configFileName) {
        try {
            log.info("MCP从配置文件创建工作流: {}", configFileName);
            McpConfigService.WorkflowCreationResult result = mcpConfigService.createFromConfigFile(configFileName);
            return ResponseRenderUtils.render(result);
        } catch (Exception e) {
            log.error("MCP从配置文件创建工作流失败", e);
            return ResponseRenderUtils.error("创建失败: " + e.getMessage());
        }
    }
}
