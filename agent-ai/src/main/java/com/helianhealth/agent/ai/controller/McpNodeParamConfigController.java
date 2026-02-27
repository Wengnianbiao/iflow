package com.helianhealth.agent.ai.controller;

import com.helianhealth.agent.ai.convert.NodeParamConfigMcpConverter;
import com.helianhealth.agent.ai.model.NodeParamConfigImportMcpRequest;
import com.helianhealth.agent.ai.model.NodeParamConfigMcpResponse;
import com.helianhealth.agent.ai.model.NodeParamConfigMcpView;
import com.helianhealth.agent.common.base.ResultData;
import com.helianhealth.agent.common.enums.ProcessType;
import com.helianhealth.agent.common.utils.ResponseRenderUtils;
import com.helianhealth.agent.console.controller.reponse.NodeParamConfigExportRsp;
import com.helianhealth.agent.repository.model.po.NodeParamConfigDO;
import com.helianhealth.agent.console.model.dto.NodeParamConfigImportDTO;
import com.helianhealth.agent.console.service.InterfaceWorkflowNodeService;
import com.helianhealth.agent.console.service.NodeParamConfigService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * MCP接口 - 节点参数配置管理
 * 提供给AI使用的节点参数配置操作接口
 * 
 * 设计理念：
 * - 使用MCP专用视图对象，屏蔽数据库细节
 * - 导出：按nodeId导出树形结构JSON，按前后置分组
 * - 导入：JSON入参直接导入，支持清空旧配置选项
 * - 支持单个配置项的更新和删除
 */
@RestController
@RequestMapping("/v1/ai/mcp/node-param-config")
@Slf4j
@AllArgsConstructor
public class McpNodeParamConfigController {

    private final NodeParamConfigService nodeParamConfigService;
    private final InterfaceWorkflowNodeService interfaceWorkflowNodeService;

    /**
     * 查询节点参数配置列表（扁平化）
     * 返回指定节点的所有参数配置（不分页）
     */
    @GetMapping("/list")
    public ResultData<List<NodeParamConfigMcpView>> listNodeParamConfigs(
            @RequestParam Integer nodeId,
            @RequestParam(required = false) ProcessType processType) {
        try {
            log.info("MCP查询节点参数配置: nodeId={}, processType={}", nodeId, processType);
            if (nodeId == null) {
                return ResponseRenderUtils.error("nodeId不能为空");
            }
            List<NodeParamConfigDO> configs = nodeParamConfigService.findAllNodeParamConfigsUnpaged(nodeId, processType);
            List<NodeParamConfigMcpView> mcpViews = NodeParamConfigMcpConverter.toMcpViewList(configs);
            return ResponseRenderUtils.render(mcpViews);
        } catch (Exception e) {
            log.error("MCP查询节点参数配置失败", e);
            return ResponseRenderUtils.error("查询节点参数配置失败: " + e.getMessage());
        }
    }

    /**
     * 导出节点参数配置（树形结构，按前后置分组）
     * 返回指定节点的所有参数配置，带有childParams嵌套关系
     */
    @GetMapping("/export/{nodeId}")
    public ResultData<NodeParamConfigMcpResponse> exportNodeParamConfig(@PathVariable Integer nodeId) {
        try {
            log.info("MCP导出节点参数配置: nodeId={}", nodeId);
            if (nodeId == null) {
                return ResponseRenderUtils.error("nodeId不能为空");
            }
            NodeParamConfigExportRsp exportRsp = interfaceWorkflowNodeService.exportNodeParamConfig(nodeId);
            NodeParamConfigMcpResponse mcpResponse = NodeParamConfigMcpConverter.toMcpResponse(nodeId, exportRsp);
            return ResponseRenderUtils.render(mcpResponse);
        } catch (Exception e) {
            log.error("MCP导出节点参数配置失败", e);
            return ResponseRenderUtils.error("导出节点参数配置失败: " + e.getMessage());
        }
    }

    /**
     * 导入节点参数配置（JSON入参）
     * 支持递归处理嵌套结构（通过childParams字段）
     */
    @PostMapping("/import")
    public ResultData<String> importNodeParamConfigs(@RequestBody NodeParamConfigImportMcpRequest request) {
        try {
            log.info("MCP导入节点参数配置: nodeId={}, clearBeforeImport={}, configCount={}", 
                    request.getNodeId(), 
                    request.getClearBeforeImport(),
                    request.getParams() != null ? request.getParams().size() : 0);
            
            if (request.getNodeId() == null) {
                return ResponseRenderUtils.error("nodeId不能为空");
            }
            if (request.getParams() == null || request.getParams().isEmpty()) {
                return ResponseRenderUtils.error("参数配置列表不能为空");
            }
            
            // 校验节点是否存在
            if (interfaceWorkflowNodeService.selectByNodeId(request.getNodeId()) == null) {
                return ResponseRenderUtils.error("节点不存在，nodeId=" + request.getNodeId());
            }
            
            // 必填项 + 枚举校验
            NodeParamConfigMcpConverter.validateViews(request.getParams());
            
            // 默认清空旧配置
            if (request.getClearBeforeImport() == null || request.getClearBeforeImport()) {
                interfaceWorkflowNodeService.deleteAllConfigByNodeId(request.getNodeId());
            }
            
            // 转换并导入
            List<NodeParamConfigImportDTO.NodeParamConfigDTO> importDTOs = 
                    NodeParamConfigMcpConverter.toImportDTOList(request.getParams());
            nodeParamConfigService.importNodeParamConfig(request.getNodeId(), importDTOs);
            
            return ResponseRenderUtils.render("导入节点参数配置成功，共" + request.getParams().size() + "条");
        } catch (IllegalArgumentException e) {
            // 枚举校验失败，返回清晰的错误提示
            log.warn("MCP导入节点参数配置参数校验失败: {}", e.getMessage());
            return ResponseRenderUtils.error("参数校验失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("MCP导入节点参数配置失败", e);
            return ResponseRenderUtils.error("导入节点参数配置失败: " + e.getMessage());
        }
    }

    /**
     * 全量删除节点参数配置
     * 删除指定节点下的所有参数配置
     */
    @DeleteMapping("/delete-all/{nodeId}")
    public ResultData<String> deleteAllNodeParamConfigs(@PathVariable Integer nodeId) {
        try {
            log.info("MCP全量删除节点参数配置: nodeId={}", nodeId);
            if (nodeId == null) {
                return ResponseRenderUtils.error("nodeId不能为空");
            }
            interfaceWorkflowNodeService.deleteAllConfigByNodeId(nodeId);
            return ResponseRenderUtils.render("全量删除节点参数配置成功");
        } catch (Exception e) {
            log.error("MCP全量删除节点参数配置失败", e);
            return ResponseRenderUtils.error("全量删除节点参数配置失败: " + e.getMessage());
        }
    }

    /**
     * 更新单个参数配置
     */
    @PostMapping("/update")
    public ResultData<NodeParamConfigMcpView> updateNodeParamConfig(@RequestBody NodeParamConfigMcpView config) {
        try {
            log.info("MCP更新节点参数配置: configId={}", config != null ? config.getConfigId() : null);
            if (config == null || config.getConfigId() == null) {
                return ResponseRenderUtils.error("参数不能为空，configId必须存在");
            }
            NodeParamConfigDO configDO = NodeParamConfigMcpConverter.toDO(config);
            NodeParamConfigDO updatedConfig = nodeParamConfigService.update(configDO);
            if (updatedConfig != null) {
                return ResponseRenderUtils.render(NodeParamConfigMcpConverter.toMcpView(updatedConfig));
            } else {
                return ResponseRenderUtils.error("更新节点参数配置失败");
            }
        } catch (IllegalArgumentException e) {
            log.warn("MCP更新节点参数配置参数校验失败: {}", e.getMessage());
            return ResponseRenderUtils.error("参数校验失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("MCP更新节点参数配置失败", e);
            return ResponseRenderUtils.error("更新节点参数配置失败: " + e.getMessage());
        }
    }

    /**
     * 删除单个参数配置
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

