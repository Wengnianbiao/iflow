package com.helianhealth.agent.ai.controller;

import com.helianhealth.agent.common.base.ResultData;
import com.helianhealth.agent.common.utils.ResponseRenderUtils;
import com.helianhealth.agent.workflow.model.domain.NodeParamConfigDO;
import com.helianhealth.agent.workflow.service.NodeParamConfigService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * MCP接口 - 节点参数配置管理
 * 提供给AI使用的节点参数配置CRUD接口
 */
@RestController
@RequestMapping("/v1/ai/mcp/node-param-config")
@Slf4j
@AllArgsConstructor
public class McpNodeParamConfigController {

    private final NodeParamConfigService nodeParamConfigService;

    /**
     * 创建节点参数配置
     */
    @PostMapping("/create")
    public ResultData<NodeParamConfigDO> createNodeParamConfig(@RequestBody NodeParamConfigDO config) {
        try {
            log.info("MCP创建节点参数配置: {}", config);
            if (config == null) {
                return ResponseRenderUtils.error("参数不能为空");
            }
            if (config.getProcessType() == null) {
                return ResponseRenderUtils.error("processType不能为空");
            }
            NodeParamConfigDO savedConfig = nodeParamConfigService.save(config);
            if (savedConfig != null) {
                return ResponseRenderUtils.render(savedConfig);
            } else {
                return ResponseRenderUtils.error("创建节点参数配置失败");
            }
        } catch (Exception e) {
            log.error("MCP创建节点参数配置失败", e);
            return ResponseRenderUtils.error("创建节点参数配置失败: " + e.getMessage());
        }
    }

    /**
     * 更新节点参数配置
     */
    @PostMapping("/update")
    public ResultData<NodeParamConfigDO> updateNodeParamConfig(@RequestBody NodeParamConfigDO config) {
        try {
            log.info("MCP更新节点参数配置: {}", config);
            if (config == null || config.getConfigId() == null) {
                return ResponseRenderUtils.error("参数不能为空，configId必须存在");
            }
            NodeParamConfigDO updatedConfig = nodeParamConfigService.update(config);
            if (updatedConfig != null) {
                return ResponseRenderUtils.render(updatedConfig);
            } else {
                return ResponseRenderUtils.error("更新节点参数配置失败");
            }
        } catch (Exception e) {
            log.error("MCP更新节点参数配置失败", e);
            return ResponseRenderUtils.error("更新节点参数配置失败: " + e.getMessage());
        }
    }

    /**
     * 删除节点参数配置
     */
    @DeleteMapping("/delete/{configId}")
    public ResultData<String> deleteNodeParamConfig(@PathVariable Integer configId) {
        try {
            log.info("MCP删除节点参数配置: configId={}", configId);
            nodeParamConfigService.deleteById(configId);
            return ResponseRenderUtils.render("节点参数配置删除成功");
        } catch (Exception e) {
            log.error("MCP删除节点参数配置失败", e);
            return ResponseRenderUtils.error("删除节点参数配置失败: " + e.getMessage());
        }
    }
}

