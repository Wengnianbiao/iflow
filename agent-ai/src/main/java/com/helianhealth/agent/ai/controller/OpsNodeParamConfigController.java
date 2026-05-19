package com.helianhealth.agent.ai.controller;

import com.helianhealth.agent.ai.convert.NodeParamConfigMcpConverter;
import com.helianhealth.agent.ai.model.NodeParamConfigImportMcpRequest;
import com.helianhealth.agent.ai.model.NodeParamConfigMcpResponse;
import com.helianhealth.agent.ai.model.NodeParamConfigMcpView;
import com.helianhealth.agent.ai.service.AiNodeParamConfigService;
import com.helianhealth.agent.common.base.ResultData;
import com.helianhealth.agent.common.enums.ProcessType;
import com.helianhealth.agent.repository.model.domain.NodeParamConfig;
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
 * Ops 接口 - 节点参数配置管理（底层 REST 能力面）。
 */
@RestController
@RequestMapping("/v1/ai/ops/node-param-config")
@Slf4j
@AllArgsConstructor
public class OpsNodeParamConfigController {

    private final AiNodeParamConfigService nodeParamConfigService;

    @GetMapping("/list")
    public ResultData<List<NodeParamConfigMcpView>> listNodeParamConfigs(
            @RequestParam Integer nodeId,
            @RequestParam(required = false) ProcessType processType) {
        try {
            log.info("Ops 查询节点参数配置: nodeId={}, processType={}", nodeId, processType);
            if (nodeId == null) {
                return ResultData.error("nodeId不能为空");
            }
            List<NodeParamConfig> configs = nodeParamConfigService.findAllNodeParamConfigsUnpaged(nodeId, processType);
            List<NodeParamConfigMcpView> mcpViews = NodeParamConfigMcpConverter.toMcpViewList(configs);
            return ResultData.ok(mcpViews);
        } catch (Exception e) {
            log.error("Ops 查询节点参数配置失败", e);
            return ResultData.error("查询节点参数配置失败: " + e.getMessage());
        }
    }

    @GetMapping("/export/{nodeId}")
    public ResultData<NodeParamConfigMcpResponse> exportNodeParamConfig(@PathVariable Integer nodeId) {
        try {
            log.info("Ops 导出节点参数配置: nodeId={}", nodeId);
            if (nodeId == null) {
                return ResultData.error("nodeId不能为空");
            }
            NodeParamConfigMcpResponse mcpResponse = nodeParamConfigService.exportNodeParamConfigs(nodeId);
            return ResultData.ok(mcpResponse);
        } catch (Exception e) {
            log.error("Ops 导出节点参数配置失败", e);
            return ResultData.error("导出节点参数配置失败: " + e.getMessage());
        }
    }

    @PostMapping("/import")
    public ResultData<String> importNodeParamConfigs(@RequestBody NodeParamConfigImportMcpRequest request) {
        try {
            log.info("Ops 导入节点参数配置: nodeId={}, clearBeforeImport={}, configCount={}",
                    request.getNodeId(),
                    request.getClearBeforeImport(),
                    request.getParams() != null ? request.getParams().size() : 0);

            if (request.getNodeId() == null) {
                return ResultData.error("nodeId不能为空");
            }
            if (request.getParams() == null || request.getParams().isEmpty()) {
                return ResultData.error("参数配置列表不能为空");
            }

            if (!nodeParamConfigService.nodeExists(request.getNodeId())) {
                return ResultData.error("节点不存在，nodeId=" + request.getNodeId());
            }

            NodeParamConfigMcpConverter.validateViews(request.getParams());

            if (request.getClearBeforeImport() == null || request.getClearBeforeImport()) {
                nodeParamConfigService.deleteAllNodeParamConfigs(request.getNodeId());
            }

            nodeParamConfigService.importNodeParamConfigs(
                    request.getNodeId(),
                    request.getParams(),
                    false
            );

            return ResultData.ok("导入节点参数配置成功，共" + request.getParams().size() + "条");
        } catch (IllegalArgumentException e) {
            log.warn("Ops 导入节点参数配置参数校验失败: {}", e.getMessage());
            return ResultData.error("参数校验失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("Ops 导入节点参数配置失败", e);
            return ResultData.error("导入节点参数配置失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/delete-all/{nodeId}")
    public ResultData<String> deleteAllNodeParamConfigs(@PathVariable Integer nodeId) {
        try {
            log.info("Ops 全量删除节点参数配置: nodeId={}", nodeId);
            if (nodeId == null) {
                return ResultData.error("nodeId不能为空");
            }
            nodeParamConfigService.deleteAllNodeParamConfigs(nodeId);
            return ResultData.ok("全量删除节点参数配置成功");
        } catch (Exception e) {
            log.error("Ops 全量删除节点参数配置失败", e);
            return ResultData.error("全量删除节点参数配置失败: " + e.getMessage());
        }
    }

    @PostMapping("/update")
    public ResultData<NodeParamConfigMcpView> updateNodeParamConfig(@RequestBody NodeParamConfigMcpView config) {
        try {
            log.info("Ops 更新节点参数配置: configId={}", config != null ? config.getConfigId() : null);
            if (config == null || config.getConfigId() == null) {
                return ResultData.error("参数不能为空，configId必须存在");
            }
            NodeParamConfig updatedConfig = nodeParamConfigService.updateNodeParamConfig(config);
            if (updatedConfig != null) {
                return ResultData.ok(NodeParamConfigMcpConverter.toMcpView(updatedConfig));
            } else {
                return ResultData.error("更新节点参数配置失败");
            }
        } catch (IllegalArgumentException e) {
            log.warn("Ops 更新节点参数配置参数校验失败: {}", e.getMessage());
            return ResultData.error("参数校验失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("Ops 更新节点参数配置失败", e);
            return ResultData.error("更新节点参数配置失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/delete/{configId}")
    public ResultData<String> deleteNodeParamConfig(@PathVariable Integer configId) {
        try {
            log.info("Ops 删除节点参数配置: configId={}", configId);
            nodeParamConfigService.deleteNodeParamConfig(configId);
            return ResultData.ok("节点参数配置删除成功");
        } catch (Exception e) {
            log.error("Ops 删除节点参数配置失败", e);
            return ResultData.error("删除节点参数配置失败: " + e.getMessage());
        }
    }
}
