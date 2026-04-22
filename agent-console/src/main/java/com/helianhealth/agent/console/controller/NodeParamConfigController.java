
package com.helianhealth.agent.console.controller;

import com.helianhealth.agent.common.base.PageList;
import com.helianhealth.agent.common.base.ResultData;
import com.helianhealth.agent.common.enums.ProcessType;
import com.helianhealth.agent.repository.model.domain.NodeParamConfig;
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
 * 节点参数配置控制器
 */
@RestController
@RequestMapping("/v1/console/node-param")
@Slf4j
@AllArgsConstructor
public class NodeParamConfigController {

    private final NodeParamConfigService nodeParamConfigService;

    /**
     * 获取所有节点参数配置
     */
    @GetMapping("/all")
    public ResultData<PageList<NodeParamConfig>> getAllNodeParamConfigs(
            @RequestParam(value = "nodeId", required = false) Integer nodeId,
            @RequestParam(value = "processType", required = false) ProcessType processType,
            @RequestParam(value = "targetParam", required = false) String targetParam,
            @RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        try {
            PageList<NodeParamConfig> pageList = nodeParamConfigService.findAllNodeParamConfigs(nodeId, processType, targetParam, pageNum, pageSize);
            return ResultData.ok(pageList);
        } catch (Exception e) {
            log.error("获取节点参数配置列表失败", e);
            return ResultData.error("获取节点参数配置列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取所有节点参数配置
     */
    @GetMapping("/all-unpaged")
    public ResultData<List<NodeParamConfig>> getAllNodeParamConfigsUnpaged(
            @RequestParam(value = "nodeId", required = false) Integer nodeId,
            @RequestParam(value = "processType", required = false) ProcessType processType) {
        try {
            List<NodeParamConfig> pageList = nodeParamConfigService.findAllNodeParamConfigsUnpaged(nodeId, processType);
            return ResultData.ok(pageList);
        } catch (Exception e) {
            log.error("获取节点参数配置列表失败", e);
            return ResultData.error("获取节点参数配置列表失败: " + e.getMessage());
        }
    }

    /**
     * 创建节点参数配置
     */
    @PostMapping("/create")
    public ResultData<NodeParamConfig> createNodeParamConfig(@RequestBody NodeParamConfig config) {
        try {

            log.info("创建节点参数配置: {}", config);
            // 验证必要参数
            if (config == null) {
                return ResultData.error("参数不能为空");
            }

            if (config.getProcessType() == null) {
                return ResultData.error("processType 不能为空");
            }

            // 调用服务层保存逻辑
            NodeParamConfig savedConfig = nodeParamConfigService.save(config);

            if (savedConfig != null) {
                return ResultData.ok(savedConfig);
            } else {
                return ResultData.error("创建节点参数配置失败");
            }
        } catch (Exception e) {
            log.error("创建节点参数配置失败", e);
            return ResultData.error("创建节点参数配置失败: " + e.getMessage());
        }
    }

    /**
     * 更新节点参数配置
     */
    @PostMapping("/update")
    public ResultData<NodeParamConfig> updateNodeParamConfig(@RequestBody NodeParamConfig config) {
        try {
            // 验证参数是否为空
            if (config == null || config.getConfigId() == null) {
                return ResultData.error("参数不能为空，configId 必须存在");
            }

            // 调用服务层更新逻辑
            NodeParamConfig updatedConfig = nodeParamConfigService.update(config);

            if (updatedConfig != null) {
                return ResultData.ok(updatedConfig);
            } else {
                return ResultData.error("更新节点参数配置失败");
            }
        } catch (Exception e) {
            log.error("更新节点参数配置失败", e);
            return ResultData.error("更新节点参数配置失败: " + e.getMessage());
        }
    }

    /**
     * 删除节点参数配置
     */
    @DeleteMapping("{configId}")
    public ResultData<String> deleteNodeParamConfig(@PathVariable Integer configId) {
        try {
            nodeParamConfigService.deleteById(configId);
            return ResultData.ok("节点参数配置删除成功");
        } catch (Exception e) {
            log.error("删除节点参数配置失败", e);
            return ResultData.error("删除节点参数配置失败: " + e.getMessage());
        }
    }
}
